package com.cenedu.backend.domain.analysis.service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.ClassDashboard;
import com.cenedu.backend.global.common.enums.DisplayLabels;

import org.springframework.stereotype.Service;

/**
 * 학급 집계를 교사용 단체 보고서 HTML/PDF 로 만든다.
 *
 * <p>보고서에 들어가는 숫자는 화면과 같은 계산({@link ClassDashboardService})에서 가져온다.
 * 여기서 다시 계산하면 화면과 종이가 다른 값을 말하게 된다.
 *
 * <p>서버가 한글 표기를 붙이는 곳은 여기뿐이다. 교사에게 그대로 전달되는 문서라서다. 화면 라벨은
 * 프론트가 붙인다.
 */
@Service
public class ClassOverviewReportService {

    private static final int TOP_PROBLEM_COUNT = 8;

    /** 한 장에 담을 학생 수. A4 한 장에 머리말과 표 머리를 빼고 들어가는 줄 수다. */
    private static final int STUDENT_ROWS_PER_PAGE = 18;

    private static final int STABLE_RATE = 80;
    private static final int REVIEW_RATE = 60;

    private final ClassDashboardService dashboard;
    private final WeaknessAnalysisQueryService worksheets;
    private final BrowserPdfRenderer renderer;

    public ClassOverviewReportService(ClassDashboardService dashboard,
                                      WeaknessAnalysisQueryService worksheets,
                                      BrowserPdfRenderer renderer) {
        this.dashboard = dashboard;
        this.worksheets = worksheets;
        this.renderer = renderer;
    }

    public String html(String assessmentId) {
        return render(dashboard.summary(assessmentId), worksheets.metrics(assessmentId));
    }

    public Path pdf(String assessmentId) {
        return renderer.render("class-" + safeFileName(assessmentId), html(assessmentId)).pdf();
    }

    /**
     * 취약점 분석 화면의 스타일을 그대로 옮긴 조판.
     *
     * <p>프론트 {@code WeaknessComponents.scss} 의 <b>후반부 개정본</b>을 기준으로 한다. 파일
     * 앞부분에 진한 파랑 계열이 남아 있지만 뒤에서 덮여 화면에 나오지 않는다. 앞부분을 보고
     * 옮기면 화면에 없는 색이 종이에만 생긴다.
     *
     * <p>본문 글꼴은 화면과 같은 스택을 쓴다. 다만 서버에 Pretendard 가 없으면 맑은 고딕으로
     * 내려앉는다. 글꼴 파일을 보고서에 심지 않은 것은 배포처마다 라이선스와 용량을 다시 따져야
     * 해서다. 크기·자간·색은 화면 값 그대로라 대체 글꼴에서도 위계는 유지된다.
     */
    private static String render(ClassDashboard data,
                                 WeaknessAnalysisQueryService.WorksheetMetrics metrics) {
        ClassDashboard.Overall o = data.overall();
        StringBuilder pages = new StringBuilder();

        pages.append(ReportStyle.page(pageHeader(data)
                + summaryCards(o, metrics)
                + ReportStyle.notice("학급 정답률과 취약 판정은 자료 부족 학생과 채점 대기 문항을 제외한 "
                        + "값이며, 등수나 전체 학업 능력을 의미하지 않습니다.")
                + ReportStyle.card("사고 유형 기준", "영역별 결과", null, areaBars(data.areas()))
                + ReportStyle.card("문항 난이도 기준", "난이도별 결과", null,
                        difficultyBars(data.difficulties()))));

        // 학생 수는 학급마다 다르다. 한 장에 몰아넣으면 정원이 큰 학급에서 뒷줄이 잘린다.
        List<List<ClassDashboard.StudentRow>> chunks = chunk(data.students(), STUDENT_ROWS_PER_PAGE);
        for (int index = 0; index < chunks.size(); index++) {
            String title = chunks.size() == 1
                    ? "학생별 결과"
                    : "학생별 결과 (" + (index + 1) + "/" + chunks.size() + ")";
            boolean last = index == chunks.size() - 1;
            pages.append(ReportStyle.page(pageHeader(data)
                    + ReportStyle.card("학생 진단", title,
                            last ? "상태 기준: 80% 이상 현재 확인 기준 충족 · 60~79% 추가 확인 필요 · "
                                    + "60% 미만 집중 지도 필요" : null,
                            studentTable(chunks.get(index)))));
        }

        pages.append(ReportStyle.page(pageHeader(data)
                + ReportStyle.card("학급 정답률 낮은 순", "먼저 확인할 문항",
                        "원본 참고값은 원래 문항과 당시 응답 집단에서 계산된 값으로, "
                                + "현재 학급 평균과 직접 동일시하지 않습니다.",
                        problemTable(data.problems()))));

        return ReportStyle.document(data.assessmentTitle() + " 학급 분석", pages.toString());
    }

