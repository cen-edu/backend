package com.cenedu.backend.domain.analysis.service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cenedu.backend.domain.analysis.dto.StudentReportSummary;
import com.cenedu.backend.domain.analysis.dto.WorksheetDetail;
import com.cenedu.backend.domain.analysis.entity.AnalysisReport;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.DisplayLabels;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생 한 명의 개인 분석을 PDF 로 만든다.
 *
 * <p>조판은 취약점 분석 화면의 개인 보기({@code StudentAnalysisView})를 따른다. 계산 규칙도
 * 화면과 같은 것을 쓴다 — 교사가 화면에서 보던 숫자와 종이가 어긋나면 어느 쪽을 믿을지 알 수 없다.
 *
 * <p><b>LLM 서술은 넣지 않는다.</b> 지금 필요한 것은 화면에 이미 있는 값을 인쇄물로 옮기는
 * 것이고, 서술 생성은 디스패처와 가드레일이 준비된 뒤에 별도로 붙인다.
 */
@Service
public class StudentReportService {

    private static final int WEAK_RATE = 60;
    private static final String REPORT_TYPE = "STUDENT_DETAIL";

    /**
     * 영역·상태 라벨은 {@link DisplayLabels} 만 쓴다.
     *
     * <p>여기에 따로 맵을 두면 학급 보고서와 개인 보고서가 같은 영역을 다르게 부른다. 실제로
     * 프론트는 {@code concept} 을 "개념"으로 쓰지만 DisplayLabels 는 "이해"로 쓴다 — 개념이
     * 소단원을 가리키는 말로도 읽혀 축이 헷갈리기 때문이다. 그 판단을 보고서마다 다시 하지 않는다.
     */
    private static String areaLabel(String area) {
        return DisplayLabels.area(area);
    }

    private static String statusLabel(String status) {
        return DisplayLabels.status(statusCode(status));
    }

    /** 화면 상태 코드를 DisplayLabels 가 아는 코드로 옮긴다. */
    private static String statusCode(String status) {
        return switch (status == null ? "" : status) {
            case "priority" -> "priority";
            case "review" -> "review";
            case "stable" -> "stable";
            case "insufficient" -> "insufficient";
            default -> "";
        };
    }

    private final WeaknessAnalysisQueryService worksheets;
    private final AnalysisReportRepository reports;
    private final BrowserPdfRenderer renderer;

    public StudentReportService(WeaknessAnalysisQueryService worksheets,
                                AnalysisReportRepository reports,
                                BrowserPdfRenderer renderer) {
        this.worksheets = worksheets;
        this.reports = reports;
        this.renderer = renderer;
    }

    /**
     * 개인 보고서를 만들고 요약을 돌려준다.
     *
     * <p>화면은 이 응답의 {@code pdfUrl} 로 곧바로 이동한다. 그래서 PDF 를 여기서 다 만들어
     * 두고 주소만 넘긴다. 주소를 먼저 주고 뒤에서 만들면 화면이 빈 파일을 받는다.
     */
    @Transactional
    public StudentReportSummary generate(String assessmentId, String studentId) {
        WorksheetDetail worksheet = worksheets.worksheet(assessmentId);
        WorksheetDetail.Student student = worksheet.students().stream()
                .filter(row -> row.id().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_ATTEMPT_NOT_FOUND));

        UUID reportId = UUID.randomUUID();
        String html = render(worksheet, student);
        Path pdf = renderer.render("student-" + reportId, html).pdf();

        AnalysisReport saved = reports.save(AnalysisReport.builder()
                .reportId(reportId)
                .assessmentId(assessmentId)
                .studentId(studentId)
                .reportType(REPORT_TYPE)
                .statusName(statusLabel(student.status()))
                .pdfPath(pdf.toString())
                .build());

