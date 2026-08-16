package com.cenedu.backend.ai.chat.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.chat.agent.ConceptChatResult.KeywordParse;
import com.cenedu.backend.ai.chat.agent.ConceptChatResult.MoveOutcome;
import com.cenedu.backend.ai.chat.agent.ConceptChatResult.MoveState;
import com.cenedu.backend.ai.chat.agent.prompt.ConceptChatPrompts;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptLanding;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.service.ConceptQueryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
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

    /**
     * 키워드 추출에만 고정 시드를 준다. 이 호출은 자연어 답변이 아니라 <b>도구 호출</b>이라,
     * 같은 질문에 같은 키워드가 나오는 편이 낫다. 답변 생성에는 주지 않는다.
     *
     * <p>측정에서 흔들림이 컸던 것이 계기다 — 같은 39턴을 두 번 돌렸더니 16턴의 키워드가 달랐고
     * 그중 5턴은 도달 여부까지 뒤집혔다. 값 자체에 의미는 없고, 고정되어 있다는 것만 중요하다.
     */
    private static final long KEYWORD_SEED = 7L;

    /**
     * 추출 호출에 실을 이력의 최대 메시지 수 (user/assistant 합산, 뒤에서부터).
     *
     * <p>현재 평가 세트의 최대 이력은 4개라 이 상한은 걸리지 않는다. 그래도 두는 이유는 컨트롤러가
     * 생겼을 때 이력이 무한히 늘어나는 것을 막기 위해서다. 지금 값을 재는 데는 영향이 없다.
     */
    private static final int EXTRACTION_HISTORY_MAX = 6;

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
        Long subUnitId = subUnitId(request.payload());

        // 소단원 개념 이름을 추출 프롬프트에 참고로 넣는다. 실제 개념명을 보여 주면 DB 에 없는
        // 이름을 지어내는 일이 줄어든다. 그래서 이름 목록 조회가 키워드 추출보다 앞선다.
        List<String> subUnitConceptNames = conceptQueryService.findSubUnitConceptNames(subUnitId);

        LlmResponse extraction = llmClient.complete(
                ConceptChatPrompts.keywordExtraction(subUnitConceptNames),
                extractionMessages(request),
                KEYWORD_SEED);
        Parsed parsed = parseExtraction(extraction.text());
        List<String> keywords = parsed.keywords();
        MoveState state = parsed.state();
        KeywordParse parse = parsed.parse();

        // buildContext 가 이름 목록을 한 번 더 읽는다. 그 중복을 의도적으로 둔다 —
        // 없애려면 "근거가 없으면 LLM 을 부르지 않는다" 판정을 여기로 복제해야 하는데,
        // 그 불변식은 한 곳에만 있어야 한다. 조회 한 번은 3~5ms 다.
        ConceptContext context = conceptQueryService.buildContext(subUnitId, keywords);

        // 질문·컨텍스트·답변 본문은 남기지 않는다. 학생 입력과 시험 문항이 로그로 나가면
        // 정답 유출 정책이 무너진다. 개수와 길이만으로도 대부분의 추적은 된다.
        log.info("개념 챗봇 조회 — keywordCount={}, keywordParse={}, moveState={}, anchorFound={}, "
                        + "conceptCount={}, subUnitConceptCount={}, empty={}",
                keywords.size(), parse, state, context.anchor() != null, context.concepts().size(),
                context.subUnitConceptNames().size(), context.empty());

        if (context.empty()) {
            // 근거 없이 부르면 모델이 일반 지식으로 답한다. 그건 "교육과정 데이터에 근거한 답변"이라는
            // 이 설계의 전제를 깨고, 지어낸 답을 학생이 교과 내용으로 받아들이게 만든다.
            return ConceptChatResult.noEvidence(
                    ConceptChatPrompts.NO_EVIDENCE_ANSWER, keywords, parse, state, context);
        }

        Move move = moveDown(state, subUnitId, context);
        if (move.outcome() == MoveOutcome.DEAD_END) {
            return ConceptChatResult.deadEnd(
                    ConceptChatPrompts.CANNOT_MOVE_ANSWER, keywords, parse, state, context);
        }
        context = move.context();

        logEvidenceSize(context, request.history().size());

        String systemPrompt = ConceptChatPrompts.answerSystemPrompt(context);
        List<ChatMessage> messages = new ArrayList<>(request.history());
        messages.add(ChatMessage.user(request.userInput()));

        LlmResponse generation = llmClient.complete(systemPrompt, messages);
        log.info("개념 챗봇 생성 — contextLength={}, historySize={}, reasoningTokens={}",
                systemPrompt.length(), request.history().size(), generation.reasoningTokens());

        return new ConceptChatResult(
                generation.text(), keywords, parse, state, move.outcome(), context, generation);
    }

    /**
     * 상태 판정을 실제 이동으로 옮긴다. 이동이 없으면 받은 근거를 그대로 돌려준다.
     *
     * <p><b>착지 실패는 한 칸으로 폴백한다.</b> 초등까지 가는 경로가 없는 중1 개념 10개는 1칸은
     * 갈 곳이 있다. 학생이 요청한 방향으로 한 칸이라도 가는 편이 아무것도 안 하는 것보다 낫고,
     * 물러섬 문구는 정말 갈 곳이 없는 19개에만 쓴다.
     *
     * <p>앵커가 없으면(소단원 이름 목록만 있는 근거) 이동하지 않는다. 내려갈 출발점이 없다.
     */
    private Move moveDown(MoveState state, Long subUnitId, ConceptContext context) {
        if (state == MoveState.NONE || context.anchor() == null) {
            return new Move(MoveOutcome.NOT_REQUESTED, context);
        }

        Long anchorId = context.anchor().id();
        Optional<ConceptLanding> landing = state == MoveState.MUCH_EASIER
                ? conceptQueryService.findNearestElementary(anchorId)
                : Optional.empty();

        Long destinationId;
        MoveOutcome outcome;
        if (landing.isPresent()) {
            destinationId = landing.get().concept().id();
            outcome = MoveOutcome.LANDING;
        } else {
            Optional<ConceptView> stepDown = conceptQueryService.findNextStepDown(anchorId);
            if (stepDown.isEmpty()) {
                log.info("개념 챗봇 이동 — state={}, outcome=DEAD_END, anchorId={}", state, anchorId);
                return new Move(MoveOutcome.DEAD_END, context);
            }
            destinationId = stepDown.get().id();
            outcome = state == MoveState.MUCH_EASIER
                    ? MoveOutcome.FALLBACK_STEP_DOWN
                    : MoveOutcome.STEP_DOWN;
        }

        log.info("개념 챗봇 이동 — state={}, outcome={}, anchorId={}, destinationId={}",
                state, outcome, anchorId, destinationId);
        return new Move(outcome, conceptQueryService.buildContextAt(subUnitId, destinationId));
    }

    /**
     * 추출 호출에 넣을 메시지. 이력을 앞에 두고 이번 질문을 마지막에 붙인다.
     *
     * <p>이력을 프롬프트 문자열로 직렬화하지 않고 <b>메시지 리스트로</b> 넣는다. 문자열로 만들면
     * "이전 대화:" 같은 머리말이 생기고, 그 머리말은 이력이 비었을 때도 남아 단발성 질문 35턴의
     * 입력까지 바꾼다. 리스트로 넣으면 <b>이력이 비었을 때 결과가 기존과 정확히 같아진다</b>.
     *
     * <p>지시어 턴("그럼 그건 왜 그래요?")에는 뽑을 명사가 없어 이력 없이는 키워드가 빈 배열이 된다.
     * 반대로 이력이 섞여 이번 질문의 추출이 흐려질 수 있다는 우려도 있었는데, 그 우려가 맞는지는
     * 이력이 실리는 턴에서만 관측된다.
     */
    private List<ChatMessage> extractionMessages(AgentRequest request) {
        List<ChatMessage> history = request.history();
        int from = Math.max(0, history.size() - EXTRACTION_HISTORY_MAX);

        List<ChatMessage> messages = new ArrayList<>(history.subList(from, history.size()));
        messages.add(ChatMessage.user(request.userInput()));
        return messages;
    }

    /**
     * 2차 생성 호출에 실린 근거의 크기. 앵커·선수 개념의 본문과 소단원 이름 목록을 <b>따로</b> 센다.
     *
     * <p>둘은 성격이 다르다. 본문은 설명의 재료이고 이름 목록은 안내의 재료다. 합쳐 버리면
     * "근거가 얇아서 물러선 답" 과 "이름만 있어서 물러선 답" 이 같은 숫자로 보인다.
     */
    private static void logEvidenceSize(ConceptContext context, int historySize) {
        int descriptionChars = context.concepts().stream()
                .map(ConceptView::description)
                .filter(Objects::nonNull)
                .mapToInt(String::length)
                .sum();
        int subUnitNameChars = context.subUnitConceptNames().stream()
                .mapToInt(String::length)
                .sum();

        log.info("개념 챗봇 근거 — historySize={}, conceptCount={}, descriptionChars={}, "
                        + "subUnitNameCount={}, subUnitNameChars={}",
                historySize, context.concepts().size(), descriptionChars,
                context.subUnitConceptNames().size(), subUnitNameChars);
    }

    /**
     * 1차 응답을 JSON 객체로 읽는다. 코드펜스가 붙어 오면 한 번은 관대하게 벗겨내고 다시 읽는다.
     *
     * <p>모델이 형식을 어기는 것은 흔한 일이라 한 번의 관용은 값을 하지만, 그 횟수는
     * {@link KeywordParse} 로 남긴다. 조용히 봐주면 프롬프트가 나빠져도 알 수 없다.
     *
     * <p>읽지 못하면 예외를 던지지 않고 빈 키워드로 진행한다. 소단원 개념 목록만으로도 답할 수
     * 있고, 키워드 추출 실패가 대화 자체를 끊을 이유는 없다. 그때 상태는 {@code NONE} 이다 —
     * <b>읽지 못한 응답을 근거로 앵커를 옮기면 안 된다.</b>
     */
    private Parsed parseExtraction(String rawText) {
        String raw = rawText.trim();
        Parsed parsed = readExtraction(raw, KeywordParse.PARSED);
        if (parsed != null) {
            return parsed;
        }

        String stripped = stripCodeFence(raw);
        if (!stripped.equals(raw)) {
            parsed = readExtraction(stripped, KeywordParse.PARSED_AFTER_FENCE_STRIP);
            if (parsed != null) {
                return parsed;
            }
        }

        log.warn("키워드 추출 응답을 JSON 객체로 읽지 못했다 — length={}", raw.length());
        return new Parsed(List.of(), MoveState.NONE, KeywordParse.FAILED);
    }

    /**
     * 읽지 못하면 {@code null}. 실패를 예외로 올리지 않으려고 null 로 신호한다.
     *
     * <p>Jackson 3 은 파싱 예외가 unchecked 라 잡지 않으면 그대로 올라간다. 모델 출력은 언제든
     * 형식을 어길 수 있으니 여기서 삼킨다.
     *
     * <p>모르는 {@code state} 값은 {@code NONE} 으로 받는다. 형식은 맞는데 값만 어긋난 경우까지
     * 통째로 실패시키면 멀쩡한 키워드를 버리게 된다.
     */
    private Parsed readExtraction(String text, KeywordParse parse) {
        try {
            Extraction extraction = objectMapper.readValue(text, Extraction.class);
            return new Parsed(
                    extraction.keywords() == null ? List.of() : extraction.keywords(),
                    moveState(extraction.state()),
                    parse);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static MoveState moveState(String raw) {
        if (raw == null) {
            return MoveState.NONE;
        }
        try {
            return MoveState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 상태 판정값이라 NONE 으로 읽는다 — state={}", raw);
            return MoveState.NONE;
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

    private record Parsed(List<String> keywords, MoveState state, KeywordParse parse) {
    }

    /** 1차 응답의 JSON 형태. {@code state} 는 문자열로 받아 {@link #moveState} 가 해석한다. */
    private record Extraction(List<String> keywords, String state) {
    }

    /** 이동 결과와 그 결과로 쓸 근거. {@code DEAD_END} 면 근거는 이동 전 그대로다. */
    private record Move(MoveOutcome outcome, ConceptContext context) {
    }
}