    /**
     * 표를 장 단위로 자른다.
     *
     * <p>장 높이를 고정해 두었기 때문에 넘치는 줄은 {@code overflow:hidden} 으로 사라진다.
     * 화면이라면 스크롤로 드러나지만 종이에서는 그냥 없어진다 — 누락을 알아챌 방법이 없다.
     * 그래서 넘칠 것 같으면 장을 넘긴다.
     */
    private static <T> List<List<T>> chunk(List<T> rows, int size) {
        if (rows.isEmpty()) {
            return List.of(List.of());
        }
        List<List<T>> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < rows.size(); start += size) {
            chunks.add(rows.subList(start, Math.min(start + size, rows.size())));
        }
        return chunks;
    }



    private static String pageHeader(ClassDashboard data) {
        return "<header class='page-header'><div><h1>취약점 분석</h1>"
                + "<p>학급과 학생의 응답을 분석하고 보고서에 담길 내용을 확인합니다.</p></div>"
                + "<span>" + ReportStyle.escape(data.assessmentTitle()) + "<br>"
                + data.assessmentDate() + " 기준<br>"
                + (data.simulation() ? "가상 학급" : "담당 학급") + "</span></header>";
    }

    /**
     * 화면 상단 요약 카드 네 장.
     *
     * <p>화면은 종합평가일 때 세 번째 자리에 평균 소요 시간을 보여 주지만 여기서는 항상 취약
     * 개념을 쓴다. 백엔드가 문항별 소요 시간을 기록하지 않아 그 자리에 늘 0분이 찍힌다.
     * 시간을 저장하기 시작하면 화면과 같은 분기를 넣는다.
     *
     * <p>취약 개념·취약 학생은 화면과 같은 계산({@code getWorksheetMetrics})에서 가져온다.
     * 학급 정답률 옆 두 칸이 화면과 다른 숫자를 말하면 교사가 어느 쪽을 믿을지 알 수 없다.
     */
    private static String summaryCards(ClassDashboard.Overall o,
                                       WeaknessAnalysisQueryService.WorksheetMetrics metrics) {
        return "<section class='summary'>"
                + ReportStyle.statCard("참여 학생", o.studentCount() + "명", o.problemCount() + "문항")
                + ReportStyle.statCard("학급 정답률", o.correctRatePercent() + "%",
                        o.correctCount() + "/" + o.attemptCount() + " 정답")
                + ReportStyle.statCard("취약 개념", metrics.weakConceptCount() + "개", "달성률 60% 미만")
                + ReportStyle.statCard("취약 학생", metrics.priorityCount() + "명", "정답률 60% 미만")
                + "</section>";
    }




    private static String areaBars(List<ClassDashboard.ClassAreaRow> areas) {
        StringBuilder out = new StringBuilder("<ul class='bars'>");
        int rank = 1;
        for (ClassDashboard.ClassAreaRow area : areas) {
            out.append(ReportStyle.bar(rank++, DisplayLabels.area(area.evaluationArea()),
                    area.correctRatePercent(),
                    area.problemCount() + "문항", area.problemCount() == 0));
        }
        return out.append("</ul>").toString();
    }

    private static String difficultyBars(List<ClassDashboard.DifficultyRow> rows) {
        StringBuilder out = new StringBuilder("<ul class='bars'>");
        int rank = 1;
        for (ClassDashboard.DifficultyRow row : rows) {
            out.append(ReportStyle.bar(rank++, DisplayLabels.difficulty(row.difficultyBand()) + " 난이도",
                    row.correctRatePercent(),
                    row.correctCount() + "/" + row.totalCount() + " 정답",
                    row.problemCount() == 0));
        }
        return out.append("</ul>").toString();
    }


    private static String studentTable(List<ClassDashboard.StudentRow> students) {
        StringBuilder rows = new StringBuilder();
        for (ClassDashboard.StudentRow student : students) {
            rows.append("<tr><td><strong>").append(ReportStyle.escape(student.studentName()))
                    .append("</strong><small>").append(ReportStyle.escape(student.studentId()))
                    .append("</small></td><td class='num'>").append(student.correctCount())
                    .append("/").append(student.totalCount())
                    .append("</td><td class='num'><span class='rate")
                    .append(student.correctRatePercent() < REVIEW_RATE ? " low" : "").append("'>")
                    .append(student.correctRatePercent()).append("%</span></td><td class='num'>")
                    .append(student.hintCount()).append("회</td><td><span class='badge ")
                    .append(ReportStyle.escape(student.status())).append("'>")
                    .append(ReportStyle.escape(DisplayLabels.status(student.status())))
                    .append("</span></td></tr>");
        }
        return "<table><thead><tr><th>학생</th><th class='num'>정답</th><th class='num'>정답률</th>"
                + "<th class='num'>힌트</th><th>상태</th></tr></thead><tbody>" + rows
                + "</tbody></table>";
    }

    private static String problemTable(List<ClassDashboard.ProblemRow> problems) {
        StringBuilder rows = new StringBuilder();
        problems.stream()
                .sorted(Comparator.comparingInt(ClassDashboard.ProblemRow::classCorrectRatePercent))
                .limit(TOP_PROBLEM_COUNT)
                .forEach(problem -> rows.append("<tr><td class='num'>")
                        .append(problem.problemNumber())
                        .append("</td><td><strong>").append(ReportStyle.escape(problem.problemTitle()))
                        .append("</strong><small>")
                        .append(ReportStyle.escape(DisplayLabels.area(problem.evaluationArea())))
                        .append(" · ").append(ReportStyle.escape(DisplayLabels.difficulty(
                                problem.difficultyBand())))
                        .append("</small></td><td class='num'><span class='rate")
                        .append(problem.classCorrectRatePercent() < REVIEW_RATE ? " low" : "")
                        .append("'>").append(problem.classCorrectRatePercent())
                        .append("%</span><small>").append(problem.correctCount()).append("/")
                        .append(problem.totalCount()).append(" 정답</small></td><td class='num'>")
                        .append(problem.referenceSuccessRate() == null ? "-"
                                : problem.referenceSuccessRate() + "%")
                        .append("</td></tr>"));
        return "<table><thead><tr><th class='num'>문항</th><th>문항 · 영역</th>"
                + "<th class='num'>학급 정답률</th><th class='num'>원본 참고</th></tr></thead><tbody>"
                + rows + "</tbody></table>";
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

}
