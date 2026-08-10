package com.cenedu.backend.domain.analysis.service;

/**
 * 학급 보고서와 개인 보고서가 함께 쓰는 조판.
 *
 * <p>취약점 분석 화면의 스타일을 옮긴 것이다. 프론트 {@code WeaknessComponents.scss} 의
 * <b>후반부 개정본</b>을 기준으로 한다. 파일 앞부분에 진한 파랑 계열이 남아 있지만 뒤에서 덮여
 * 화면에 나오지 않는다. 앞부분을 보고 옮기면 화면에 없는 색이 종이에만 생긴다.
 *
 * <p>두 보고서가 이 클래스를 함께 쓰는 이유는, 한쪽만 손대면 같은 교사가 같은 날 받은 두 장이
 * 서로 다른 서식이 되기 때문이다.
 *
 * <p>본문 글꼴은 화면과 같은 스택을 쓰되 파일을 심지 않았다. 배포처마다 라이선스와 용량을 다시
 * 따져야 한다. 크기·색·자간은 화면 값 그대로라 대체 글꼴에서도 위계는 유지된다.
 */
final class ReportStyle {

    private static final int WEAK_RATE = 60;
    private static final int STABLE_RATE = 80;

    private ReportStyle() {
    }

    static String document(String title, String pages) {
        return document(title, pages, "");
    }

    /**
     * @param extraCss 보고서 종류마다 다른 규칙. 공통 css 뒤에 붙어 필요한 것만 덮는다.
     *                 두 보고서에 다 쓰이는 규칙은 여기가 아니라 {@link #css()} 로 올린다.
     */
    static String document(String title, String pages, String extraCss) {
        return "<!doctype html><html lang='ko'><head><meta charset='utf-8'><title>"
                + escape(title) + "</title><style>" + css() + extraCss + "</style></head><body>"
                + pages + "</body></html>";
    }

    static String page(String body) {
        return "<section class='sheet'>" + body + "</section>";
    }

    static String card(String kicker, String title, String footer, String body) {
        return "<section class='card'><header><span>" + escape(kicker) + "</span><h2>"
                + escape(title) + "</h2></header>" + body
                + (footer == null ? "" : "<footer>" + escape(footer) + "</footer>")
                + "</section>";
    }

    static String statCard(String title, String value, String note) {
        return "<article><small>" + escape(title) + "</small><b>" + escape(value)
                + "</b><span>" + escape(note) + "</span></article>";
    }

    static String notice(String text) {
        return "<p class='notice'>" + escape(text) + "</p>";
    }

    /**
     * 막대 한 줄. 정답률이 낮을수록 진한 색을 쓴다.
     *
     * <p>값이 없는 줄은 흐리게 두고 막대를 그리지 않는다. 0%로 그리면 "전부 틀렸다"로 읽힌다.
     */
    static String bar(int rank, String label, int percent, String note, boolean empty) {
        return "<li" + (empty ? " class='muted'" : "") + "><div class='row'>"
                + "<span class='rank'>" + rank + "</span>"
                + "<span class='label'>" + escape(label) + "</span>"
                + "<span class='value'>" + (empty ? "-" : percent + "%")
                + "<small>" + escape(note) + "</small></span></div>"
                + (empty ? "" : track(percent, ""))
                + "</li>";
    }

    /**
     * 학생 값과 학급 평균을 겹쳐 그린 막대.
     *
     * <p>학생 막대만 두면 60% 가 잘한 것인지 못한 것인지 알 수 없다. 학급 평균 자리에 눈금을
     * 세워 한눈에 비교되게 한다.
     */
    static String comparisonBar(int rank, String label, int percent, int classPercent,
                                int questionCount, boolean empty) {
        String note = questionCount + "문항 · 학급 " + (empty ? "-" : classPercent + "%");
        return "<li" + (empty ? " class='muted'" : "") + "><div class='row'>"
                + "<span class='rank'>" + rank + "</span>"
                + "<span class='label'>" + escape(label) + "</span>"
                + "<span class='value'>" + (empty ? "-" : percent + "%")
                + "<small>" + escape(note) + "</small></span></div>"
                + (empty ? "" : track(percent,
                        "<b class='mark' style='left:" + classPercent + "%'></b>"))
                + "</li>";
    }

