package com.cenedu.backend.domain.analysis.reissue;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.LearningState;
import com.cenedu.backend.domain.analysis.entity.LearningStatus;

/**
 * 한 개념·단계에서 관찰된 것과, 그것이 세 칸에 몇 문항으로 번역되는지.
 *
 * <p>기존 {@code ReissueService.mode()} 는 셋 중 하나를 골랐다. 화면은 개념마다 세 칸을 함께
 * 요구하므로, 같은 판단을 배타적 모드가 아니라 <b>칸별 문항 수</b>로 편다. 규칙은 그대로다.
 *
 * @param dwell          이전 문제지에서 머물던 난이도. 승급 전 값이다.
 * @param nextDifficulty 유사 문항을 고를 난이도. 승급·강등이 반영된 값이다.
 * @param lostProblemIds 시스템 오류로 기록되지 않은 문항. 복습 칸의 근거이자 문항 수다.
 * @param targetStage    처음 틀린 풀이 구간. 관찰 표시 전용이고 선정에는 쓰지 않는다.
 */
public record ConceptFocus(
        String conceptId,
        String stepId,
        String bankUnit,
        LearningState state,
        QuestionDifficulty dwell,
        QuestionDifficulty nextDifficulty,
        List<String> lostProblemIds,
        List<String> wrongProblemIds,
        String evaluationArea,
        String targetStage
) {
    public ConceptFocus {
        lostProblemIds = lostProblemIds == null ? List.of() : List.copyOf(lostProblemIds);
        wrongProblemIds = wrongProblemIds == null ? List.of() : List.copyOf(wrongProblemIds);
    }

    /**
     * 이 개념에서 칸마다 몇 문항을 제안할지.
     *
     * <p>복습은 기록되지 않은 문항 수 그대로다. 그 문항을 다시 내는 것이지 새로 고르는 것이
     * 아니므로 늘리거나 줄일 근거가 없다. 보통 0이다.
     *
     * <p>유사는 오류가 관찰됐을 때만이다. {@code CLEAR}·{@code IMPROVED} 는 이 회차에 확인할
     * 오류가 없다는 뜻이라 유사 문항을 골라도 볼 것이 없다.
     *
     * <p>응용은 <b>사다리를 끝까지 올라갔을 때만</b>이다. 하 문제지를 다 맞힌 것과 상 문제지를
     * 다 맞힌 것은 다르다. 상에서 오류가 없어야 더 줄 것이 없는 상태다.
     *
     * @param setSize 유사·응용 한 세트의 문항 수. 판정 규칙(서로 다른 문항 2개)과 맞춘 값이다.
     */
    public int proposedCount(ReissueStage stage, int setSize) {
        return switch (stage) {
            case RETRACE -> lostProblemIds.size();
            case BASIC -> state != null && !noErrorLeft() ? setSize : 0;
            case INDEPENDENT -> noErrorLeft() && dwell == QuestionDifficulty.HIGH ? setSize : 0;
        };
    }

    /** 이 회차에서 확인할 오류가 남아 있지 않은지. 상태가 없으면 판단할 수 없어 거짓이다. */
    public boolean noErrorLeft() {
        return state != null && (state.status() == LearningStatus.CLEAR
                || state.status() == LearningStatus.IMPROVED);
    }
}
