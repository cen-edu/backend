package com.cenedu.backend.domain.analysis.dto.response;

import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;

/**
 * AI 문장 생성 요청을 접수한 결과.
 *
 * @param generationStatus 접수 직후의 생성 상태
 * @param retryAfterMs     다음 조회까지 기다릴 시간. 이 간격으로 보고서 조회를 반복하다가
 *                         상태가 READY 또는 FAILED 가 되면 멈춘다
 */
public record AnalysisReportGenerationResponse(
        GenerationStatus generationStatus,
        long retryAfterMs
) {
}
