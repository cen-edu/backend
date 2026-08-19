package com.cenedu.backend.ai.chat.agent.loop;

import com.cenedu.backend.ai.client.LlmModelOptions;
import com.cenedu.backend.ai.client.LlmUseCase;
import com.cenedu.backend.ai.client.OpenAiProperties;
import com.cenedu.backend.ai.client.OpenAiClientConfig;
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

    /** 개념 챗봇 루프. 측정으로 맞춰 놓은 경로라 모델을 고정한다({@link LlmUseCase#CONCEPT_CHAT_LOOP}). */
    @Bean
    public OpenAiChatOptions loopChatOptions(OpenAiProperties properties) {
        return build(properties, LlmUseCase.CONCEPT_CHAT_LOOP);
    }

    /** 서술형 채점 루프. 어느 모델로 채점할지를 재는 중이라 설정으로 뺀다. */
    @Bean
    public OpenAiChatOptions essayGradingChatOptions(OpenAiProperties properties) {
        return build(properties, LlmUseCase.ESSAY_GRADING);
    }

    /**
     * 목적별 설정을 <b>세 값 한 묶음</b>으로 받아 옵션을 만든다.
     *
     * <p>{@code properties.model()} 을 직접 읽지 않는다. 그러면 목적별 분리 기구를 지나쳐 전역
     * 기본값을 그대로 물고 가는데, 도구를 싣는 경로는 모델이 바뀌면 조합 자체가 거절될 수 있다.
     *
     * <p><b>모델만 갈아끼우지 않는다.</b> Spring AI 는 런타임 옵션에 기본 옵션을 물려주지 않아,
     * 하나만 덮으면 나머지 둘이 요청에서 조용히 사라진다. {@code optionsFor} 가 빠진 값을 기본값으로
     * 채워 주므로 여기서는 그 셋을 그대로 싣기만 한다.
     */
    private OpenAiChatOptions build(OpenAiProperties properties, LlmUseCase useCase) {
        LlmModelOptions options = properties.optionsFor(useCase);
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(options.model())
                .maxCompletionTokens(Math.toIntExact(options.maxCompletionTokens()))
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries());
        if (OpenAiClientConfig.supportsReasoningEffort(options.model())) {
            builder.reasoningEffort(options.reasoningEffort());
        }
        return builder.build();
    }

    /**
     * 루프가 쓰는 모델 빈. <b>옵션 빈이 둘이라도 모델 빈은 하나면 된다</b> — 두 루프 모두 호출마다
     * {@code Prompt} 에 자기 옵션을 실어 보내고, 그 옵션이 모델의 기본 옵션을 이긴다. 여기 실린
     * 옵션은 아무도 쓰지 않지만 빌더가 요구해서 넘긴다.
     *
     * <p>옵션을 빈으로 따로 내는 이유: 루프가 호출마다 도구 목록만 갈아끼운 사본을 만들어야 한다.
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
