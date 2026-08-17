package com.cenedu.backend.domain.worksheet.dto.response;

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
 * 누락이 코드에 드러나지 않아 {@code ANSWER_REF}가 조용히 사라지는 원인이었다.
 *
 * <p>{@code segments} JSON의 빈칸은 {@code answerUnitId}가 아니라 {@code unitKey}(예: "B1")로
 * 표시된다(실측). 문항 안에서 {@code unitKey}가 유일하므로 이걸로 {@code problem_answer_unit.id}를
 * 찾아 붙인다.
 *
 * <p>{@code answerRef}는 앞선 단계 칸에 학생이 입력한 <b>현재 값</b>을 읽기 전용으로 다시 보여주는
 * 세그먼트다. 서버는 그 값을 모르고({@code answer_raw}는 정답이라 채우면 정답이 노출된다)
 * {@code answerUnitId}만 내려보낸다. 프론트가 이 ID로 자기 답안 상태를 공유한다.
 * 그래서 {@code value}는 영구히 null이다.
 */
@Slf4j
public record StudentSegmentResponse(
        @Schema(description = "세그먼트 종류", allowableValues = {"text", "blank", "answerRef"})
        String type,

        String value,
        Long answerUnitId
) {

    public static StudentSegmentResponse from(
            ProblemStepSegmentResponse segment, Map<String, Long> answerUnitIdByUnitKey
    ) {
        String type = segment.type();
        if ("TEXT".equals(type)) {
            return new StudentSegmentResponse("text", segment.value(), null);
        }
        if ("BLANK".equals(type)) {
            Long answerUnitId = answerUnitIdByUnitKey.get(segment.unitKey());
            // 채점 단위라 조용히 넘기지 않는다 — 칸이 없으면 학생은 못 쓰는데 미입력 오답이 된다.
            if (answerUnitId == null) {
                throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
            }
            return new StudentSegmentResponse("blank", null, answerUnitId);
        }
        if ("ANSWER_REF".equals(type)) {
            Long answerUnitId = answerUnitIdByUnitKey.get(segment.unitKey());
            // 채점 단위가 아니다. 세그먼트 하나 때문에 학습지 전체 조회를 막지 않고 text로 강등한다.
            if (answerUnitId == null) {
                log.warn("ANSWER_REF의 unitKey를 answerUnit으로 풀지 못해 text로 강등한다 — unitKey={}",
                        segment.unitKey());
                return new StudentSegmentResponse("text", null, null);
            }
            return new StudentSegmentResponse("answerRef", null, answerUnitId);
        }
        throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
    }
}
