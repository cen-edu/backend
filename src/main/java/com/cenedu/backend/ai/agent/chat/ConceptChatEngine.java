package com.cenedu.backend.ai.agent.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.agent.chat.ConceptChatResult.KeywordParse;
import com.cenedu.backend.ai.agent.chat.prompt.ConceptChatPrompts;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.service.ConceptQueryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 개념 챗봇의 고정 2단계 파이프라인.
 *
 * <pre>
 * 질문 → [LLM 1] 키워드 추출 → ConceptQueryService 조회 → (근거 없으면 종료) → [LLM 2] 답변
 * </pre>
 *
 * <p>루프가 아니다. 에이전트가 도구를 스스로 고르는 구조는 다음 단계이며, 지금 고정 순서로 두는
 * 이유는 실패 지점을 분리하기 위해서다. 키워드 추출이 나쁜지, 검색이 못 닿는지, 생성이 나쁜지를
 * 각각 볼 수 있어야 루프를 얹을 근거가 생긴다.
 *
 * <p>{@code Agent} 구현이 아니라 별도 {@code @Component} 인 것은 AGENTS.md 5절이 지정한 방식이다 —
 * 여러 에이전트가 같은 로직을 쓰면 빼서 주입받는다. {@code REVIEW_CHAT} 이 나중에 이 엔진을 쓴다.
 *
 * <p>{@code AgentDispatcher} 를 주입받지 않는다. 디스패처가 에이전트를 주입받으므로 순환이 되어
 * 기동이 실패한다. LLM 을 직접 부르는 것은 위반이 아니다 — 금지된 것은 {@code dispatch()} 재호출이다.
 */
@Component
public class ConceptChatEngine {

    private static final Logger log = LoggerFactory.getLogger(ConceptChatEngine.class);

    /** {@code AgentRequest.payload()} 에서 현재 소단원을 꺼낼 키. 없으면 소단원 목록 없이 진행한다. */
    private static final String PAYLOAD_SUB_UNIT_ID = "subUnitId";

    private static final TypeReference<List<String>> KEYWORD_LIST = new TypeReference<>() {
    };

    /**
     * 키워드 추출에만 고정 시드를 준다. 이 호출은 자연어 답변이 아니라 <b>도구 호출</b>이라,
     * 같은 질문에 같은 키워드가 나오는 편이 낫다. 답변 생성에는 주지 않는다.
     *
     * <p>측정에서 흔들림이 컸던 것이 계기다 — 같은 39턴을 두 번 돌렸더니 16턴의 키워드가 달랐고
     * 그중 5턴은 도달 여부까지 뒤집혔다. 값 자체에 의미는 없고, 고정되어 있다는 것만 중요하다.
     */
    private static final long KEYWORD_SEED = 7L;

    private final LlmClient llmClient;
    private final ConceptQueryService conceptQueryService;
    private final ObjectMapper objectMapper;

    public ConceptChatEngine(LlmClient llmClient, ConceptQueryService conceptQueryService,
                             ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.conceptQueryService = conceptQueryService;
        this.objectMapper = objectMapper;
    }

