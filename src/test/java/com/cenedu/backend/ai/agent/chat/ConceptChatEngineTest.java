package com.cenedu.backend.ai.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.agent.chat.ConceptChatResult.KeywordParse;
import com.cenedu.backend.ai.agent.chat.prompt.ConceptChatPrompts;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.entity.enums.GradeBand;
import com.cenedu.backend.domain.chat.service.ConceptQueryService;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LLM 을 부르지 않고 파이프라인의 분기를 고정한다. 실제 호출은 {@code ConceptChatLiveTest} 가 한다.
 *
 * <p>가장 중요한 것은 "근거가 없으면 두 번째 LLM 을 부르지 않는다" 다. 이 규칙이 무너지면 모델이
 * 일반 지식으로 답하고, 그 답은 교육과정 데이터에 근거하지 않았는데도 그렇게 보인다.
 */
class ConceptChatEngineTest {

    private static final Long SUB_UNIT_ID = 42L;

    private RecordingLlmClient llmClient;
    private ConceptQueryService conceptQueryService;
    private ConceptChatEngine engine;

    @BeforeEach
    void setUp() {
        llmClient = new RecordingLlmClient();
        conceptQueryService = mock(ConceptQueryService.class);
        engine = new ConceptChatEngine(llmClient, conceptQueryService, new ObjectMapper());
    }

    @Test
    @DisplayName("JSON 배열 응답에서 키워드를 순서대로 뽑는다")
    void parsesJsonArray() {
        llmClient.enqueue("[\"맞꼭지각\",\"각의 크기\"]", "답변");
        givenContext(contextWithAnchor());

        ConceptChatResult result = engine.answer(request("맞꼭지각이 왜 같아요?", Map.of()));

        assertThat(result.keywords()).containsExactly("맞꼭지각", "각의 크기");
        assertThat(result.keywordParse()).isEqualTo(KeywordParse.PARSED);
    }

    @Test
    @DisplayName("코드펜스가 붙어 오면 벗겨내고 파싱한다")
    void stripsCodeFence() {
        llmClient.enqueue("```json\n[\"소인수분해\",\"약수\"]\n```", "답변");
        givenContext(contextWithAnchor());

        ConceptChatResult result = engine.answer(request("소인수분해가 뭐예요?", Map.of()));

        assertThat(result.keywords()).containsExactly("소인수분해", "약수");
        assertThat(result.keywordParse()).isEqualTo(KeywordParse.PARSED_AFTER_FENCE_STRIP);
    }

    @Test
    @DisplayName("JSON 이 아닌 응답이면 예외 없이 빈 키워드로 진행한다")
    void continuesWhenParsingFails() {
        llmClient.enqueue("키워드는 소인수분해입니다.", "답변");
        givenContext(contextWithAnchor());

        ConceptChatResult result = engine.answer(request("소인수분해가 뭐예요?", Map.of()));

        assertThat(result.keywords()).isEmpty();
        assertThat(result.keywordParse()).isEqualTo(KeywordParse.FAILED);
        assertThat(result.text()).isEqualTo("답변");
    }

    @Test
    @DisplayName("빈 배열이면 키워드 없이 진행한다")
    void continuesWhenKeywordsAreEmpty() {
        llmClient.enqueue("[]", "답변");
        givenContext(contextWithAnchor());

        ConceptChatResult result = engine.answer(request("오늘 급식 뭐예요?", Map.of()));

        assertThat(result.keywords()).isEmpty();
        assertThat(result.keywordParse()).isEqualTo(KeywordParse.PARSED);
        verify(conceptQueryService).buildContext(isNull(), eq(List.of()));
    }

    @Test
    @DisplayName("근거가 전무하면 두 번째 LLM 을 부르지 않고 고정 문구를 돌려준다")
    void doesNotCallLlmWhenContextIsEmpty() {
        llmClient.enqueue("[\"약분\",\"통분\"]");
        givenContext(ConceptContext.noEvidence());

        ConceptChatResult result = engine.answer(request("약분이랑 통분 알려주세요", Map.of()));

        assertThat(llmClient.systemPrompts).hasSize(1);
        assertThat(llmClient.systemPrompts.get(0)).isEqualTo(ConceptChatPrompts.KEYWORD_EXTRACTION);
        assertThat(result.text()).isEqualTo(ConceptChatPrompts.NO_EVIDENCE_ANSWER);
        assertThat(result.generation()).isNull();
    }

