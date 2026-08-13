package com.cenedu.backend.domain.chat.dto.response;

import com.cenedu.backend.domain.chat.entity.ChatConcept;
import com.cenedu.backend.domain.chat.entity.enums.GradeBand;

/**
 * 이름 검색이 돌려주는 후보 한 건. <b>본문(description)을 싣지 않는다.</b>
 *
 * <p>검색은 "어느 개념을 앵커로 삼을지" 를 고르는 단계라 이름과 식별 정보만 있으면 된다.
 * 본문은 앵커가 정해진 뒤 {@link ConceptView} 가 실어 나른다. 여기서 본문까지 주면 상위가
 * 후보 5건의 설명을 통째로 프롬프트에 넣는 길이 열린다.
 *
 * <p>{@code gradeBand} 를 함께 주는 이유: 이름이 같은 개념이 중1과 초등에 나뉘어 있는 경우가
 * 있다(예: {@code 공약수}). 이름만으로는 구분되지 않아 호출부가 어느 쪽인지 알 수 없다.
 * <b>다만 순위 계산에는 쓰지 않는다</b> — 현재 동명 후보는 {@code id} 순으로만 갈린다.
 *
 * @param subUnitId 중1 개념은 소단원에 매달리고 초등 개념은 매달리지 않아 {@code null} 이다
 */
public record ConceptCandidate(Long id, String name, GradeBand gradeBand, Long subUnitId) {

    public static ConceptCandidate from(ChatConcept concept) {
        return new ConceptCandidate(
                concept.getId(), concept.getName(), concept.getGradeBand(), concept.getSubUnitId());
    }
}
