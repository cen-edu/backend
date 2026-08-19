package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채점 화면의 답안 칸 하나. <b>교사에게만 나가는 필드가 여럿이다</b>(명세 6절) —
 * {@code correctAnswer} · {@code autoScore} · {@code compareMethod} · {@code failureReason} ·
 * {@code rubric[].evidence}.
 *
 * @param handwritingUrl 만료 10분이다(문항 이미지 1시간과 다르다). S3가 꺼진 환경에서는 {@code null}
 * @param autoScore      자동채점 원값. 최초 기록 후 불변이라 교사 수정 전후 비교에 쓴다
 */
public record GradingAnswerUnitResponse(
        Long submissionAnswerId,
        Long answerUnitId,
        int displayOrder,
        String correctAnswer,
        String studentAnswer,

        @Schema(description = "정답 보기 ID. 객관식이 아니거나 정답을 풀지 못하면 null")
        Long correctChoiceId,

        @Schema(description = "학생이 고른 보기 ID. 객관식이 아니거나 미제출이면 null")
        Long selectedChoiceId,

        String handwritingUrl,

        @Schema(allowableValues = {"CHOICE", "VALUE", "EXACT", "SET", "SUBST", "RUBRIC"})
        String compareMethod,

        @Schema(allowableValues = {"NOT_GRADED", "GRADED", "FAILED"})
        String gradingStatus,

        @Schema(allowableValues = {"auto", "teacher"})
        String gradedBy,

        BigDecimal autoScore,
        BigDecimal finalScore,
        String failureReason,

        @Schema(description = "서술형만 값이 있다. 판정 전에도 기준 목록은 내려간다 — "
                + "각 항목의 satisfied 가 null 이면 아직 판정하지 않은 것이다")
        List<RubricItem> rubric
) {

    /**
     * 서술형 채점 기준 항목의 판정.
     *
     * <p>{@code satisfied}는 <b>3상태</b>다. {@code null}이면 아직 판정하지 않은 것이고,
     * {@code false}는 보고서 미충족으로 판정한 것이다. 이 둘을 합치면 교사 화면에서
     * "채점 안 된 답안"이 "전부 틀린 답안"으로 보인다.
     *
     * @param evidence LLM 판정 근거. <b>task_06b 전까지는 항상 {@code null}</b> — 교사가 손으로
     *                 체크한 판정에는 근거 문자열이 없다(명세 6절)
     */
    public record RubricItem(
            Long rubricItemId,
            String description,
            BigDecimal weight,

            @Schema(description = "null 이면 미판정, true/false 는 판정 결과")
            Boolean satisfied,

            String evidence
    ) {
    }
}
