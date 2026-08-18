package com.cenedu.backend.domain.submission.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 채점 칸 하나의 답안. {@code normalized}·{@code answerImageRef}·{@code compareMethod}·점수 계열은
 * 받지 않는다 — 서버가 결정하거나 조립한다(명세 6절).
 */
public record StudentAnswerUnitSaveRequest(
        @NotNull(message = "answerUnitId는 필수입니다.")
        Long answerUnitId,

        @Schema(description = "고른 보기 ID. 객관식이 아니면 null")
        Long selectedChoiceId,

        @Schema(description = """
                주관식 답의 LaTeX 표기. 필기 인식 결과를 프론트가 넣는다 — 서버는 이미지에서 값을
                뽑지 않는다(필기 이미지는 별도 업로드 엔드포인트이며 채점에 쓰이지 않는다).
                객관식·서술형이면 null.

                유니코드 수학기호(½ ≤ √ ×)가 아니라 LaTeX 명령으로 보낼 것. 서버는 형식을 검증하지
                않으므로 잘못 보내도 저장은 성공하고, 채점 시점에 그 칸만 FAILED 로 떨어진다.
                숫자·문자 하나뿐인 답은 LaTeX 표기가 평문과 같다(4 는 그냥 "4").
                """,
                example = "\\frac{40}{3}")
        String rawLatex,

        @Schema(description = """
                이 칸의 필기 이미지를 업로드했는지. true 면 서버가 답안 이미지 경로를 기록해
                교사 채점 화면에서 원본 필기를 볼 수 있게 한다. 채점 판정에는 쓰이지 않는다.
                """)
        @NotNull(message = "hasHandwriting은 필수입니다.")
        Boolean hasHandwriting
) {
}
