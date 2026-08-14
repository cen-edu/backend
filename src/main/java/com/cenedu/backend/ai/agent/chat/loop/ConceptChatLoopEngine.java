package com.cenedu.backend.ai.agent.chat.loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.agent.chat.loop.ConceptLoopResult.InvocationOutcome;
import com.cenedu.backend.ai.agent.chat.loop.ConceptLoopResult.LoopTrace;
import com.cenedu.backend.ai.agent.chat.loop.ConceptLoopResult.ToolInvocation;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.service.ConceptTools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 개념 챗봇의 도구 루프(B 구조). 고정 2단계 파이프라인과 <b>병행</b>해서 존재한다.
 *
 * <pre>
 * 질문 → [LLM] → 도구 호출? → 실행 → 결과를 붙여 다시 [LLM] → … → 답변
 * </pre>
 *
 * <p>고정 파이프라인과의 차이는 <b>누가 순서를 정하는가</b> 하나다. 저쪽은 키워드 추출 → 조회 →
 * 생성이 코드에 박혀 있고, 여기서는 모델이 매번 다음 수를 고른다. 그래서 저쪽은 검색이 못 닿으면
 * 끝이지만, 여기서는 다른 말로 다시 찾을 수 있다. 그 재질의 능력이 이 구조의 존재 이유다.
 *
 * <p><b>도구 실행을 Spring AI 에 맡기지 않고 직접 돈다.</b> {@code ChatClient} 에 맡기면 루프가
 * 라이브러리 안에서 돌아 호출 상한·중복 차단·차수별 토큰을 넣을 자리가 없다. 이 세 가지가 이번
 * 측정의 대상이므로 루프는 우리 것이어야 한다. {@code OpenAiChatModel} 자체는 도구를 실행하지
 * 않고 정의만 요청에 실어 주므로, 실행을 여기서 하는 것이 이 모델의 정상 사용법이다.
 *
 * <p>이력을 시스템 프롬프트 뒤에 그대로 붙인다. 지시어 3턴("그건 왜 그래?")이 앵커를 잡으려면
 * 앞 대화가 컨텍스트에 있어야 한다 — 고정 파이프라인이 이 셋을 전부 놓치는 이유가 그것이다.
 */
@Component
public class ConceptChatLoopEngine {

    private static final Logger log = LoggerFactory.getLogger(ConceptChatLoopEngine.class);

    /** {@code AgentRequest.payload()} 에서 현재 소단원을 꺼낼 키. 고정 파이프라인과 같은 키다. */
    private static final String PAYLOAD_SUB_UNIT_ID = "subUnitId";

    /**
     * 턴당 도구 호출 상한. 도달하면 그 시점의 결과로 답을 만들게 한다.
     *
     * <p><b>차단된 중복도 이 수에 센다.</b> 실행만 세면 같은 호출을 반복하는 모델이 상한에 영영
     * 닿지 않아 루프가 끝나지 않는다. 상한의 목적은 비용이 아니라 종료 보장이다.
     */
    private static final int MAX_TOOL_CALLS = 4;

    /** LLM 호출 횟수 백스톱. 상한 도달 후의 강제 답변까지 세도 이 값에 닿지 않는다. */
    private static final int MAX_MODEL_CALLS = 8;

    private static final TypeReference<List<Object>> ANY_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ConceptView>> CONCEPT_LIST = new TypeReference<>() {
    };

    private static final String TOOL_GET_PREREQS = "get_prereqs";

    private final OpenAiChatModel chatModel;
    private final OpenAiChatOptions baseOptions;
    private final ObjectMapper objectMapper;
    private final List<ToolCallback> toolCallbacks;
    private final Map<String, ToolCallback> toolsByName;

