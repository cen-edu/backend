package com.cenedu.backend.domain.analysis.report;

import java.util.List;

/**
 * 생성된 AI 문장 묶음. 아직 검증 전이라 그대로 저장하지 않는다.
 *
 * @param summaryMessage     학생 상세 상단 분석 요약
 * @param overallObservation 하단 종합 관찰. 미응답 문항 패턴도 여기서 서술한다
 * @param itemMessages       문항별 문장. 채점 완료 문항만 와야 한다
 * @param promptVersion      문장을 만든 프롬프트 버전
 * @param modelName          실제로 호출한 모델
 * @param llmSchemaVersion   응답이 따른 출력 계약 버전
 */
public record AnalysisReportDraft(
        String summaryMessage,
        String overallObservation,
        List<ItemMessageDraft> itemMessages,
        String promptVersion,
        String modelName,
        Short llmSchemaVersion
) {
    public AnalysisReportDraft {
        itemMessages = List.copyOf(itemMessages);
    }

    public record ItemMessageDraft(
            Long worksheetItemId,
            String observation,
            String learningPoint,
            String retryGuide
    ) {
    }
}
