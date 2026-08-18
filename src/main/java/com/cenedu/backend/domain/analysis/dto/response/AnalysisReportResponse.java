package com.cenedu.backend.domain.analysis.dto.response;

import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;

/**
 * 학생 상세 화면의 AI 분석 문장.
 *
 * <p>보고서가 아직 만들어지지 않았어도 오류가 아니라 {@code PENDING} 상태로 응답한다. 화면이
 * "생성 전"과 "조회 실패"를 구분하지 않아도 되게 하려는 것이다.
 *
 * @param customLearningMessage 맞춤 학습 결과 해석. AI가 아니라 백엔드 규칙으로 만들며 저장하지
 *                              않는다. 규칙이 정해지기 전까지는 항상 {@code null} 이다
 * @param itemMessages          문항별 문장. <b>채점 완료 문항만</b> 담기므로 학습지 문항 수보다
 *                              적을 수 있다. {@code worksheetItemId} 로 문항 결과와 맞춘다
 */
public record AnalysisReportResponse(
        GenerationStatus generationStatus,
        String summaryMessage,
        String customLearningMessage,
        List<ItemMessage> itemMessages,
        String overallObservation
) {
    public AnalysisReportResponse {
        itemMessages = List.copyOf(itemMessages);
    }

    /** 아직 생성된 적이 없는 보고서의 응답. */
    public static AnalysisReportResponse notGenerated() {
        return new AnalysisReportResponse(
                GenerationStatus.PENDING, null, null, List.of(), null);
    }

    public record ItemMessage(
            Long worksheetItemId,
            String observation,
            String learningPoint,
            String retryGuide
    ) {
    }
}
