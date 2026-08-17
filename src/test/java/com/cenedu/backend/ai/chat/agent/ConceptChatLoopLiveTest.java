package com.cenedu.backend.ai.chat.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.chat.agent.ConceptChatLiveTest.Scenario;
import com.cenedu.backend.ai.chat.agent.ConceptChatLiveTest.Turn;
import com.cenedu.backend.ai.chat.agent.loop.ConceptChatLoopEngine;
import com.cenedu.backend.ai.chat.agent.loop.ConceptLoopResult;
import com.cenedu.backend.ai.chat.agent.loop.ConceptLoopResult.LoopTrace;
import com.cenedu.backend.ai.chat.agent.loop.ConceptLoopResult.ToolInvocation;
import com.cenedu.backend.ai.guard.GuardDecision;
import com.cenedu.backend.ai.guard.input.InputGuard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 도구 루프를 같은 39턴에 흘려 고정 파이프라인과 나란히 놓을 측정값을 뽑는다.
 *
 * <p><b>{@link ConceptChatLiveTest} 를 고치지 않고 새로 만들었다.</b> 저쪽은 고정 파이프라인의
 * 기준선을 만든 러너라, 손대면 task_13 이 만든 36/39 가 무엇을 재던 값인지 알 수 없게 된다.
 * 평가 세트({@link EvalQuestions#ALL})와 기대 유형·기대 앵커는 저쪽 것을 그대로 읽는다.
 *
 * <p><b>{@code reached} 의 정의를 저쪽과 같은 엄격도로 맞췄다.</b> 고정 파이프라인은 검색 1위를
 * 앵커로 삼고 그 이름이 기대 앵커와 같은지만 봤다. 루프에는 "검색 1위" 가 없으므로, 모델이 처음
 * {@code get_prereqs} 로 펼친 개념(hop 0)을 앵커로 본다. 둘 다 "모델이 설명하기로 고른 개념"
 * 이라는 점에서 같은 자리다. 본문이 전달된 개념 전체는 따로 기록해 느슨한 해석도 가능하게 둔다.
 *
 * <p>결과 파일은 두 개다. 턴 단위 표와 LLM 호출 단위 로그(부록용)를 한 파일에 담으면 둘 다 읽기
 * 어려워진다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ConceptChatLoopLiveTest {

    private static final Path TURN_REPORT = Path.of("build", "measurements", "15_loop_turns.tsv");
    private static final Path CALL_REPORT = Path.of("build", "measurements", "15_loop_calls.tsv");

    @Autowired
    private ConceptChatLoopEngine engine;
    @Autowired
    private List<InputGuard> inputGuards;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("EVAL_QUESTIONS 전수를 도구 루프에 흘려 측정값을 파일로 남긴다")
    void runAllEvalQuestionsThroughLoop() throws IOException {
        StringBuilder turns = new StringBuilder(
                "category\tid\tsubUnit\tquestion\toutcome\texpectedAnchor\tactualAnchor\treached\t"
                        + "deliveredConcepts\tmodelCalls\ttoolCalls\tblocked\tcapped\tnoEvidence\t"
                        + "promptTokens\tcompletionTokens\tlastFinishReason\ttoolSequence\tanswer\n");
        StringBuilder calls = new StringBuilder(
                "id\tcallIndex\ttoolsOffered\tpromptTokens\tcompletionTokens\tfinishReason\t"
                        + "toolName\targuments\tresultCount\toutcome\n");

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
                    turns.append(blockedRow(scenario, turn, blocked));
                    continue;
                }

                ConceptLoopResult result = runWithOneRetry(request);
                if (result == null) {
                    turns.append(unrunnableRow(scenario, turn));
                    continue;
                }

                turns.append(turnRow(scenario, turn, result));
                calls.append(callRows(turn, result));

                history.add(ChatMessage.user(turn.question()));
                history.add(ChatMessage.assistant(result.text()));
            }
        }

        Files.createDirectories(TURN_REPORT.getParent());
        Files.writeString(TURN_REPORT, turns.toString(), StandardCharsets.UTF_8);
        Files.writeString(CALL_REPORT, calls.toString(), StandardCharsets.UTF_8);
        System.out.println("[측정] 턴 표: " + TURN_REPORT.toAbsolutePath());
        System.out.println("[측정] 호출 로그: " + CALL_REPORT.toAbsolutePath());
    }

    /**
     * 실패한 턴은 한 번만 다시 부른다. 그래도 실패하면 그 턴을 "실행 불가" 로 남기고 계속한다.
     *
     * <p>한 턴의 API 오류로 39턴이 통째로 날아가면 재실행 비용이 그만큼 다시 든다. 실행 불가 자체가
     * 기록해야 할 관측값이다.
     */
    private ConceptLoopResult runWithOneRetry(AgentRequest request) {
        try {
            return engine.answer(request);
        } catch (RuntimeException first) {
            System.out.println("[측정] 1차 실패, 재시도: " + first.getMessage());
            try {
                return engine.answer(request);
            } catch (RuntimeException second) {
                System.out.println("[측정] 재시도도 실패: " + second.getMessage());
                return null;
            }
        }
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
                turn.outcome().name(), String.join("|", turn.expectedAnchors()), "-", "-",
                "-", "0", "0", "0", "-", "-", "-", "-", "GUARD_BLOCKED",
                "-", oneLine(decision.reasonCode() + " / " + decision.message())) + "\n";
    }

    private String unrunnableRow(Scenario scenario, Turn turn) {
        return String.join("\t",
                scenario.category(), turn.id(), scenario.subUnitKey(), turn.question(),
                turn.outcome().name(), String.join("|", turn.expectedAnchors()), "-", "-",
                "-", "-", "-", "-", "-", "-", "-", "-", "실행불가", "-", "실행불가") + "\n";
    }

    private String turnRow(Scenario scenario, Turn turn, ConceptLoopResult result) {
        boolean reached = !turn.expectedAnchors().isEmpty()
                && turn.expectedAnchors().contains(result.anchorName());
        List<LoopTrace> traces = result.traces();
        String lastFinish = traces.isEmpty() ? "-" : traces.get(traces.size() - 1).finishReason();

        return String.join("\t",
                scenario.category(),
                turn.id(),
                scenario.subUnitKey(),
                turn.question(),
                turn.outcome().name(),
                String.join("|", turn.expectedAnchors()),
                result.anchorName(),
                turn.expectedAnchors().isEmpty() ? "-" : (reached ? "O" : "X"),
                String.join("|", result.deliveredConcepts()),
                String.valueOf(traces.size()),
                String.valueOf(result.toolCallCount()),
                String.valueOf(result.blockedCount()),
                String.valueOf(result.cappedOut()),
                String.valueOf(result.noEvidence()),
                String.valueOf(result.promptTokens()),
                String.valueOf(result.completionTokens()),
                lastFinish == null ? "-" : lastFinish,
                toolSequence(result),
                oneLine(result.text())) + "\n";
    }

    /** 도구 호출을 한 칸에 담는다. {@code 이름(인자)->건수} 를 부른 순서대로 이었다. */
    private static String toolSequence(ConceptLoopResult result) {
        List<String> steps = new ArrayList<>();
        for (LoopTrace trace : result.traces()) {
            for (ToolInvocation invocation : trace.invocations()) {
                steps.add("%s(%s)->%s%s".formatted(
                        invocation.name(),
                        oneLine(invocation.arguments()),
                        invocation.resultCount() < 0 ? "?" : String.valueOf(invocation.resultCount()),
                        invocation.outcome() == ConceptLoopResult.InvocationOutcome.EXECUTED
                                ? "" : "[" + invocation.outcome().name() + "]"));
            }
        }
        return steps.isEmpty() ? "-" : String.join(" > ", steps);
    }

    private static String callRows(Turn turn, ConceptLoopResult result) {
        StringBuilder rows = new StringBuilder();
        for (LoopTrace trace : result.traces()) {
            if (trace.invocations().isEmpty()) {
                rows.append(callRow(turn, trace, null));
                continue;
            }
            for (ToolInvocation invocation : trace.invocations()) {
                rows.append(callRow(turn, trace, invocation));
            }
        }
        return rows.toString();
    }

    private static String callRow(Turn turn, LoopTrace trace, ToolInvocation invocation) {
        return String.join("\t",
                turn.id(),
                String.valueOf(trace.callIndex()),
                String.valueOf(trace.toolsOffered()),
                String.valueOf(trace.promptTokens()),
                String.valueOf(trace.completionTokens()),
                trace.finishReason() == null ? "-" : trace.finishReason(),
                invocation == null ? "-" : invocation.name(),
                invocation == null ? "-" : oneLine(invocation.arguments()),
                invocation == null ? "-" : (invocation.resultCount() < 0 ? "?" : String.valueOf(invocation.resultCount())),
                invocation == null ? "답변" : invocation.outcome().name()) + "\n";
    }

    private Long subUnitId(String externalKey) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM curriculum_unit WHERE external_key = ?", Long.class, externalKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace("\t", " ").replace("\r", "").replace("\n", "⏎");
    }
}
