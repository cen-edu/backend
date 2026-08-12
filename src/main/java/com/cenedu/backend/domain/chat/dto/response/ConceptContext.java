package com.cenedu.backend.domain.chat.dto.response;

import java.util.List;

/**
 * 한 번의 개념 조회로 모은 근거 묶음. 프롬프트 조립의 재료다.
 *
 * @param anchor              앵커 개념. 검색이 실패했으면 null
 * @param concepts            앵커와 그 선수 개념. hop 오름차순이며 첫 원소가 앵커다
 * @param subUnitConceptNames 현재 소단원에 속한 개념 이름 목록. 본문 없이 이름만 담는다
 * @param empty               근거가 전무한지 여부. true 면 LLM 을 호출하지 않는다
 */
public record ConceptContext(
        ConceptView anchor,
        List<ConceptView> concepts,
        List<String> subUnitConceptNames,
        boolean empty
) {

    /**
     * 앵커와 소단원 개념이 모두 없는 경우. 상위가 LLM 호출을 건너뛰는 판단에 쓴다.
     *
     * <p>이름을 {@code empty()} 로 두면 레코드 컴포넌트 {@code empty} 의 접근자와 충돌해
     * 컴파일되지 않는다.
     */
    public static ConceptContext noEvidence() {
        return new ConceptContext(null, List.of(), List.of(), true);
    }

    /** 근거가 하나라도 있는 경우. 앵커가 없어도 소단원 이름 목록만으로 답할 수 있다. */
    public static ConceptContext of(ConceptView anchor, List<ConceptView> concepts,
                                    List<String> subUnitConceptNames) {
        return new ConceptContext(anchor, concepts, subUnitConceptNames, false);
    }
}
