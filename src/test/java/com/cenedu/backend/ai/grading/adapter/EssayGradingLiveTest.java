package com.cenedu.backend.ai.grading.adapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.domain.grading.port.EssayGradingCommand;
import com.cenedu.backend.domain.grading.port.RubricCriterion;
import com.cenedu.backend.domain.grading.port.RubricJudgement;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2a — 골든셋 문항 하나에 <b>실제 API 호출</b>로 end-to-end 를 한 번 태운다(정지 조건 2).
 *
 * <p>확인하는 것은 <b>판정 JSON 이 파싱되는 것까지</b>다. 판정이 맞는지는 보지 않는다 — 그건
 * 사람이 만든 정답 라벨이 있어야 하는 단계 4 의 일이고, 여기서 눈으로 채점하기 시작하면 측정
 * 기준이 값이 아니라 인상이 된다.
 *
 * <p>스프링 컨텍스트를 띄워 <b>주입된 어댑터</b>를 쓴다. {@code OpenAiChatModel} 빈이 둘이라
 * 생성자 파라미터 이름이 어긋나면 기동에서 바로 드러나는데, 어댑터를 손으로 만들면 그 위험이
 * 테스트를 통과해 버린다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class EssayGradingLiveTest {

    private static final Path GOLDENSET = Path.of(
            System.getenv().getOrDefault("GOLDENSET_HOME", "../EduCenDocs/tools/goldenset/handmade"));
    private static final Path FONT = Path.of(
            System.getenv().getOrDefault("KOREAN_FONT", "/mnt/c/Windows/Fonts/malgun.ttf"));
    private static final Path REPORT = Path.of("build", "measurements", "2a_essay_grading.txt");

    /**
     * 학생 답안. 손으로 쓴 재료다.
     *
     * <p>곱셈을 {@code x} 로 썼다 — 학생 필기에서 가장 흔하고 전사가 가장 잘 깨지는 자리다(D9).
     * 여기서 도구가 {@code UNREADABLE} 을 내는지 아닌지가 단계 4 의 곱셈 기호 집계로 이어진다.
     */
    private static final List<String> ANSWER_LINES = List.of(
            "126 ÷ 2 = 63",
            "63 ÷ 3 = 21",
            "21 ÷ 3 = 7",
            "7은 소수이다",
            "따라서 126 = 2 x 3^2 x 7");

    @Autowired
    private EssayGradingAdapter adapter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("골든셋 답안 1장이 실제 호출로 돌아 판정 JSON 이 파싱된다")
    void gradesOneGoldensetAnswerEndToEnd() throws Exception {
        Path question = GOLDENSET.resolve("normal/normal-essay-001.json");
        Assumptions.assumeTrue(Files.exists(question), "골든셋이 없다: " + question.toAbsolutePath());
        Assumptions.assumeTrue(Files.exists(FONT), "한글 폰트가 없다: " + FONT);

        JsonNode root = objectMapper.readTree(Files.readString(question, StandardCharsets.UTF_8));
        List<RubricCriterion> criteria = new ArrayList<>();
        long rubricItemId = 1;
        for (JsonNode item : root.path("rubricItems")) {
            // DB 의 problem_rubric_item.id 자리다. 골든셋은 rubricKey 만 갖고 있어 순번을 쓴다.
            criteria.add(new RubricCriterion(rubricItemId++, item.path("criterion").asString("")));
        }
        assertThat(criteria).isNotEmpty();

        byte[] png = TypesetAnswerImage.renderPng(ANSWER_LINES, FONT);
        EssayGradingCommand command =
                new EssayGradingCommand(TypesetAnswerImage.toDataUri(png), criteria);

        EssayGradingRun run = adapter.run(command, true);

        writeReport(root, criteria, png.length, run);

        assertThat(run.result().isJudged())
                .as("판정 JSON 이 파싱되고 요청한 항목 전부에 판정이 붙어야 한다. status=%s",
                        run.result().status())
                .isTrue();
        assertThat(run.result().judgements()).hasSameSizeAs(criteria);
        assertThat(run.result().transcription()).isNotBlank();
    }

    /** 값만 남긴다. 해석은 보고서에서 사람이 붙인다. */
    private void writeReport(JsonNode question, List<RubricCriterion> criteria, int pngBytes,
                             EssayGradingRun run) throws Exception {
        EssayGradingRun.Trace trace = run.trace();
        StringBuilder report = new StringBuilder()
                .append("문항\tnormal-essay-001\n")
                .append("발문\t").append(question.path("contentBlocks").path(0).path("text").asString("")).append('\n')
                .append("답안줄수\t").append(ANSWER_LINES.size()).append('\n')
                .append("이미지바이트\t").append(pngBytes).append('\n')
                .append("status\t").append(run.result().status()).append('\n')
                .append("도구제공\t").append(trace.toolsOffered()).append('\n')
                .append("LLM호출차수\t").append(trace.modelCalls()).append('\n')
                .append("도구호출\t").append(trace.toolCalls()).append('\n')
                .append("도구상태분포\t").append(trace.toolStatusCounts()).append('\n')
                .append("버린판정\t").append(trace.droppedItems()).append('\n')
                .append("JSON실패\t").append(trace.malformedOutputs()).append('\n')
                .append("프롬프트토큰\t").append(trace.promptTokens()).append('\n')
                .append("완성토큰\t").append(trace.completionTokens()).append('\n')
                .append("소요ms\t").append(trace.elapsedMillis()).append('\n')
                .append("\n[transcription]\n").append(run.result().transcription()).append("\n\n[items]\n");
        for (RubricJudgement judgement : run.result().judgements()) {
            String label = criteria.stream()
                    .filter(criterion -> criterion.rubricItemId() == judgement.rubricItemId())
                    .map(RubricCriterion::label)
                    .findFirst()
                    .orElse("");
            report.append(judgement.rubricItemId()).append('\t')
                    .append(judgement.verdict()).append('\t')
                    .append(label).append('\t')
                    .append(judgement.evidence()).append('\n');
        }
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
    }
}