        return StudentReportSummary.of(saved.getReportId().toString(), studentId, assessmentId,
                REPORT_TYPE, saved.getStatusName());
    }

    /** 만들어 둔 PDF 파일 경로. */
    @Transactional(readOnly = true)
    public Path pdfPath(String reportId) {
        AnalysisReport report = reports.findByReportId(parse(reportId))
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (report.getPdfPath() == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND, "이 보고서에는 PDF 가 없습니다.");
        }
        return Path.of(report.getPdfPath());
    }

    private static UUID parse(String reportId) {
        try {
            return UUID.fromString(reportId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND,
                    "올바르지 않은 보고서 번호입니다: " + reportId);
        }
    }

    // ---------------------------------------------------------------- 조판

    private String render(WorksheetDetail worksheet, WorksheetDetail.Student student) {
        Metrics me = metrics(student);
        List<WorksheetDetail.Student> reliable = worksheet.students().stream()
                .filter(row -> !"insufficient".equals(row.status()))
                .toList();
        int classRate = reliable.isEmpty() ? 0
                : (int) Math.round(reliable.stream().mapToInt(row -> metrics(row).scoreRate())
                        .average().orElse(0));
        int gap = me.scoreRate() - classRate;
        String weakConcept = weakConcept(worksheet, student);

        StringBuilder pages = new StringBuilder();
        pages.append(ReportStyle.page(
                header(worksheet, student)
                        + metricCards(me, weakConceptCount(worksheet, student))
                        + ReportStyle.notice("이 분석은 현재 학습지 응답만을 기준으로 하며 등수나 "
                                + "전체 학업 능력을 의미하지 않습니다.")
                        + priorityCard(student, weakConcept)
                        + comparisonCard(me.scoreRate(), classRate, gap)));
        pages.append(ReportStyle.page(
                header(worksheet, student)
                        + ReportStyle.card("학생 vs 학급", "영역별 결과", null,
                                breakdownBars(worksheet, student, "area"))
                        + ReportStyle.card("학생 vs 학급", "난이도별 결과", null,
                                breakdownBars(worksheet, student, "difficulty"))));
        pages.append(ReportStyle.page(
                header(worksheet, student)
                        + ReportStyle.card("응답 흐름과 관찰", "풀이 기록",
                                observation(weakConcept),
                                recordTable(worksheet, student))));

        return ReportStyle.document(student.name() + " 개인 분석", pages.toString());
    }

    private static String header(WorksheetDetail worksheet, WorksheetDetail.Student student) {
        return "<header class='page-header'><div><h1>" + ReportStyle.escape(student.name())
                + " <span class='badge " + ReportStyle.escape(student.status()) + "'>"
                + ReportStyle.escape(statusLabel(student.status()))
                + "</span></h1><p>" + ReportStyle.escape(worksheet.title())
                + " 개인 분석</p></div><span>" + worksheet.date() + " 기준<br>"
                + ReportStyle.escape(worksheet.className()) + "</span></header>";
    }

    private static String metricCards(Metrics me, int weakConcepts) {
        return "<section class='summary'>"
                + ReportStyle.statCard("풀이 문항", me.solvedCount() + "문항", "채점 완료 기준")
                + ReportStyle.statCard("정답", me.correctCount() + "문항", "부분 점수 없음")
                + ReportStyle.statCard("정답률", me.scoreRate() + "%", "채점 대기 제외")
                + ReportStyle.statCard("취약 개념", weakConcepts + "개", "달성률 60% 미만")
                + "</section>";
    }

    private static String priorityCard(WorksheetDetail.Student student, String weakConcept) {
        long wrong = student.responses().stream()
                .filter(response -> response.gradedBy() != null)
                .filter(response -> response.score() < response.maxScore())
                .count();
        return ReportStyle.card("가장 먼저 지도할 부분", weakConcept, null,
                "<dl class='facts'>"
                        + fact("상태", statusLabel(student.status()))
                        + fact("누적 오답", wrong + "회")
                        + fact("최근 연속 정답", recentStreak(student) + "회")
                        + "</dl>");
    }

    /** 뒤에서부터 세어 오답이나 힌트를 만나기 전까지의 연속 정답 수. */
    private static int recentStreak(WorksheetDetail.Student student) {
        List<WorksheetDetail.Response> responses = student.responses();
        int streak = 0;
        for (int index = responses.size() - 1; index >= 0; index--) {
            WorksheetDetail.Response response = responses.get(index);
            if (response.score() != response.maxScore() || response.hintUsed()) {
                return streak;
            }
            streak++;
        }
        return streak;
    }

    private static String comparisonCard(int studentRate, int classRate, int gap) {
        String tone = gap > 0 ? "above" : gap < 0 ? "below" : "";
        return ReportStyle.card("같은 학습지 기준", "학급 비교",
                "이 비교는 현재 학습지 응답만을 기준으로 하며 등수나 전체 능력을 의미하지 않습니다.",
                "<dl class='facts'>"
                        + fact("학생 정답률", "<span class='" + tone + "'>" + studentRate + "%</span>")
                        + fact("학급 평균", classRate + "%")
                        + fact("평균과 차이", "<span class='" + tone + "'>"
                                + (gap > 0 ? "+" : "") + gap + "%p</span>")
                        + "</dl>");
    }

    private static String fact(String label, String value) {
        return "<div><dt>" + ReportStyle.escape(label) + "</dt><dd>" + value + "</dd></div>";
    }

    /**
     * 영역·난이도별 학생 값과 학급 평균을 함께 그린다.
     *
     * <p>학생 막대만 두면 60% 가 잘한 것인지 못한 것인지 알 수 없다. 화면도 학급 평균을 같이
     * 보여 주기 때문에 그 구성을 유지한다.
     */
    private static String breakdownBars(WorksheetDetail worksheet, WorksheetDetail.Student student,
                                        String dimension) {
        boolean isArea = "area".equals(dimension);
        List<String> keys = "area".equals(dimension)
                ? List.of("concept", "calculation", "reasoning", "problemSolving")
                : List.of("low", "mid", "high");

        StringBuilder out = new StringBuilder("<ul class='bars'>");
        int rank = 1;
        for (String key : keys) {
            Breakdown mine = breakdown(worksheet, List.of(student), dimension, key);
            Breakdown clazz = breakdown(worksheet, worksheet.students().stream()
                    .filter(row -> !"insufficient".equals(row.status())).toList(), dimension, key);
            out.append(ReportStyle.comparisonBar(rank++, (isArea ? areaLabel(key) : DisplayLabels.difficulty(key)),
                    mine.rate(), clazz.rate(), mine.questionCount(), mine.responseCount() == 0));
        }
        return out.append("</ul>").toString();
    }

    private static Breakdown breakdown(WorksheetDetail worksheet,
                                       List<WorksheetDetail.Student> students,
                                       String dimension, String key) {
        List<WorksheetDetail.Question> questions = worksheet.questions().stream()
                .filter(question -> key.equals("area".equals(dimension)
                        ? question.area() : question.difficulty()))
                .toList();
        int total = 0;
        int correct = 0;
        for (WorksheetDetail.Student student : students) {
            for (WorksheetDetail.Question question : questions) {
                WorksheetDetail.Response response = student.responses().stream()
                        .filter(row -> row.no() == question.no())
                        .findFirst().orElse(null);
                if (response == null || response.gradedBy() == null) {
                    continue;
                }
                total++;
                if (response.score() == response.maxScore()) {
                    correct++;
                }
            }
        }
        int rate = total == 0 ? 0 : (int) Math.round(correct * 100.0 / total);
        return new Breakdown(rate, questions.size(), total);
    }

    private static String recordTable(WorksheetDetail worksheet, WorksheetDetail.Student student) {
        StringBuilder rows = new StringBuilder();
        for (WorksheetDetail.Question question : worksheet.questions()) {
            WorksheetDetail.Response response = student.responses().stream()
                    .filter(row -> row.no() == question.no())
                    .findFirst().orElse(null);
            String result = response == null || response.gradedBy() == null ? "채점 대기"
                    : response.score() == response.maxScore()
                            ? (response.hintUsed() ? "힌트 후 정답" : "정답") : "오답";
            boolean wrong = "오답".equals(result);
            int classRate = questionAccuracy(worksheet, question.no());
            rows.append("<tr><td class='num'>").append(question.no())
                    .append("번</td><td><strong>").append(ReportStyle.escape(question.prompt()))
                    .append("</strong><small>")
                    .append(ReportStyle.escape(areaLabel(question.area())))
                    .append(" · ").append(ReportStyle.escape(
                            DisplayLabels.difficulty(question.difficulty())))
                    .append("</small></td><td><span class='rate").append(wrong ? " low" : "")
                    .append("'>").append(ReportStyle.escape(result))
                    .append("</span></td><td class='num'>").append(classRate)
                    .append("%</td></tr>");
        }
        return "<table><thead><tr><th class='num'>문항</th><th>문항 · 영역</th><th>결과</th>"
                + "<th class='num'>학급 정답률</th></tr></thead><tbody>" + rows
                + "</tbody></table>";
    }

    private static int questionAccuracy(WorksheetDetail worksheet, int questionNo) {
        List<WorksheetDetail.Response> responses = worksheet.students().stream()
                .filter(student -> !"insufficient".equals(student.status()))
                .flatMap(student -> student.responses().stream())
                .filter(response -> response.no() == questionNo)
                .filter(response -> response.gradedBy() != null)
                .toList();
        if (responses.isEmpty()) {
            return 0;
        }
        long correct = responses.stream()
                .filter(response -> response.score() == response.maxScore())
                .count();
        return (int) Math.round(correct * 100.0 / responses.size());
    }

    /**
     * 가장 낮은 점수를 받은 문항이 다루는 개념.
     *
     * <p>없으면 지어내지 않고 "추가 응답 확인"으로 둔다. 근거 없는 개념 이름을 교사에게
     * 건네면 그 개념을 다시 가르치게 된다.
     */
    private static String weakConcept(WorksheetDetail worksheet, WorksheetDetail.Student student) {
        return worksheet.questions().stream()
                .map(question -> Map.entry(question, student.responses().stream()
                        .filter(row -> row.no() == question.no())
                        .findFirst().orElse(null)))
                .filter(entry -> entry.getValue() != null && entry.getValue().gradedBy() != null)
                .sorted((a, b) -> Double.compare(
                        (double) a.getValue().score() / Math.max(a.getValue().maxScore(), 1),
                        (double) b.getValue().score() / Math.max(b.getValue().maxScore(), 1)))
                .findFirst()
                .map(entry -> {
                    WorksheetDetail.Question question = entry.getKey();
                    if (!question.steps().isEmpty()) {
                        String conceptId = question.steps().get(0).conceptId();
                        return worksheet.concepts().stream()
                                .filter(concept -> concept.id().equals(conceptId))
                                .map(WorksheetDetail.Concept::label)
                                .findFirst()
                                .orElse(areaLabel(question.area()));
                    }
                    return areaLabel(question.area());
                })
                .orElse("추가 응답 확인");
    }

    private static int weakConceptCount(WorksheetDetail worksheet,
                                        WorksheetDetail.Student student) {
        return (int) worksheet.concepts().stream()
                .filter(concept -> {
                    int total = 0;
                    int correct = 0;
                    for (WorksheetDetail.Question question : worksheet.questions()) {
                        for (WorksheetDetail.QuestionStep step : question.steps()) {
                            if (!concept.id().equals(step.conceptId())) {
                                continue;
                            }
                            total++;
                            WorksheetDetail.ResponseStep answered = student.responses().stream()
                                    .filter(response -> response.no() == question.no())
                                    .flatMap(response -> response.steps().stream())
                                    .filter(item -> item.order() == step.order())
                                    .findFirst().orElse(null);
                            if (answered != null && answered.attempted() && answered.correct()) {
                                correct++;
                            }
                        }
                    }
                    return total > 0 && Math.round(correct * 100.0 / total) < WEAK_RATE;
                })
                .count();
    }

    private static String observation(String weakConcept) {
        return weakConcept + " 관련 문항에서 정답의 근거를 말로 설명하게 하고, 같은 구조를 다시 "
                + "해결하는지 확인해 주세요.";
    }

    private static Metrics metrics(WorksheetDetail.Student student) {
        List<WorksheetDetail.Response> graded = student.responses().stream()
                .filter(response -> response.gradedBy() != null)
                .toList();
        int correct = (int) graded.stream()
                .filter(response -> response.score() == response.maxScore())
                .count();
        int rate = graded.isEmpty() ? 0 : (int) Math.round(correct * 100.0 / graded.size());
        return new Metrics(graded.size(), correct, rate);
    }

    private record Metrics(int solvedCount, int correctCount, int scoreRate) {
    }

    private record Breakdown(int rate, int questionCount, int responseCount) {
    }
}
