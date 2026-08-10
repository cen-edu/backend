package com.cenedu.backend.domain.analysis.service;

/**
 * 개인 보고서에만 쓰는 조판 규칙.
 *
 * <p>취약점 분석 화면의 개인 보기를 그대로 옮긴 것이다. 학급 보고서와 겹치는 규칙은 여기 두지
 * 않고 {@link ReportStyle} 에 둔다 — 같은 규칙을 두 곳에 두면 한쪽만 고쳐진다.
 */
final class StudentReportLayout {

    private StudentReportLayout() {
    }

    static String css() {
        return """
                /* student-analysis-view__header */
                .student-head{margin-bottom:12px;padding:18px 20px;background:#fff;
                  border:1px solid #dde2e8;border-radius:10px}
                .student-head h1{display:flex;align-items:center;gap:10px;color:#172033;
                  font-size:22px;font-weight:700;letter-spacing:-.4px}
                .student-head p{margin-top:5px;color:#747f91;font-size:13px}

                /* student-analysis-view__metrics : 카드 넉 장이 아니라 구분선으로 나뉜 한 장이다 */
                .metrics{margin-bottom:12px;display:grid;grid-template-columns:repeat(4,1fr);
                  background:#fff;border:1px solid #dde2e8;border-radius:10px;overflow:hidden}
                .metrics>div{padding:16px 20px;border-left:1px solid #ebeef2}
                .metrics>div:first-child{border-left:0}
                .metrics span{display:block;color:#596579;font-size:12px}
                .metrics strong{display:block;margin-top:6px;color:#172033;font-size:24px;
                  font-weight:700;font-variant-numeric:tabular-nums}
                .metrics strong small{margin-left:3px;color:#747f91;font-size:12px;font-weight:500}

                /* student-diagnosis-page__insight */
                .insight{margin-bottom:12px;padding:12px 15px;background:#f7f5ef;
                  border:1px solid #ded7c5;border-radius:8px;color:#6f6248;font-size:13px}
                .insight strong{color:#5b4f39;font-weight:700}

                /* student-priority : 왼쪽에 강조선이 있는 카드 */
                .priority{margin-bottom:12px;padding:18px 20px;background:#fff;
                  border:1px solid #dde2e8;border-left:3px solid #a96762;border-radius:10px}
                .priority>span{display:block;color:#596579;font-size:11px;font-weight:600}
                .priority h2{margin-top:3px;color:#172033;font-size:20px;font-weight:700}
                .priority p{margin-top:5px;color:#747f91;font-size:11px}

                /* 3칸 지표. 학급 비교와 지도 요약이 같은 모양을 쓴다 */
                .triple{margin-top:16px;display:grid;grid-template-columns:repeat(3,1fr)}
                .triple>div{padding:0 16px;border-left:1px solid #ebeef2}
                .triple>div:first-child{padding-left:0;border-left:0}
                .triple dt{color:#747f91;font-size:11px}
                .triple dd{margin-top:5px;color:#172033;font-size:20px;font-weight:700;
                  font-variant-numeric:tabular-nums}
                .triple .above{color:#356c55}
                .triple .below{color:#a96762}

                /* result-breakdown : 학생과 학급을 두 줄로 겹쳐 놓는다 */
                .compare{display:grid;gap:14px;list-style:none}
                .compare li{display:grid;grid-template-columns:96px 1fr;gap:12px;
                  align-items:center}
                .compare .name{color:#172033;font-size:13px;font-weight:600}
                .compare .name small{display:block;margin-top:2px;color:#747f91;font-size:11px;
                  font-weight:500}
                .compare .rows{display:grid;gap:6px}
                .compare .row{display:grid;grid-template-columns:1fr 38px;align-items:center;
                  gap:8px}
                .compare .track{height:14px;background:#f1f3f5;border-radius:3px}
                .compare .track i{display:block;height:100%;border-radius:inherit;
                  background:#4f806b}
                .compare .row.klass .track i{background:#8f9bab}
                .compare .pct{color:#465166;font-size:11px;font-weight:600;text-align:right;
                  font-variant-numeric:tabular-nums}
                .legend{margin-top:12px;display:flex;justify-content:flex-end;gap:14px;
                  color:#747f91;font-size:11px}
                .legend b{display:inline-block;width:9px;height:9px;margin-right:5px;
                  border-radius:2px;vertical-align:middle}
                .legend .s b{background:#4f806b}
                .legend .k b{background:#8f9bab}

                /* question-result */
                .qcards{display:grid;gap:10px}
                .qcard{border:1px solid #e2e7ec;border-radius:8px;overflow:hidden}
                .qcard>header{padding:11px 14px;display:flex;align-items:center;gap:11px;
                  background:#fafbfc}
                .qcard>header>span{width:27px;height:27px;flex:0 0 27px;display:inline-flex;
                  align-items:center;justify-content:center;background:#edf0f3;border-radius:8px;
                  color:#596579;font-size:12px;font-weight:800}
                .qcard>header h3{flex:1;color:#34465a;font-size:13px;font-weight:600;
                  overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                .qcard>header strong{font-size:12px}
                .qcard .correct{color:#278b64}
                .qcard .wrong{color:#cc5951}
                .qcard .hint{color:#80652f}
                .qcard .pending{color:#667185}
                .qcard .answers{padding:12px 15px;display:grid;
                  grid-template-columns:1fr 1fr 150px;gap:10px;
                  border-bottom:1px solid #ebeef2}
                .qcard .answers dt{color:#747f91;font-size:11px}
                .qcard .answers dd{margin-top:4px;color:#35475a;font-size:12px;font-weight:600;
                  line-height:1.45}
                .qcard .guidance{padding:14px 16px;display:grid;grid-template-columns:repeat(3,1fr);
                  gap:10px}
                .qcard .guidance>div{padding:10px;background:#f6f8fa;border-radius:8px}
                .qcard .guidance dt{color:#8b96a3;font-size:11px}
                .qcard .guidance dd{margin-top:4px;color:#3e5064;font-size:12px;font-weight:700;
                  line-height:1.45}
                """;
    }
}
