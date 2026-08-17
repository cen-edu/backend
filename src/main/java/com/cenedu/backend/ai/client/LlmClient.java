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
    default LlmResponse complete(String systemPrompt, List<ChatMessage> messages) {
        return complete(systemPrompt, messages, null);
    }

    /**
     * 같은 입력에 같은 출력을 받고 싶을 때 쓴다. 도구처럼 쓰이는 호출(키워드 추출 등)이 대상이다.
     *
     * <p><b>gpt-5 계열에서 흔들림을 줄일 수단은 이것뿐이다.</b> {@code temperature=0} 과
     * {@code top_p} 는 모델이 400 으로 거부한다("Only the default (1) value is supported").
     * 실측: 같은 프롬프트로 3회씩 불러 seed 를 준 쪽은 9/9 동일, 안 준 쪽은 7/9 였다.
     *
     * <p>다만 <b>보장이 아니라 best-effort</b> 다. 응답에 {@code system_fingerprint} 가 오지 않아
     * 백엔드가 바뀌어 결정성이 깨졌는지 확인할 방법도 없다. 측정의 재현성을 높이는 장치로만 보고,
     * 같은 값이 온다는 전제를 코드에 심지 않는다.
     *
     * @param seed 고정할 시드. {@code null} 이면 지정하지 않는다(기존 동작)
     */
    LlmResponse complete(String systemPrompt, List<ChatMessage> messages, Long seed);
}
