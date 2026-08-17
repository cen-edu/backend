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
 * <p><b>앵커를 턴 사이에 왕복시킨다(task_24b §0-1).</b> 러너가 <b>직전 턴이 실제로 반환한
 * 앵커 id</b> 를 다음 턴 payload 로 되돌려준다. 이게 없으면 파이프라인이 매 턴 키워드로 앵커를
 * 새로 찾는데, 이력에는 시나리오 첫 개념이 가장 진하게 남아 있어 <b>되감김</b>이 일어난다
 * (task_24 에서 {@code DA1} 이 13 → 3 → 다시 13). 컨트롤러가 하게 될 일을 러너가 대신하는
 * 것이며, 배선 쪽은 {@code SolveChatAgent} 가 {@code AgentResponse.data} 로 같은 값을 낸다.
 *
 * <p><b>{@code Turn.priorAnchorId()} 는 여전히 넣지 않는다.</b> 그 값은 정답표를 세울 때
 * "직전 앵커에서 한 칸" 을 계산한 근거이지 입력이 아니다. 넣으면 되감김이 사라진 것처럼 보이지만
 * 실제로는 답을 쥐여 준 것이고, 컨트롤러가 붙는 순간 다시 무너진다. 러너가 되돌려주는 것은
 * <b>정답표 값이 아니라 직전 실행이 낸 값</b>이다.
 *
 * <p>소단원을 주지 않는다. 이 세트의 시나리오는 소단원이 아니라 개념에서 출발한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class DownwardChatLiveTest {

    private static final Path REPORT = Path.of("build", "measurements", "24b_downward.tsv");

    @Autowired
    private ConceptChatEngine engine;

    @Test
    @DisplayName("하향 탐색 평가 세트 전수를 파이프라인에 흘려 측정값을 파일로 남긴다")
    void runAllDownwardQuestions() throws IOException {
        StringBuilder report = new StringBuilder(
                "scenario\tcategory\tturnId\tutterance\tscored\texpectedState\tactualState\t"
                        + "moveOutcome\tcarriedAnchorId\texpectedAnchorId\tactualAnchorId\tactualAnchor\tverdict\t"
                        + "scoreB\tkeywords\tparse\tconceptCount\tevidenceChars\tanswerChars\tanswer\n");

        for (Scenario scenario : DownwardEvalQuestions.ALL) {
            List<ChatMessage> history = new ArrayList<>();
            Long carried = null;

            for (Turn turn : scenario.turns()) {
                ConceptChatResult result = engine.answer(new AgentRequest(
                        AgentKind.SOLVE_CHAT,
                        new Actor(1L, Actor.Role.STUDENT),
                        turn.utterance(),
                        history,
                        carried == null ? Map.of()
                                : Map.of(ConceptChatEngine.PAYLOAD_CURRENT_CONCEPT_ID, carried)));

                assertThat(result.text()).isNotBlank();
                report.append(row(scenario, turn, result, carried));

                carried = carriedAfter(result, carried);
                history.add(ChatMessage.user(turn.utterance()));
                history.add(ChatMessage.assistant(result.text()));
            }
        }

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        System.out.println("[측정] 결과 파일: " + REPORT.toAbsolutePath());
    }

    /**
     * 다음 턴에 되돌려줄 앵커. <b>이번 턴이 실제로 낸 값만 쓴다.</b>
     *
     * <p>앵커가 없는 턴(근거 0건)에서는 직전 값을 그대로 들고 간다 — 컨트롤러가 붙어도 같다.
     * {@code AgentResponse.data} 에 {@code currentConceptId} 가 실리지 않은 응답을 받은 클라이언트는
     * 자기가 갖고 있던 값을 버릴 이유가 없다. 여기서만 버리면 측정과 배선이 갈린다.
     */
    private static Long carriedAfter(ConceptChatResult result, Long previous) {
        ConceptView anchor = result.context().anchor();
        return anchor == null ? previous : anchor.id();
    }

    private static String row(Scenario scenario, Turn turn, ConceptChatResult result, Long carried) {
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
                carried == null ? "-" : String.valueOf(carried),
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
