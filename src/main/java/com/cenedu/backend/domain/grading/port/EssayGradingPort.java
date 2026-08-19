package com.cenedu.backend.domain.grading.port;

/**
 * 서술형 채점 조율측과 {@code ai.grading.adapter} 구현체 사이의 칸 1개 채점 경계다.
 *
 * <p>계약과 Adapter 구현 모두 배세빈이 담당한다. 시스템 트리거 호출이라 {@code AgentDispatcher}
 * 를 거치지 않는다(AGENTS.md 3절 4번) — 사용자가 입력한 프롬프트가 없어 공통 입력 가드레일이
 * 검사할 대상이 없다. 대신 학생 필기가 프롬프트에 들어가므로 인젝션 처리는 Adapter 가 직접 한다.
 *
 * <p><b>{@code RuleGrader} 를 대체하지 않는다.</b> 규칙 채점 5종은 결정론 전용으로 남고, 이 경계는
 * {@code RUBRIC} 분기에서만 갈라져 나간다.
 */
public interface EssayGradingPort {

    /** 학생 필기 이미지 1장을 채점 기준 항목별로 판정한다. 점수는 내지 않는다. */
    EssayGradingResult grade(EssayGradingCommand command);
}
