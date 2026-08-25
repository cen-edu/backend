package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.Set;

/**
 * 수정 Session이 놓인 바깥 문맥 때문에 교체 후보에서 반드시 빼야 하는 문항을 알려준다.
 *
 * <p>지금은 학습지가 유일한 문맥이다. 학습지 문항을 다시 수정할 때 문제은행 조회가 같은
 * 학습지에 이미 들어 있는 문항을 뽑아 오면, 교체를 확정하는 순간
 * {@code uk_worksheet_item_question} 위반으로 뒤늦게 실패한다 — 교사 입장에서는 AI가 한참
 * 고민한 뒤 알 수 없는 이유로 거절하는 것처럼 보인다. 조회 단계에서 미리 빼는 편이 낫다.
 *
 * <p>problem 도메인이 worksheet 스키마를 직접 알지 않도록 port로 둔다. 구현이 없으면
 * 제외 대상도 없는 것으로 본다.
 */
public interface ReplacementExclusionPort {

    /** 이 Session의 교체 후보에서 제외할 문항 ID를 반환한다. 없으면 빈 집합이다. */
    Set<Long> excludedQuestionIds(long sessionId);
}
