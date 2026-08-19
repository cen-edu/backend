package com.cenedu.backend.domain.analysis.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 문장 생성에 넘기는 정제된 요청. analysis 도메인이 소유하는 AI 독립 계약이다.
 *
 * <p>학생이 답안란에 직접 쓴 텍스트가 {@link AnswerUnit#studentAnswer()} 로 들어온다. 이 객체를
 * 만드는 쪽이 길이를 자르고, 구현체는 이 값을 <b>데이터로만</b> 다뤄야 한다. 프롬프트 본문에
 * 이어 붙이지 않고 JSON 값으로 전달한다.
 *
 * @param gradedItems            채점이 끝나 문장을 만들 문항. 이 목록 밖의 문항은 응답에 와도 버린다
 * @param unansweredItemNumbers  학생이 답안을 내지 않은 문항 번호. 문항별 문장을 만들지 않고
 *                               종합 관찰에서만 다룬다
 */
public record AnalysisReportRequest(
        long assignmentStudentId,
        StudentSummary summary,
        List<GradedItem> gradedItems,
        List<Integer> unansweredItemNumbers,
        List<WeakSubcategory> weakSubcategories
) {
    public AnalysisReportRequest {
        gradedItems = List.copyOf(gradedItems);
        unansweredItemNumbers = List.copyOf(unansweredItemNumbers);
        weakSubcategories = List.copyOf(weakSubcategories);
    }

    /** 문장을 만들어도 되는 문항. 생성 결과를 대조하는 기준이다. */
    public List<Long> gradedWorksheetItemIds() {
        return gradedItems.stream().map(GradedItem::worksheetItemId).toList();
    }

    /**
     * @param accuracyRate      학생 정답률(백분율). 채점 결과가 없으면 {@code null}
     * @param classAccuracyRate 같은 학습지 학급 정답률
     */
    public record StudentSummary(
            int totalItemCount,
            int gradedItemCount,
            int correctItemCount,
            BigDecimal accuracyRate,
            BigDecimal classAccuracyRate
    ) {
    }

    /**
     * @param resultType        CORRECT / PARTIAL_CORRECT / INCORRECT
     * @param classAccuracyRate 이 문항의 학급 정답률. 학생 결과를 학급과 견주는 근거다
     */
    public record GradedItem(
            Long worksheetItemId,
            int itemNumber,
            String questionTitle,
            String evaluationArea,
            String difficultyBand,
            String resultType,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal classAccuracyRate,
            List<AnswerUnit> answerUnits
    ) {
        public GradedItem {
            answerUnits = List.copyOf(answerUnits);
        }
    }

    public record AnswerUnit(
            String label,
            String studentAnswer,
            String correctAnswer,
            String resultType
    ) {
    }

    public record WeakSubcategory(
            String subcategoryName,
            BigDecimal accuracyRate
    ) {
    }
}
