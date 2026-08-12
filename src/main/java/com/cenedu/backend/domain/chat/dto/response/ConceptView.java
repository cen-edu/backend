package com.cenedu.backend.domain.chat.dto.response;

import com.cenedu.backend.domain.chat.entity.ChatConcept;
import com.cenedu.backend.domain.chat.entity.enums.GradeBand;

/**
 * 개념 한 건. HTTP 응답이 아니라 프롬프트 조립 쪽으로 넘기는 도메인 전달 데이터다.
 *
 * <p>DB 의 description 은 줄바꿈을 리터럴 두 글자 {@code \n} 으로 갖고 있다(455행 중 242행).
 * 그 치환을 여기 {@link #from} 한 곳에서만 한다. DB 원문은 원천 그대로 두고, 프롬프트로
 * 나가는 경계에서만 실제 개행으로 바꾼다는 뜻이다. 치환 지점이 여러 곳으로 흩어지면
 * 어디서 한 번 더 바뀌었는지 추적할 수 없게 된다.
 *
 * @param hop 앵커에서 선수 방향으로 몇 걸음인지. 앵커 자신은 0, 확장 결과가 아니면 null
 */
public record ConceptView(
        Long id,
        String name,
        String description,
        GradeBand gradeBand,
        String sourceSemester,
        Integer hop
) {

    /** DB 의 리터럴 줄바꿈 표기. 원천 문자열이 이 두 글자를 그대로 담고 있다. */
    private static final String LITERAL_NEWLINE = "\\n";

    /** 확장 결과가 아닌 단건 조회용. hop 은 null 이다. */
    public static ConceptView from(ChatConcept concept) {
        return from(concept, null);
    }

    /** 확장 결과용. hop 0 은 앵커 자신이다. */
    public static ConceptView from(ChatConcept concept, Integer hop) {
        return new ConceptView(
                concept.getId(),
                concept.getName(),
                toPromptText(concept.getDescription()),
                concept.getGradeBand(),
                concept.getSourceSemester(),
                hop
        );
    }

    /** 리터럴 {@code \n} 두 글자를 실제 개행으로 바꾼다. */
    private static String toPromptText(String description) {
        return description == null ? null : description.replace(LITERAL_NEWLINE, "\n");
    }
}
