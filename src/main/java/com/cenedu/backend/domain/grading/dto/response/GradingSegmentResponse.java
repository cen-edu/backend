package com.cenedu.backend.domain.grading.dto.response;

import java.util.Map;

import com.cenedu.backend.domain.problem.dto.response.ProblemStepSegmentResponse;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;

/**
 * 빈칸형 풀이 단계의 세그먼트 하나. {@code type}이 {@code blank}면 채점 칸이고
 * {@code answerUnitId}로 {@code answerUnits}와 이어진다.
 *
 * <p>DB 타입({@code TEXT}/{@code BLANK}/{@code ANSWER_REF})을 프론트 토큰으로 <b>명시적으로 열거해</b>
 * 변환한다. {@code toLowerCase()} 같은 파생 규칙을 쓰지 않는다 — 파생은 네 번째 타입이 생겨도
 * 누락이 코드에 드러나지 않는다.
 *
 * <p>{@code value}는 {@code text} 세그먼트에만 있다. {@code blank}·{@code answerRef}의 학생 답은
 * {@code answerUnits[]}가 가지고 있고, 교사 화면은 {@code answerUnitId}로 그 칸을 찾아 그린다.
 *
 * <p>학생 API의 {@code StudentSegmentResponse}와 변환 규칙이 같지만 재사용하지 않는다(명세 6절) —
 * 규칙을 바꿀 일이 생기면 학생 화면과 교사 화면 중 어느 쪽 요구인지 분리해서 판단해야 한다.
 */
@Slf4j
public record GradingSegmentResponse(
        @Schema(description = "세그먼트 종류", allowableValues = {"text", "blank", "answerRef"})
        String type,

        String value,
        Long answerUnitId
) {

    public static GradingSegmentResponse from(
            ProblemStepSegmentResponse segment, Map<String, Long> answerUnitIdByUnitKey
    ) {
        String type = segment.type();
        if ("TEXT".equals(type)) {
            return new GradingSegmentResponse("text", segment.value(), null);
        }
        if ("BLANK".equals(type)) {
            Long answerUnitId = answerUnitIdByUnitKey.get(segment.unitKey());
            // 채점 단위라 조용히 넘기지 않는다 — 칸이 없으면 교사가 그 칸을 채점할 수 없다.
            if (answerUnitId == null) {
                throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
            }
            return new GradingSegmentResponse("blank", null, answerUnitId);
        }
        if ("ANSWER_REF".equals(type)) {
            Long answerUnitId = answerUnitIdByUnitKey.get(segment.unitKey());
            // 채점 단위가 아니다. 세그먼트 하나 때문에 채점 화면 전체를 막지 않고 text로 강등한다.
            if (answerUnitId == null) {
                log.warn("ANSWER_REF의 unitKey를 answerUnit으로 풀지 못해 text로 강등한다 — unitKey={}",
                        segment.unitKey());
                return new GradingSegmentResponse("text", null, null);
            }
            return new GradingSegmentResponse("answerRef", null, answerUnitId);
        }
        throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
    }
}
