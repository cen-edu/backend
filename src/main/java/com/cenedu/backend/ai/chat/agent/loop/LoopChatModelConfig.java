package com.cenedu.backend.ai.chat.agent.loop;

import com.cenedu.backend.ai.client.OpenAiProperties;
import com.openai.client.OpenAIClient;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도구 루프가 쓰는 Spring AI 모델 빈.
 *
 * <p>스타터({@code spring-ai-starter-model-openai})를 쓰지 않는다. 자동 설정이 붙으면 키가 없는
 * 팀원의 기동이 자동 설정의 사정에 따라 달라지는데, 이 프로젝트는 "키가 없어도 뜨고 호출 시점에
 * 실패한다" 를 이미 정해 뒀다({@code OpenAiClientConfig}). 그 규칙을 자동 설정에 맡기지 않는다.
 *
 * <p><b>{@code ai/client} 가 만든 {@link OpenAIClient} 를 그대로 넘긴다.</b> Spring AI 2.0 의
 * OpenAI 모듈은 우리가 이미 쓰던 {@code com.openai} SDK 를 감싸는 구조라, 커넥션 풀과 타임아웃·
 * 재시도 설정을 두 벌로 두지 않아도 된다. 고정 파이프라인과 루프가 같은 HTTP 스택을 쓴다.
 *
 * <p>{@code options} 에 키·타임아웃을 다시 적는 이유는 빌더가 비동기 클라이언트를 따로 만들기
 * 때문이다. 우리는 동기 호출만 쓰지만, 만들어지는 쪽도 같은 설정을 보게 해 둔다.
 *
 * <p><b>{@code temperature} 를 지정하지 않는다.</b> gpt-5-mini 는 1 이 아닌 값을 400 으로 거절한다
 * (task_10 에서 확인). Spring AI 는 값이 null 이면 요청에 싣지 않으므로 그대로 둔다.
 */
@Configuration
public class LoopChatModelConfig {

    /**
     * 모델 파라미터는 고정 파이프라인과 같은 값을 쓴다. 이번 측정의 대상은 구조지 모델 설정이
     * 아니므로, {@code reasoning_effort=minimal} 이 도구 선택에 통하는지까지 그대로 관찰한다.
     */
    @Bean
    public OpenAiChatOptions loopChatOptions(OpenAiProperties properties) {
        return OpenAiChatOptions.builder()
                .model(properties.model())
                .reasoningEffort(properties.reasoningEffort())
                .maxCompletionTokens(Math.toIntExact(properties.maxCompletionTokens()))
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .build();
    }

    /**
     * 옵션을 빈으로 따로 내는 이유: 루프가 호출마다 도구 목록만 갈아끼운 사본을 만들어야 한다.
     * 모델의 기본 옵션을 읽는 접근자는 제거 예정으로 표시돼 있어 쓰지 않는다.
     */
    @Bean
    public OpenAiChatModel loopChatModel(OpenAIClient openAIClient, OpenAiChatOptions loopChatOptions) {
        return OpenAiChatModel.builder()
                .openAiClient(openAIClient)
                .options(loopChatOptions)
                .build();
    }
}
