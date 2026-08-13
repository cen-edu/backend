package com.cenedu.backend.ai.client;

import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.global.common.BusinessException;

/**
 * LLM 한 번 호출. 에이전트는 이 인터페이스만 알고 OpenAI SDK 타입은 모른다.
 *
 * <p>스트리밍을 두지 않는다. {@code AgentResponse} 가 완성 응답인 이유와 같다 —
 * 출력 가드레일이 전체를 보고 판단해야 하는데, 토큰을 흘려보내면 되돌릴 수 없다.
 *
 * <p>대화 턴 타입으로 {@code ai.agent.ChatMessage} 를 그대로 쓴다. 클라이언트 전용 타입을 따로 두면
 * 에이전트마다 변환 코드가 붙는데, 두 타입이 담을 내용이 {@code role} 과 {@code content} 로 같다.
 * {@code ai/agent} 는 이동규 소유라 고칠 수 없지만 읽어 쓰는 것은 제약이 아니다.
 * 그 타입이 바뀌면 여기가 같이 바뀐다는 것이 이 선택의 비용이다.
 */
public interface LlmClient {

    /**
     * 시스템 메시지와 대화를 보내고 완성된 답변을 받는다.
     *
     * @param systemPrompt 모델의 역할·규칙. 비우려면 {@code null}
     * @param messages     대화 턴. 마지막이 이번에 답할 사용자 발화다.
     * @throws BusinessException 호출이 실패했거나 응답 텍스트가 비어 있을 때
     */
    LlmResponse complete(String systemPrompt, List<ChatMessage> messages);
}