    public ConceptChatLoopEngine(OpenAiChatModel loopChatModel,
                                 OpenAiChatOptions loopChatOptions,
                                 ObjectMapper objectMapper,
                                 ConceptTools conceptTools) {
        this.chatModel = loopChatModel;
        this.baseOptions = loopChatOptions;
        this.objectMapper = objectMapper;
        this.toolCallbacks = List.of(ToolCallbacks.from(conceptTools));

        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        this.toolCallbacks.forEach(callback -> byName.put(callback.getToolDefinition().name(), callback));
        this.toolsByName = Map.copyOf(byName);
    }

    /** 한 턴을 처리한다. 가드레일은 상위(디스패처 또는 측정 러너)가 이미 통과시켰다고 본다. */
    public ConceptLoopResult answer(AgentRequest request) {
        Long subUnitId = subUnitId(request.payload());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ConceptLoopPrompts.system(subUnitId)));
        for (ChatMessage past : request.history()) {
            messages.add(past.role() == ChatMessage.Role.USER
                    ? new UserMessage(past.content())
                    : new AssistantMessage(past.content()));
        }
        messages.add(new UserMessage(request.userInput()));

        List<LoopTrace> traces = new ArrayList<>();
        Set<String> attempted = new LinkedHashSet<>();
        List<String> delivered = new ArrayList<>();

        String anchorName = "";
        int toolCalls = 0;
        int blocked = 0;
        int executed = 0;
        int executedWithRows = 0;
        boolean capped = false;

        for (int callIndex = 1; callIndex <= MAX_MODEL_CALLS; callIndex++) {
            boolean toolsOffered = toolCalls < MAX_TOOL_CALLS;
            capped = capped || !toolsOffered;

            ChatResponse response = chatModel.call(new Prompt(messages, options(toolsOffered)));
            Generation generation = response.getResult();
            if (generation == null) {
                throw new IllegalStateException("모델이 응답을 하나도 돌려주지 않았다");
            }

            AssistantMessage output = generation.getOutput();
            Usage usage = response.getMetadata().getUsage();
            String finishReason = generation.getMetadata().getFinishReason();

            if (!output.hasToolCalls()) {
                traces.add(new LoopTrace(callIndex, toolsOffered, promptTokens(usage), completionTokens(usage),
                        finishReason, List.of()));
                log.info("[루프] 종료 차수={} 도구호출={} 차단={} 상한도달={}",
                        callIndex, toolCalls, blocked, capped);
                return new ConceptLoopResult(text(output), List.copyOf(traces), anchorName, List.copyOf(delivered),
                        toolCalls, blocked, capped, executed > 0 && executedWithRows == 0);
            }

            messages.add(output);
            List<ToolInvocation> invocations = new ArrayList<>();
            List<ToolResponse> responses = new ArrayList<>();

            for (AssistantMessage.ToolCall call : output.getToolCalls()) {
                toolCalls++;
                String arguments = call.arguments() == null ? "" : call.arguments();
                ToolCallback callback = toolsByName.get(call.name());

                if (callback == null) {
                    invocations.add(new ToolInvocation(call.name(), arguments, -1, InvocationOutcome.UNKNOWN_TOOL));
                    responses.add(new ToolResponse(call.id(), call.name(),
                            "그런 도구는 없다. 쓸 수 있는 것은 " + String.join(", ", toolsByName.keySet()) + " 뿐이다."));
                    continue;
                }
                if (toolCalls > MAX_TOOL_CALLS) {
                    invocations.add(new ToolInvocation(call.name(), arguments, -1, InvocationOutcome.BLOCKED_CAP));
                    responses.add(new ToolResponse(call.id(), call.name(),
                            "도구를 부를 수 있는 횟수를 다 썼다. 지금까지 받은 것만으로 답하라."));
                    continue;
                }
                if (!attempted.add(call.name() + "|" + arguments)) {
                    blocked++;
                    invocations.add(new ToolInvocation(call.name(), arguments, -1,
                            InvocationOutcome.BLOCKED_DUPLICATE));
                    responses.add(new ToolResponse(call.id(), call.name(),
                            "같은 도구를 같은 인자로 이미 불렀다. 결과는 같으므로 다시 실행하지 않았다. "
                                    + "다른 말로 찾아보거나, 지금까지 받은 것으로 답하라."));
                    continue;
                }

                String result = execute(callback, arguments);
                executed++;
                int rows = rowCount(result);
                if (rows > 0) {
                    executedWithRows++;
                }
                if (TOOL_GET_PREREQS.equals(call.name())) {
                    List<ConceptView> concepts = parseConcepts(result);
                    concepts.forEach(concept -> delivered.add(concept.name()));
                    if (anchorName.isEmpty()) {
                        anchorName = concepts.stream()
                                .filter(concept -> concept.hop() != null && concept.hop() == 0)
                                .map(ConceptView::name)
                                .findFirst()
                                .orElse("");
                    }
                }

                invocations.add(new ToolInvocation(call.name(), arguments, rows, InvocationOutcome.EXECUTED));
                responses.add(new ToolResponse(call.id(), call.name(), result));
            }

            messages.add(ToolResponseMessage.builder().responses(responses).build());
            traces.add(new LoopTrace(callIndex, toolsOffered, promptTokens(usage), completionTokens(usage),
                    finishReason, List.copyOf(invocations)));
        }

        throw new IllegalStateException("LLM 호출이 " + MAX_MODEL_CALLS + "회를 넘겼다. 루프가 끝나지 않는다");
    }

    /**
     * 상한에 닿으면 도구를 빼고 부른다.
     *
     * <p>"이제 답하라" 는 문장을 덧붙이는 대신 도구 자체를 회수하는 이유는, 지시는 모델이 무시할 수
     * 있지만 요청 본문에 없는 도구는 부를 수가 없기 때문이다. 종료가 지시가 아니라 구조로 보장된다.
     */
    private OpenAiChatOptions options(boolean withTools) {
        return withTools
                ? baseOptions.mutate().toolCallbacks(toolCallbacks).build()
                : baseOptions.mutate().toolCallbacks(List.of()).build();
    }

    /**
     * 도구가 던지면 루프를 죽이지 않고 그 사실을 모델에 돌려준다.
     *
     * <p>인자를 만든 것이 모델이라 실패도 모델이 고칠 수 있는 종류다. 여기서 예외를 올리면 그 턴이
     * 통째로 사라져 <b>측정에서 빠진다</b> — 실패 원인을 귀속시키려면 턴이 끝까지 가야 한다.
     */
    private String execute(ToolCallback callback, String arguments) {
        try {
            return callback.call(arguments);
        } catch (RuntimeException exception) {
            log.warn("[도구] 실행 실패 tool={} 이유={}",
                    callback.getToolDefinition().name(), exception.getMessage());
            return "도구 실행이 실패했다. 인자를 확인하고 다시 시도하거나 다른 방법으로 찾아라.";
        }
    }

    /** 도구 결과의 건수. 셀 수 없으면 -1 을 돌려주고 판단은 호출부에 남긴다. */
    private int rowCount(String result) {
        try {
            return objectMapper.readValue(result, ANY_LIST).size();
        } catch (JacksonException exception) {
            return -1;
        }
    }

    private List<ConceptView> parseConcepts(String result) {
        try {
            return objectMapper.readValue(result, CONCEPT_LIST);
        } catch (JacksonException exception) {
            log.warn("[루프] get_prereqs 결과를 개념으로 읽지 못했다");
            return List.of();
        }
    }

    private static String text(AssistantMessage output) {
        String text = output.getText();
        return text == null ? "" : text;
    }

    private static Integer promptTokens(Usage usage) {
        return usage == null ? null : usage.getPromptTokens();
    }

    private static Integer completionTokens(Usage usage) {
        return usage == null ? null : usage.getCompletionTokens();
    }

    private static Long subUnitId(Map<String, Object> payload) {
        Object value = payload.get(PAYLOAD_SUB_UNIT_ID);
        return value instanceof Number number ? number.longValue() : null;
    }
}
