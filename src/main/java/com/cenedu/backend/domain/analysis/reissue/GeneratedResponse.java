package com.cenedu.backend.domain.analysis.reissue;

import java.util.List;

import com.cenedu.backend.global.common.enums.DisplayLabels;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조정된 구성으로 실제로 나온 문항.
 *
 * <p>서비스 내부 record 를 그대로 내보내지 않는다. 그러면 내부 구조를 고칠 때 API 계약이
 * 조용히 바뀐다.
 */
@Schema(description = "생성된 문항 목록")
public record GeneratedResponse(

        String studentId,

        List<PickedResponse> questions,

        @Schema(description = "요청됐지만 생성이 미구현이라 나가지 못한 응용 문항 수")
        int pendingAppliedCount
) {

    public static GeneratedResponse from(ReissueProposalService.Generated generated) {
        return new GeneratedResponse(
                generated.studentId(),
                generated.questions().stream().map(PickedResponse::from).toList(),
                generated.pendingAppliedCount());
    }

    @Schema(description = "뽑힌 문항 하나")
    public record PickedResponse(

            @Schema(description = "RETRACE 복습 · BASIC 유사 · INDEPENDENT 응용")
            ReissueStage stage,
            String conceptId,

            @Schema(description = "복습이면 원본 문항 ID, 유사면 뱅크 문항 ID")
            String questionId,
            @Schema(description = "문항 본문. 복습은 원본을 다시 내므로 null 이다")
            String prompt,
            String unitName,
            String difficultyBand,
            String difficultyName,
            @Schema(description = "빈칸 수. 복습은 0이다")
            int blankCount,

            @Schema(description = "이 문항이 뽑힌 이유")
            String reason
    ) {
        static PickedResponse from(ReissueProposalService.Picked picked) {
            return new PickedResponse(
                    picked.stage(), picked.conceptId(), picked.questionId(),
                    picked.prompt(), picked.unitName(), picked.difficultyBand(),
                    DisplayLabels.difficulty(picked.difficultyBand()),
                    picked.blankCount(), picked.reason());
        }
    }
}
