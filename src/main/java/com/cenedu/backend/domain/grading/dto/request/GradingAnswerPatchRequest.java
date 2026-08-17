package com.cenedu.backend.domain.grading.dto.request;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 답안 한 칸의 점수·판정 수정(명세 9절). <b>셋 중 하나만</b> 보낸다.
 *
 * @param rubricChecks 서술형 판정. <b>점수는 서버가 계산한다</b> — 프론트가 합산한 값을 신뢰하면
 *                     배점이 바뀔 때 두 곳이 어긋난다. 그래서 {@code finalScore}를 함께 받지 않는다
 * @param resetToAuto  교사 수정을 되돌려 자동채점값으로 복귀시킨다. 되돌린 칸은 다음 자동채점
 *                     대상에 다시 들어간다
 */
public record GradingAnswerPatchRequest(

        @Schema(description = "교사가 직접 넣는 점수")
        BigDecimal finalScore,

        @Schema(description = "서술형 채점 기준별 충족 여부")
        @Valid
        List<RubricCheck> rubricChecks,

        @Schema(description = "자동채점값으로 되돌리기")
        Boolean resetToAuto
) {

    public record RubricCheck(
            @NotNull(message = "채점 기준 ID는 필수입니다.")
            Long rubricItemId,

            @NotNull(message = "충족 여부는 필수입니다.")
            Boolean satisfied
    ) {
    }

    /** 어떤 수정인지. 셋 중 정확히 하나가 아니면 {@code null}이다. */
    public Kind kind() {
        int provided = 0;
        Kind kind = null;
        if (finalScore != null) {
            provided++;
            kind = Kind.FINAL_SCORE;
        }
        if (rubricChecks != null && !rubricChecks.isEmpty()) {
            provided++;
            kind = Kind.RUBRIC_CHECKS;
        }
        if (Boolean.TRUE.equals(resetToAuto)) {
            provided++;
            kind = Kind.RESET_TO_AUTO;
        }
        return provided == 1 ? kind : null;
    }

    public enum Kind { FINAL_SCORE, RUBRIC_CHECKS, RESET_TO_AUTO }
}
