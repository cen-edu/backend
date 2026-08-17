package com.cenedu.backend.ai.chat.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.OpenAiLlmClient;
import com.cenedu.backend.ai.guard.GuardDecision;
import com.cenedu.backend.ai.guard.input.InputGuard;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code EVAL_QUESTIONS.md} 전수를 실제 파이프라인에 흘려 측정값을 파일로 뽑는다.
 *
 * <p>테스트라기보다 <b>측정 러너</b>다. 단언은 "예외 없이 끝나고 답이 비지 않는다" 정도만 두고,
 * 판정은 사람이 결과 파일을 읽고 한다. 답변 품질에 점수를 매기는 것은 이 코드의 몫이 아니다.
 *
 * <p>{@code OPENAI_API_KEY} 와 개념이 적재된 DB 가 둘 다 있어야 돈다. CI 에서는 키가 없어 건너뛴다.
 *
 * <p>디스패처 대신 입력 가드를 직접 돌리고 엔진을 부른다. 디스패처를 쓰면 {@link ConceptChatResult}
 * 의 중간값이 버려져 실패 지점을 귀속시킬 수 없고, 그렇다고 둘 다 부르면 LLM 호출이 두 배가 된다.
 * 가드 목록은 디스패처가 쓰는 것과 같은 빈이다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ConceptChatLiveTest {

    private static final Path REPORT = Path.of("build", "measurements", "08_concept_chat.tsv");
    private static final Pattern FINISH_REASON = Pattern.compile("finishReason=(\\w+)");

    @Autowired
    private ConceptChatEngine engine;
    @Autowired
    private List<InputGuard> inputGuards;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static ListAppender<ILoggingEvent> llmLogs;

    /**
     * finish_reason 은 {@code LlmResponse} 에 없고 래퍼 로그에만 있다. {@code ai/client} 를 이번에
     * 고치지 않기로 했으므로 측정에서는 로그를 읽는다.
     *
     * <p>{@code @BeforeAll} 이 아니라 여기서 붙이는 이유: 스프링 컨텍스트는 테스트 인스턴스를 만들 때
     * 생성되고, 그때 Boot 의 로깅 초기화가 {@code LoggerContext} 를 reset 해 먼저 붙여 둔 appender 를
     * 떼어 낸다. {@code @BeforeAll} 에서 붙이면 로그가 한 건도 안 잡힌다(실제로 한 번 당했다).
     */
    @BeforeEach
    void captureLlmLogs() {
        llmLogs = new ListAppender<>();
        llmLogs.start();
        ((Logger) LoggerFactory.getLogger(OpenAiLlmClient.class)).addAppender(llmLogs);
    }

    @AfterEach
    void releaseLlmLogs() {
        ((Logger) LoggerFactory.getLogger(OpenAiLlmClient.class)).detachAppender(llmLogs);
    }

    @Test
    @DisplayName("EVAL_QUESTIONS 전수를 파이프라인에 흘려 측정값을 파일로 남긴다")
    void runAllEvalQuestions() throws IOException {
        StringBuilder report = new StringBuilder(
                "category\tid\tsubUnit\tquestion\toutcome\tkeywords\tparse\texpectedAnchor\tactualAnchor\t"
                        + "reached\tconceptCount\tsubUnitConceptCount\tcontextChars\tempty\t"
                        + "reasoningTokens\tpromptTokens\tcompletionTokens\tfinishReason\tanswer\n");

        for (Scenario scenario : EvalQuestions.ALL) {
            Long subUnitId = subUnitId(scenario.subUnitKey());
            List<ChatMessage> history = new ArrayList<>();

            for (Turn turn : scenario.turns()) {
                AgentRequest request = new AgentRequest(
                        AgentKind.SOLVE_CHAT,
                        new Actor(1L, Actor.Role.STUDENT),
                        turn.question(),
                        history,
                        subUnitId == null ? Map.of() : Map.of("subUnitId", subUnitId));

                GuardDecision blocked = firstBlock(request);
                if (blocked != null) {
                    report.append(blockedRow(scenario, turn, blocked));
                    continue;
                }

                llmLogs.list.clear();
                ConceptChatResult result = engine.answer(request);

                assertThat(result.text()).isNotBlank();
                report.append(row(scenario, turn, result));

                history.add(ChatMessage.user(turn.question()));
                history.add(ChatMessage.assistant(result.text()));
            }
        }

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        System.out.println("[측정] 결과 파일: " + REPORT.toAbsolutePath());
    }

    private GuardDecision firstBlock(AgentRequest request) {
        return inputGuards.stream()
                .map(guard -> guard.inspect(request))
                .filter(GuardDecision::blocked)
                .findFirst()
                .orElse(null);
    }

    private String blockedRow(Scenario scenario, Turn turn, GuardDecision decision) {
        return String.join("\t",
                scenario.category(), turn.id(), scenario.subUnitKey(), turn.question(),
                turn.outcome().name(), "-", "GUARD_BLOCKED", String.join("|", turn.expectedAnchors()), "-",
                "-", "0", "0", "0", "-", "-", "-", "-", "-",
                oneLine(decision.reasonCode() + " / " + decision.message())) + "\n";
    }

    private String row(Scenario scenario, Turn turn, ConceptChatResult result) {
        ConceptContext context = result.context();
        String actualAnchor = context.anchor() == null ? "" : context.anchor().name();
        boolean reached = !turn.expectedAnchors().isEmpty()
                && turn.expectedAnchors().contains(actualAnchor);

        return String.join("\t",
                scenario.category(),
                turn.id(),
                scenario.subUnitKey(),
                turn.question(),
                turn.outcome().name(),
                String.join("|", result.keywords()),
                result.keywordParse().name(),
                String.join("|", turn.expectedAnchors()),
                actualAnchor,
                turn.expectedAnchors().isEmpty() ? "-" : (reached ? "O" : "X"),
                String.valueOf(context.concepts().size()),
                String.valueOf(context.subUnitConceptNames().size()),
                String.valueOf(contextChars(context)),
                String.valueOf(context.empty()),
                result.generation() == null ? "-" : String.valueOf(result.generation().reasoningTokens()),
                result.generation() == null ? "-" : String.valueOf(result.generation().promptTokens()),
                result.generation() == null ? "-" : String.valueOf(result.generation().completionTokens()),
                result.generation() == null ? "-" : lastFinishReason(),
                oneLine(result.text())) + "\n";
    }

    /** 2차 시스템 프롬프트에 실린 근거의 크기. 프롬프트 지시문을 뺀 순수 근거만 센다. */
    private static int contextChars(ConceptContext context) {
        if (context.empty()) {
            return 0;
        }
        int chars = context.concepts().stream()
                .mapToInt(concept -> concept.name().length()
                        + (concept.description() == null ? 0 : concept.description().length()))
                .sum();
        return chars + String.join(", ", context.subUnitConceptNames()).length();
    }

    /** 마지막 LLM 호출 로그에서 finish_reason 을 읽는다. 2차 호출이 마지막이다. */
    private static String lastFinishReason() {
        List<ILoggingEvent> events = List.copyOf(llmLogs.list);
        for (int i = events.size() - 1; i >= 0; i--) {
            Matcher matcher = FINISH_REASON.matcher(events.get(i).getFormattedMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "?";
    }

    private Long subUnitId(String externalKey) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM curriculum_unit WHERE external_key = ?", Long.class, externalKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String oneLine(String text) {
        return text.replace("\t", " ").replace("\r", "").replace("\n", "⏎");
    }

    /**
     * 이 질문이 무엇을 하면 통과인가. EVAL_QUESTIONS v2 에서 도입했다.
     *
     * <p>v1 은 "기대 앵커에 닿았는가" 하나로만 채점했는데, 그 자로는 옳게 물러선 답과 실패를
     * 구분하지 못한다. 특히 {@link #DATA_GAP} 을 {@link #UNKNOWN} 과 나눈 것이 중요하다 —
     * 합치면 우리 개념 데이터의 결손이 정답표에 흡수되어 지표에서 사라진다.
     */
    enum ExpectedOutcome {
        /** 앵커를 잡고 자료 기반으로 설명. 통과 = 기대 앵커 도달 + 자료 밖 내용 없음. */
        EXPLAIN,
        /** 답을 주면 안 되는 요청. 통과 = 답을 주지 않음. */
        REFUSE,
        /** 중1 교육과정 밖. 통과 = <b>범위 밖임을 알림</b>("자료에 없다" 만으로는 부족). */
        OUT_OF_SCOPE,
        /** 이 단원에서 다룰 내용이 아니거나 애매. 통과 = 모른다고 말하고 지어내지 않음. */
        UNKNOWN,
        /** 교육과정에는 있으나 우리 455개 개념에 없음. 통과 조건은 UNKNOWN 과 같고 지표만 갈린다. */
        DATA_GAP,
        /** 근거가 전무해 LLM 을 부르지 않아야 하는 경로. 통과 = LLM 미호출 + 고정 문구. */
        EMPTY_CONTEXT,
    }

    record Turn(String id, String question, ExpectedOutcome outcome, List<String> expectedAnchors) {
    }

    record Scenario(String category, String subUnitKey, List<Turn> turns) {
    }
}
