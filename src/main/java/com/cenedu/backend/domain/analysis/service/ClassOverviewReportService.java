package com.cenedu.backend.domain.analysis.service;

import java.nio.file.Path;
import java.util.Comparator;

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

    private final ClassDashboardService dashboard;
    private final BrowserPdfRenderer renderer;

    public ClassOverviewReportService(ClassDashboardService dashboard,
                                      BrowserPdfRenderer renderer) {
        this.dashboard = dashboard;
        this.renderer = renderer;
    }

    public String html(String assessmentId) {
        return render(dashboard.summary(assessmentId));
    }

    public Path pdf(String assessmentId) {
        return renderer.render("class-" + safeFileName(assessmentId), html(assessmentId)).pdf();
    }

    private static String render(ClassDashboard data) {
        StringBuilder students = new StringBuilder();
        for (ClassDashboard.StudentRow student : data.students()) {
            String css = student.correctRatePercent() >= 80 ? "stable"
                    : student.correctRatePercent() >= 60 ? "watch" : "attention";
            students.append("<tr><td><strong>").append(escape(student.studentName()))
                    .append("</strong><small>").append(escape(student.studentId()))
                    .append("</small></td><td>").append(student.correctCount()).append("/")
                    .append(student.totalCount()).append("</td><td><b>")
                    .append(student.correctRatePercent()).append("%</b></td><td>")
                    .append(student.hintCount()).append("회</td><td><span class='")
                    .append(css).append("'>")
                    .append(escape(DisplayLabels.status(student.status())))
                    .append("</span></td></tr>");
        }

        StringBuilder areas = new StringBuilder();
        for (ClassDashboard.ClassAreaRow area : data.areas()) {
            areas.append("<article><small>")
                    .append(escape(DisplayLabels.area(area.evaluationArea())))
                    .append("</small><b>").append(area.correctRatePercent())
                    .append("%</b><span>").append(area.correctCount()).append("/")
                    .append(area.totalCount()).append(" 정답 · ")
                    .append(area.problemCount()).append("문항</span></article>");
        }

        StringBuilder difficulties = new StringBuilder();
        for (ClassDashboard.DifficultyRow row : data.difficulties()) {
            difficulties.append("<article><small>").append(difficulty(row.difficultyBand()))
                    .append(" 난이도</small><b>").append(row.correctRatePercent())
                    .append("%</b><span>").append(row.correctCount()).append("/")
                    .append(row.totalCount()).append(" 정답</span></article>");
        }

        StringBuilder problems = new StringBuilder();
        data.problems().stream()
                .sorted(Comparator.comparingInt(ClassDashboard.ProblemRow::classCorrectRatePercent))
                .limit(TOP_PROBLEM_COUNT)
                .forEach(problem -> problems.append("<tr><td>").append(problem.problemNumber())
                        .append("</td><td><strong>").append(escape(problem.problemTitle()))
                        .append("</strong><small>")
                        .append(escape(DisplayLabels.area(problem.evaluationArea())))
                        .append(" · ").append(difficulty(problem.difficultyBand()))
                        .append("</small></td><td><b>").append(problem.classCorrectRatePercent())
                        .append("%</b><small>").append(problem.correctCount()).append("/")
                        .append(problem.totalCount()).append(" 정답</small></td><td>")
                        .append(problem.referenceSuccessRate() == null ? "-"
                                : problem.referenceSuccessRate() + "%")
                        .append("</td></tr>"));

        ClassDashboard.Overall o = data.overall();
        return """
                <!doctype html><html lang='ko'><head><meta charset='utf-8'><style>
                @page{size:A4;margin:0}*{box-sizing:border-box}body{margin:0;background:#dce5ee;color:#17243b;font-family:'Malgun Gothic',sans-serif}.page{width:210mm;height:297mm;margin:0 auto 8mm;background:#f7f9fc;padding:16mm 17mm;page-break-after:always;overflow:hidden}.page:last-child{page-break-after:auto}
                .top{display:flex;justify-content:space-between;border-bottom:2px solid #14395b;padding-bottom:7mm}.k{font-size:10px;color:#16858b;font-weight:900;letter-spacing:1.4px}h1{font-size:26px;color:#14395b;margin:4px 0}.meta{text-align:right;color:#64748b;font-size:11px;line-height:1.7}h2{font-size:17px;color:#14395b;margin:8mm 0 4mm}
                .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}.grid article{background:#fff;border:1px solid #dbe4ee;border-radius:11px;padding:11px}.grid small,.grid b,.grid span{display:block}.grid small,.grid span{color:#64748b;font-size:9px}.grid b{font-size:20px;color:#14395b;margin:3px 0}.area-grid{grid-template-columns:repeat(4,1fr)}
                table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #dbe4ee;font-size:10.5px}th,td{padding:7px 9px;border-bottom:1px solid #e8edf2;text-align:left}th{background:#edf3f8;color:#3e5872}td small{display:block;color:#64748b;margin-top:2px}.stable,.watch,.attention{display:inline-block;border-radius:20px;padding:3px 8px;font-weight:800}.stable{background:#dcfce7;color:#147544}.watch{background:#fff3c4;color:#8a6500}.attention{background:#fee2e2;color:#b42318}
                .notice{margin-top:8px;color:#64748b;font-size:9px}.simulation{display:inline-block;background:#dcf5ef;color:#087866;border-radius:99px;padding:5px 9px;font-weight:800}
                </style></head><body>
                """ + header(data, "담당 학생 전체 현황")
                + "<h2>학급 한눈에 보기</h2><section class='grid'>"
                + stat("참여 학생", o.studentCount() + "명", o.problemCount() + "문항")
                + stat("학급 정답률", o.correctRatePercent() + "%",
                        o.correctCount() + "/" + o.attemptCount() + " 정답")
                + stat("힌트 사용", o.hintCount() + "회", "독립 정답과 구분")
                + stat("집중 지도", o.attentionStudentCount() + "명", "정답률 60% 미만")
                + "</section><h2>평가 영역별 학급 결과</h2><section class='grid area-grid'>" + areas
                + "</section><h2>난이도별 학급 결과</h2><section class='grid'>" + difficulties
                + "</section><h2>학생별 결과</h2><table><thead><tr><th>학생</th><th>정답</th>"
                + "<th>정답률</th><th>힌트</th><th>상태</th></tr></thead><tbody>" + students
                + "</tbody></table><p class='notice'>상태 기준: 80% 이상 현재 확인 기준 충족 · "
                + "60~79% 추가 확인 필요 · 60% 미만 집중 지도 필요</p></main>"
                + header(data, "먼저 확인할 문항")
                + "<h2>학급 정답률이 낮은 문항</h2><table><thead><tr><th>#</th><th>문항·영역</th>"
                + "<th>학급 결과</th><th>원본 참고</th></tr></thead><tbody>" + problems
                + "</tbody></table><p class='notice'>원본 참고값은 원래 문항과 당시 응답 집단에서 "
                + "계산된 값으로, 현재 학급 평균과 직접 동일시하지 않습니다.</p>"
                + "<h2>교사 확인 순서</h2><section class='grid'>"
                + stat("1", "문항 확인", "학급 전체가 어려워한 문항")
                + stat("2", "학생 확인", "집중 지도 학생의 개별 결과")
                + stat("3", "영역 확인", "개념·문제해결·계산·해석")
                + stat("4", "다음 수업", "재설명 후 확인 문제 배치")
                + "</section></main></body></html>";
    }

    private static String header(ClassDashboard data, String title) {
        return "<main class='page'><header class='top'><div><span class='k'>ONE REPORT · CLASS"
                + "</span><h1>" + escape(title) + "</h1></div><div class='meta'>"
                + escape(data.assessmentTitle()) + "<br>" + data.assessmentDate() + "<br>"
                + (data.simulation() ? "<span class='simulation'>가상 데이터</span>" : "실제 학급 데이터")
                + "</div></header>";
    }

    private static String stat(String label, String value, String note) {
        return "<article><small>" + escape(label) + "</small><b>" + escape(value)
                + "</b><span>" + escape(note) + "</span></article>";
    }

    private static String difficulty(String band) {
        return DisplayLabels.difficulty(band);
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
