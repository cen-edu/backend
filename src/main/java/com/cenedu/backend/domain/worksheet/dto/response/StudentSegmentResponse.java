package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.Map;

import com.cenedu.backend.domain.problem.dto.response.ProblemStepSegmentResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 빈칸형 풀이 단계의 세그먼트 하나. {@code type}이 {@code blank}면 채점 칸이고
 * {@code answerUnitId}로 {@code answerUnits}와 이어진다.
 *
 * <p>DB의 {@code type} 값은 대문자({@code TEXT}/{@code BLANK})지만 프론트가 소문자로 정확히 비교한다
 * (실측: {@code PracticeProblemView.jsx}). 여기서 소문자로 바꾼다.
 *
 * <p>{@code segments} JSON의 빈칸은 {@code answerUnitId}가 아니라 {@code unitKey}(예: "B1")로
 * 표시된다(실측). 문항 안에서 {@code unitKey}가 유일하므로 이걸로 {@code problem_answer_unit.id}를
 * 찾아 붙인다.
 */
public record StudentSegmentResponse(
        @Schema(description = "세그먼트 종류", allowableValues = {"text", "blank"})
        String type,

        String value,
        Long answerUnitId
) {

    public static StudentSegmentResponse from(
            ProblemStepSegmentResponse segment, Map<String, Long> answerUnitIdByUnitKey
    ) {
        String type = segment.type() == null ? null : segment.type().toLowerCase();
        if ("blank".equals(type)) {
            return new StudentSegmentResponse(type, null, answerUnitIdByUnitKey.get(segment.unitKey()));
        }
        return new StudentSegmentResponse(type, segment.value(), null);
    }
}