    public ConceptChatResult answer(AgentRequest request) {
        ConceptChatResult.KeywordParse parse;
        List<String> keywords;

        LlmResponse extraction = llmClient.complete(
                ConceptChatPrompts.KEYWORD_EXTRACTION,
                // 히스토리를 넣지 않는다. 직전 질문의 키워드가 섞이면 이번 질문의 추출이 흐려진다.
                List.of(ChatMessage.user(request.userInput())),
                KEYWORD_SEED);
        Parsed parsed = parseKeywords(extraction.text());
        keywords = parsed.keywords();
        parse = parsed.parse();

        ConceptContext context = conceptQueryService.buildContext(
                subUnitId(request.payload()), keywords);

        // 질문·컨텍스트·답변 본문은 남기지 않는다. 학생 입력과 시험 문항이 로그로 나가면
        // 정답 유출 정책이 무너진다. 개수와 길이만으로도 대부분의 추적은 된다.
        log.info("개념 챗봇 조회 — keywordCount={}, keywordParse={}, anchorFound={}, conceptCount={}, "
                        + "subUnitConceptCount={}, empty={}",
                keywords.size(), parse, context.anchor() != null, context.concepts().size(),
                context.subUnitConceptNames().size(), context.empty());

        if (context.empty()) {
            // 근거 없이 부르면 모델이 일반 지식으로 답한다. 그건 "교육과정 데이터에 근거한 답변"이라는
            // 이 설계의 전제를 깨고, 지어낸 답을 학생이 교과 내용으로 받아들이게 만든다.
            return ConceptChatResult.noEvidence(
                    ConceptChatPrompts.NO_EVIDENCE_ANSWER, keywords, parse, context);
        }

        String systemPrompt = ConceptChatPrompts.answerSystemPrompt(context);
        List<ChatMessage> messages = new ArrayList<>(request.history());
        messages.add(ChatMessage.user(request.userInput()));

        LlmResponse generation = llmClient.complete(systemPrompt, messages);
        log.info("개념 챗봇 생성 — contextLength={}, historySize={}, reasoningTokens={}",
                systemPrompt.length(), request.history().size(), generation.reasoningTokens());

        return new ConceptChatResult(generation.text(), keywords, parse, context, generation);
    }

    /**
     * 1차 응답을 JSON 배열로 읽는다. 코드펜스가 붙어 오면 한 번은 관대하게 벗겨내고 다시 읽는다.
     *
     * <p>모델이 형식을 어기는 것은 흔한 일이라 한 번의 관용은 값을 하지만, 그 횟수는
     * {@link KeywordParse} 로 남긴다. 조용히 봐주면 프롬프트가 나빠져도 알 수 없다.
     *
     * <p>읽지 못하면 예외를 던지지 않고 빈 키워드로 진행한다. 소단원 개념 목록만으로도 답할 수
     * 있고, 키워드 추출 실패가 대화 자체를 끊을 이유는 없다.
     */
    private Parsed parseKeywords(String rawText) {
        String raw = rawText.trim();
        List<String> keywords = readArray(raw);
        if (keywords != null) {
            return new Parsed(keywords, KeywordParse.PARSED);
        }

        String stripped = stripCodeFence(raw);
        if (!stripped.equals(raw)) {
            keywords = readArray(stripped);
            if (keywords != null) {
                return new Parsed(keywords, KeywordParse.PARSED_AFTER_FENCE_STRIP);
            }
        }

        log.warn("키워드 추출 응답을 JSON 배열로 읽지 못했다 — length={}", raw.length());
        return new Parsed(List.of(), KeywordParse.FAILED);
    }

    /**
     * 읽지 못하면 {@code null}. 실패를 예외로 올리지 않으려고 null 로 신호한다.
     *
     * <p>Jackson 3 은 파싱 예외가 unchecked 라 잡지 않으면 그대로 올라간다. 모델 출력은 언제든
     * 형식을 어길 수 있으니 여기서 삼킨다.
     */
    private List<String> readArray(String text) {
        try {
            return objectMapper.readValue(text, KEYWORD_LIST);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String body = text.substring(firstNewline + 1);
        int closing = body.lastIndexOf("```");
        return (closing < 0 ? body : body.substring(0, closing)).trim();
    }

    /**
     * 소단원이 없으면 {@code null} 을 돌려 소단원 목록 없이 진행한다. 예외를 던지지 않는다 —
     * 단원 밖에서 물어보는 경우가 있다.
     *
     * <p>숫자만 받는다. payload 는 우리 컨트롤러가 채우는 맵이라 JSON 숫자로 들어온다.
     */
    private static Long subUnitId(Map<String, Object> payload) {
        return payload.get(PAYLOAD_SUB_UNIT_ID) instanceof Number number ? number.longValue() : null;
    }

    private record Parsed(List<String> keywords, KeywordParse parse) {
    }
}