    @Test
    @DisplayName("앵커가 있으면 두 번째 LLM 을 한 번 부르고 컨텍스트에 앵커 이름을 싣는다")
    void callsLlmWithAnchorContext() {
        llmClient.enqueue("[\"맞꼭지각\"]", "맞꼭지각은 마주 보는 각이에요.");
        givenContext(contextWithAnchor());

        ConceptChatResult result = engine.answer(
                request("맞꼭지각이 뭐야?", Map.of("subUnitId", SUB_UNIT_ID)));

        assertThat(llmClient.systemPrompts).hasSize(2);
        assertThat(llmClient.systemPrompts.get(1))
                .contains("[앵커 개념]")
                .contains("맞꼭지각")
                .contains("[선수 개념]")
                .contains("각의 크기");
        assertThat(result.text()).isEqualTo("맞꼭지각은 마주 보는 각이에요.");
        assertThat(llmClient.messages.get(1)).hasSize(1);
    }

    @Test
    @DisplayName("payload 에 subUnitId 가 없어도 예외 없이 진행한다")
    void worksWithoutSubUnitId() {
        llmClient.enqueue("[\"절댓값\"]", "답변");
        givenContext(contextWithAnchor());

        ConceptChatResult result = engine.answer(request("절댓값이 뭐예요?", Map.of()));

        assertThat(result.text()).isEqualTo("답변");
        verify(conceptQueryService).buildContext(isNull(), eq(List.of("절댓값")));
    }

    @Test
    @DisplayName("컨텍스트의 설명에 리터럴 개행 문자가 남지 않는다")
    void contextKeepsRealNewlines() {
        llmClient.enqueue("[\"맞꼭지각\"]", "답변");
        givenContext(ConceptContext.of(
                view("맞꼭지각", "두 직선이 만날 때\n마주 보는 두 각.", 0),
                List.of(view("맞꼭지각", "두 직선이 만날 때\n마주 보는 두 각.", 0)),
                List.of("맞꼭지각", "교각")));

        engine.answer(request("맞꼭지각이 뭐야?", Map.of("subUnitId", SUB_UNIT_ID)));

        String contextPrompt = llmClient.systemPrompts.get(1);
        assertThat(contextPrompt).doesNotContain("\\n");
        assertThat(contextPrompt).contains("두 직선이 만날 때\n마주 보는 두 각.");
    }

    @Test
    @DisplayName("히스토리는 두 번째 호출에만 넘긴다 — 키워드 추출은 현재 질문만 본다")
    void sendsHistoryOnlyToGeneration() {
        llmClient.enqueue("[\"맞꼭지각\"]", "답변");
        givenContext(contextWithAnchor());

        engine.answer(new AgentRequest(
                AgentKind.SOLVE_CHAT,
                new Actor(1L, Actor.Role.STUDENT),
                "그럼 그건 왜 그래요?",
                List.of(ChatMessage.user("맞꼭지각이 뭐야?"), ChatMessage.assistant("마주 보는 각이에요.")),
                Map.of()));

        assertThat(llmClient.messages.get(0)).hasSize(1);
        assertThat(llmClient.messages.get(1)).hasSize(3);
    }

    private void givenContext(ConceptContext context) {
        when(conceptQueryService.buildContext(any(), anyList())).thenReturn(context);
    }

    private static ConceptContext contextWithAnchor() {
        ConceptView anchor = view("맞꼭지각", "두 직선이 만날 때 마주 보는 두 각.", 0);
        return ConceptContext.of(
                anchor,
                List.of(anchor, view("각의 크기", "각을 이루는 두 반직선의 벌어진 정도.", 1)),
                List.of("맞꼭지각", "교각"));
    }

    private static ConceptView view(String name, String description, int hop) {
        return new ConceptView(1L + hop, name, description, GradeBand.MIDDLE_1, "1학년 1학기", hop);
    }

    private static AgentRequest request(String userInput, Map<String, Object> payload) {
        return AgentRequest.of(AgentKind.SOLVE_CHAT, new Actor(1L, Actor.Role.STUDENT), userInput, payload);
    }

    /** 큐에 넣어 둔 텍스트를 순서대로 돌려주고, 받은 프롬프트와 메시지를 기록한다. */
    private static final class RecordingLlmClient implements LlmClient {

        private final Deque<String> texts = new ArrayDeque<>();
        private final List<String> systemPrompts = new ArrayList<>();
        private final List<List<ChatMessage>> messages = new ArrayList<>();

        void enqueue(String... responses) {
            texts.addAll(List.of(responses));
        }

        @Override
        public LlmResponse complete(String systemPrompt, List<ChatMessage> chatMessages) {
            systemPrompts.add(systemPrompt);
            messages.add(List.copyOf(chatMessages));
            String text = texts.poll();
            if (text == null) {
                throw new IllegalStateException("예상하지 않은 LLM 호출입니다. 호출 횟수를 확인하세요.");
            }
            return new LlmResponse(text, 0, 0, 0);
        }
    }
}