    private static String track(int percent, String extra) {
        String tone = percent < WEAK_RATE ? " high" : percent < STABLE_RATE ? " mid" : "";
        return "<div class='track" + tone + "'><i style='width:" + percent + "%'></i>"
                + extra + "</div>";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String css() {
        return """
                @page{size:A4;margin:0}
                *,*::before,*::after{margin:0;padding:0;box-sizing:border-box}
                body{background:#e9edf1;color:#465166;
                  font-family:"Pretendard Variable",Pretendard,-apple-system,system-ui,
                    "Noto Sans KR","Malgun Gothic",sans-serif;
                  text-rendering:optimizeLegibility;-webkit-print-color-adjust:exact;
                  print-color-adjust:exact}
                .sheet{width:210mm;height:297mm;margin:0 auto;padding:14mm 15mm;background:#f4f6f8;
                  page-break-after:always;overflow:hidden}
                .sheet:last-child{page-break-after:auto}

                /* weakness-page__page-header */
                .page-header{margin-bottom:12px;padding:18px 20px 16px;display:flex;
                  align-items:flex-end;justify-content:space-between;gap:20px;background:#fff;
                  border:1px solid #dde2e8;border-radius:10px}
                .page-header h1{display:flex;align-items:center;gap:10px;color:#172033;
                  font-size:22px;font-weight:700;line-height:1.35;letter-spacing:-.4px}
                .page-header p{margin-top:5px;color:#747f91;font-size:13px}
                .page-header>span{color:#596579;font-size:12px;white-space:nowrap;text-align:right;
                  line-height:1.7}

                /* diagnosis-summary__card */
                .summary{margin-bottom:12px;display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
                .summary article{padding:16px 18px;background:#fff;border:1px solid #dde2e8;
                  border-radius:10px}
                .summary small{display:block;color:#596579;font-size:12px}
                .summary b{display:block;margin-top:6px;color:#172033;font-size:24px;font-weight:700;
                  font-variant-numeric:tabular-nums}
                .summary span{display:block;margin-top:3px;color:#747f91;font-size:11px}

                /* grading-notice */
                .notice{margin-bottom:12px;padding:11px 14px;background:#f7f5ef;
                  border:1px solid #ded7c5;border-radius:8px;color:#6f6248;font-size:12px}

                /* diagnosis-card */
                .card{margin-bottom:12px;padding:20px;background:#fff;border:1px solid #dde2e8;
                  border-radius:10px}
                .card>header{margin-bottom:16px}
                .card>header>span{display:block;color:#596579;font-size:11px;font-weight:600}
                .card>header>h2{margin-top:3px;color:#172033;font-size:18px;font-weight:700}
                .card>footer{margin-top:12px;color:#8995a3;font-size:11px;line-height:1.55}

                /* student-priority dl */
                .facts{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
                .facts>div{padding:12px;background:#f6f8fa;border-radius:9px}
                .facts dt{color:#8b96a3;font-size:11px}
                .facts dd{margin-top:4px;color:#34475b;font-size:16px;font-weight:600;
                  font-variant-numeric:tabular-nums}
                .facts .above{color:#356c55}
                .facts .below{color:#a96762}

                /* concept-bars */
                .bars{display:grid;gap:4px;list-style:none}
                .bars li{padding:9px 8px}
                .bars .row{display:grid;grid-template-columns:16px 1fr auto;align-items:center;
                  gap:8px}
                .bars .rank{color:#747f91;font-size:12px;font-variant-numeric:tabular-nums}
                .bars .label{min-width:0;color:#172033;font-size:13px;font-weight:600;
                  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                .bars .value{color:#465166;font-size:12px;font-variant-numeric:tabular-nums}
                .bars .value small{margin-left:4px;color:#747f91;font-size:11px}
                .bars .track{position:relative;height:6px;margin:6px 0 0 24px;background:#eef0f3;
                  border-radius:2px}
                .bars .track i{display:block;height:100%;background:#8a94a3;border-radius:inherit}
                .bars .track.high i{background:#a96762}
                .bars .track.mid i{background:#a58a55}
                .bars .track .mark{position:absolute;top:-3px;width:2px;height:12px;
                  background:#596579;border-radius:1px}
                .bars .muted .label{color:#747f91;font-weight:500}

                /* matrix-table */
                table{width:100%;border-collapse:collapse;font-size:13px}
                th{padding:0 8px 8px;color:#7c8997;font-size:12px;font-weight:700;text-align:left;
                  border-bottom:1px solid #ebeef2}
                td{padding:9px 8px;color:#34475c;border-bottom:1px solid #f1f3f5;
                  vertical-align:middle}
                tbody tr:last-child td{border-bottom:0}
                td.num,th.num{text-align:right;font-variant-numeric:tabular-nums}
                td small{display:block;margin-top:2px;color:#747f91;font-size:11px}
                td strong{font-weight:600;color:#172033}

                /* status-badge */
                .badge{display:inline-flex;align-items:center;gap:6px;color:#667185;font-size:12px;
                  font-weight:600;white-space:nowrap}
                .badge::before{width:7px;height:7px;border-radius:50%;background:#9aa4b1;content:""}
                .badge.priority{color:#8f4d48}
                .badge.priority::before{background:#a96762}
                .badge.review{color:#80652f}
                .badge.review::before{background:#a58a55}
                .badge.stable{color:#356c55}
                .badge.stable::before{background:#4f806b}
                .badge.insufficient{color:#747f91}

                /* score-rate */
                .rate{color:#279268;font-weight:600;font-variant-numeric:tabular-nums}
                .rate.low{color:#d65b53}
                """;
    }
}
