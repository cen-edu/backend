package com.cenedu.backend.domain.analysis.dto.response;

import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

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

        @Schema(description = "생성 상태. 만든 적이 없으면 PENDING 이며 문장은 모두 null 이다")
        GenerationStatus generationStatus,

        @Schema(description = "학생 상세 상단 분석 요약")
        String summaryMessage,

        @Schema(description = "맞춤 학습 결과 해석. 백엔드 규칙으로 만들며 저장하지 않는다. 현재는 항상 null")
        String customLearningMessage,

        @Schema(description = "문항별 문장. 채점 완료 문항만 담겨 학습지 문항 수보다 적을 수 있다")
        List<ItemMessage> itemMessages,

        @Schema(description = "학생 상세 하단 종합 관찰. 미응답 문항도 여기서 다룬다")
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

            @Schema(description = "문항 결과와 연결하는 키")
            Long worksheetItemId,

            @Schema(description = "화면의 확인된 점", example = "이항까지는 맞았으나 부호를 바꾸지 않고 옮겼습니다.")
            String observation,

            @Schema(description = "화면의 학습 포인트", example = "이항할 때 부호 바꾸기")
            String learningPoint,

            @Schema(description = "화면의 다시 풀 때",
                    example = "각 줄에서 무엇을 옮겼는지 말하게 하며 다시 풀게 해 주세요.")
            String retryGuide
    ) {
    }
}
