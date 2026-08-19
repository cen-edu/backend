package com.cenedu.backend.domain.analysis.report.pdf;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportResponse;
import com.cenedu.backend.domain.analysis.dto.response.CustomLearningSessionListResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentAnalysisSummaryResponse;

/**
 * 학생 분석 PDF 한 장에 들어갈 값을 템플릿이 읽기 좋은 모양으로 모은 것.
 *
 * <p>응답 DTO 다섯 개를 템플릿에 그대로 넘기지 않는다. 학습지 유형에 따라 어떤 API 의 어떤
 * 필드를 쓸지가 갈리는데, 그 분기를 템플릿에 두면 조건문이 화면 구조를 덮어 읽기 어려워진다.
 * <b>유형 판단과 값 결합은 Java 에서 끝내고 템플릿은 배치만 한다.</b>
 *
 * @param comparisonTitle 비교 막대의 제목. 학습평가면 평가 영역, 종합평가면 문항 유형이다
 * @param comparisonBars  학생과 학급을 나란히 보여줄 막대. 유형에 따라 출처 API 가 다르다
 * @param report          AI 문장. 아직 생성되지 않았어도 {@code null} 이 아니라 PENDING 상태로 온다
 */
public record StudentReportView(
        StudentAnalysisSummaryResponse summary,
        String comparisonTitle,
        List<ComparisonBar> comparisonBars,
        List<ComparisonBar> difficultyBars,
        List<ItemRow> items,
        AnalysisReportResponse report,
        List<CustomLearningSessionListResponse.CustomLearningSession> customSessions,
        String generatedAt
) {
    public StudentReportView {
        comparisonBars = List.copyOf(comparisonBars);
        difficultyBars = List.copyOf(difficultyBars);
        items = List.copyOf(items);
        customSessions = List.copyOf(customSessions);
    }

    /** AI 문장이 아직 없으면 그 영역만 비우고 나머지를 출력한다. */
    public boolean hasAiMessages() {
        return report != null && report.summaryMessage() != null;
    }

    /**
     * 학생과 학급을 견주는 막대 한 줄.
     *
     * @param referenceOnly 표본이 적어 참고용으로만 볼 값인지
     */
    public record ComparisonBar(
            String label,
            int itemCount,
            BigDecimal studentRate,
            BigDecimal classRate,
            boolean referenceOnly
    ) {

        /** 막대 너비. 값이 없으면 0 을 준다 — CSS 는 null 을 받으면 렌더링이 깨진다. */
        public String studentWidth() {
            return width(studentRate);
        }

        public String classWidth() {
            return width(classRate);
        }

        private String width(BigDecimal rate) {
            return rate == null ? "0" : rate.toPlainString();
        }
    }

    /**
     * 문항 한 줄. 채점 결과와 그 문항의 AI 문장을 합쳐 둔다.
     *
     * <p>화면은 두 목록을 {@code worksheetItemId} 로 맞춰 그리지만, PDF 는 한 번에 한 줄로
     * 인쇄되므로 미리 붙여 두는 편이 템플릿이 단순하다. AI 문장이 없는 문항은 세 값이 모두
     * {@code null} 이다 — 채점되지 않아 문장을 만들 근거가 없었던 문항이다.
     */
    public record ItemRow(
            int itemNumber,
            String questionTitle,
            String evaluationArea,
            String difficultyBand,
            String resultType,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal classAccuracyRate,
            String observation,
            String learningPoint,
            String retryGuide
    ) {

        public boolean hasAiMessage() {
            return observation != null;
        }
    }
}
