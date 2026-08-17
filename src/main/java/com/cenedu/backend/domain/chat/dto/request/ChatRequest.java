package com.cenedu.backend.domain.chat.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 개념 챗봇 한 턴의 요청.
 *
 * <p><b>이 계약이 이 작업의 본체다.</b> 파이프라인이 무상태라 대화의 연속성은 전적으로
 * 클라이언트가 되돌려주는 두 값에 달려 있다 — {@link #history()} 와 {@link #currentConceptId()}.
 * 둘 중 하나만 빠져도 기능이 조용히 죽는다(이력이 빠지면 하향 탐색 전체, 앵커가 빠지면 되감김).
 *
 * @param question         학생 질문
 * @param history          이전 대화. 없으면 빈 배열로 취급한다. 서버가 개수 상한을 건다
 * @param currentConceptId <b>직전 응답이 준 값을 그대로 되돌려준다.</b> 화면에 그릴 정보가 아니라
 *                         연속성 토큰이다. 없는 id 를 보내도 오류가 아니라 없는 것으로 친다
 * @param subUnitId        학생이 보고 있는 소단원. 선택이며, 없으면 소단원 개념 목록 주입을 건너뛴다
 */
public record ChatRequest(
        @NotBlank(message = "question은 필수입니다.")
        String question,

        @Valid
        List<ChatHistoryMessage> history,

        Long currentConceptId,

        Long subUnitId
) {

    public List<ChatHistoryMessage> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
