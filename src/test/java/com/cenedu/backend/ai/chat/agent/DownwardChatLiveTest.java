package com.cenedu.backend.ai.chat.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.chat.agent.DownwardEvalQuestions.Scenario;
import com.cenedu.backend.ai.chat.agent.DownwardEvalQuestions.Turn;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link DownwardEvalQuestions} 18채점턴을 실제 파이프라인에 흘려 측정값을 파일로 뽑는다.
 *
 * <p>{@code ConceptChatLiveTest} 를 고치지 않고 별도 파일로 둔다. 두 세트는 분모가 다르고
 * 채점 축도 다르다 — 39턴은 기대 유형 6종이고 이쪽은 <b>기대 앵커 id 일치</b> 하나다.
 * 한 파일에 합치면 어느 세트의 숫자인지가 흐려진다.
 *
 * <p><b>{@code Turn.priorAnchorId()} 를 파이프라인에 넣지 않는다.</b> 그 값은 정답표를 세울 때
 * "직전 앵커에서 한 칸" 을 계산한 근거이지 입력이 아니다. 넣으면 우리가 답을 쥐여 주는 셈이라
 * 앵커를 이력에서 되찾는 능력이 측정에서 사라진다. 러너가 파이프라인에 주는 것은
 * <b>발화와 이력뿐</b>이며, 39턴 러너와 같은 방식이다.
 *
 * <p>소단원을 주지 않는다. 이 세트의 시나리오는 소단원이 아니라 개념에서 출발한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class DownwardChatLiveTest {

    private static final Path REPORT = Path.of("build", "measurements", "24_downward.tsv");

    @Autowired
    private ConceptChatEngine engine;

    @Test
    @DisplayName("하향 탐색 평가 세트 전수를 파이프라인에 흘려 측정값을 파일로 남긴다")
    void runAllDownwardQuestions() throws IOException {
        StringBuilder report = new StringBuilder(
                "scenario\tcategory\tturnId\tutterance\tscored\texpectedState\tactualState\t"
                        + "moveOutcome\texpectedAnchorId\tactualAnchorId\tactualAnchor\tverdict\t"
                        + "scoreB\tkeywords\tparse\tconceptCount\tevidenceChars\tanswerChars\tanswer\n");

        for (Scenario scenario : DownwardEvalQuestions.ALL) {
            List<ChatMessage> history = new ArrayList<>();

            for (Turn turn : scenario.turns()) {
                ConceptChatResult result = engine.answer(new AgentRequest(
                        AgentKind.SOLVE_CHAT,
                        new Actor(1L, Actor.Role.STUDENT),
                        turn.utterance(),
                        history,
                        Map.of()));

                assertThat(result.text()).isNotBlank();
                report.append(row(scenario, turn, result));

                history.add(ChatMessage.user(turn.utterance()));
                history.add(ChatMessage.assistant(result.text()));
            }
        }

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        System.out.println("[측정] 결과 파일: " + REPORT.toAbsolutePath());
    }

    private static String row(Scenario scenario, Turn turn, ConceptChatResult result) {
        ConceptContext context = result.context();
        ConceptView anchor = context.anchor();
        Long actualAnchorId = anchor == null ? null : anchor.id();

        // 채점 축 B — 실제 앵커가 기대 앵커와 같은 개념(id)이면 O. 첫 턴은 채점하지 않는다.
        boolean matched = actualAnchorId != null && actualAnchorId == turn.expectedAnchorId();

        return String.join("\t",
                scenario.id(),
                scenario.category().name(),
                turn.id(),
                turn.utterance(),
                String.valueOf(turn.scored()),
                turn.expectedState().name(),
                result.moveState().name(),
                result.moveOutcome().name(),
                String.valueOf(turn.expectedAnchorId()),
                actualAnchorId == null ? "-" : String.valueOf(actualAnchorId),
                anchor == null ? "" : anchor.name(),
                turn.verdict().name(),
                turn.scored() ? (matched ? "O" : "X") : "-",
                String.join("|", result.keywords()),
                result.keywordParse().name(),
                String.valueOf(context.concepts().size()),
                String.valueOf(evidenceChars(context)),
                String.valueOf(result.text().length()),
                oneLine(result.text())) + "\n";
    }

    /** 2차 프롬프트에 실린 근거 본문의 크기. task_18 부록 B 와 같은 셈법이라 그대로 비교된다. */
    private static int evidenceChars(ConceptContext context) {
        return context.concepts().stream()
                .map(ConceptView::description)
                .filter(Objects::nonNull)
                .mapToInt(String::length)
                .sum();
    }

    private static String oneLine(String text) {
        return text.replace("\t", " ").replace("\r", "").replace("\n", "⏎");
    }
}
