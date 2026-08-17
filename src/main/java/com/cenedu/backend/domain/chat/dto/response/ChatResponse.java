package com.cenedu.backend.domain.chat.dto.response;

/**
 * 개념 챗봇 한 턴의 응답. <b>말풍선 하나에 필요한 것만 담는다.</b>
 *
 * <p>앵커 이름·근거·개념 목록을 내보내지 않는다(task_25 §0-2 결정 2). 프론트가 렌더링하는 것은
 * 답변 텍스트 하나이고, 중간값은 측정용이라 {@code ConceptChatResult} 안에 남는다.
 *
 * @param answer           답변 텍스트
 * @param currentConceptId <b>다음 요청에 그대로 실어 보낼 값.</b> 화면에 쓰지 않는다.
 *                         앵커를 서버에 쌓지 않고 왕복시키는 것이 되감김을 없앤 장치이며
 *                         (task_24c), 이 필드가 그 왕복의 클라이언트 쪽 절반이다.
 *                         앵커가 잡히지 않은 턴이면 {@code null} 이다
 */
public record ChatResponse(String answer, Long currentConceptId) {
}
