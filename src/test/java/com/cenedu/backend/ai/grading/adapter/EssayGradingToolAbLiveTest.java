package com.cenedu.backend.ai.grading.adapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
 * 단계 4 — 도구가 값을 하는가. <b>A 군(도구 루프) 대 B 군(도구 없음)</b>을 같은 이미지·같은
 * 프롬프트로 돌리고 값만 남긴다.
 *
 * <p><b>해석하지 않는다.</b> 표를 뽑아 파일로 내리는 데까지가 이 테스트의 일이다. 정확도가 몇
 * 퍼센트 높다는 것과 도구가 값을 한다는 것은 다른 문장이고, 그 사이를 잇는 것은 사람이 한다.
 *
 * <p><b>B 군에 별도 어댑터를 만들지 않는다.</b> {@code adapter.run(command, false, seed)} 가
 * 도구 목록만 비운 사본으로 같은 코드를 탄다. 두 군이 다른 코드를 타면 무엇이 차이를 만들었는지
 * 알 수 없다(금지 16 — 한 번에 두 변수를 바꾸지 않는다).
 *
 * <p><b>활자 이미지로 대체 실행하지 않는다.</b> 필기가 없으면 건너뛴다 — 활자로 돌리면 전사
 * 축이 통째로 사라져서, 남는 것은 "읽기 쉬운 글을 잘 읽는가" 라는 재보지 않아도 되는 값이다.
 *
 * <p>돌리는 법:
 * <pre>
 * OPENAI_API_KEY=... ./gradlew test --tests '*EssayGradingToolAbLiveTest'
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class EssayGradingToolAbLiveTest {

    private static final Path GOLDENSET = Path.of(
            System.getenv().getOrDefault("GOLDENSET_HOME", "../EduCenDocs/tools/goldenset/handmade"));

    /** 기대 판정 라벨. <b>사람이 만든다</b> — 이 파일이 없으면 채점할 기준이 없다. */
    private static final Path LABELS = GOLDENSET.resolve("goldenset-answer-labels.json");

    private static final Path REPORT = Path.of(
            System.getenv().getOrDefault("MEASUREMENTS_HOME", "../EduCenDocs/docs/measurements"),
            "4_essay_grading_tool_ab.md");

    /**
     * 고정 seed(D18). 운영은 쓰지 않는다 — 두 군의 차이가 도구 유무에서 오는지, 그저 다시 굴린
     * 주사위에서 오는지 가르려고 측정에서만 고정한다.
     */
    private static final int SEED = 20260819;

    @Autowired
    private EssayGradingAdapter adapter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("평가 세트 전수를 A·B 두 군에 흘려 측정값을 파일로 남긴다")
    void measuresToolEffectAcrossGroups() throws Exception {
        Assumptions.assumeTrue(Files.exists(LABELS),
                "기대 판정 라벨이 없다: " + LABELS.toAbsolutePath()
                        + " — 라벨과 필기 이미지가 준비되기 전에는 돌리지 않는다");

        JsonNode labels = objectMapper.readTree(Files.readString(LABELS, StandardCharsets.UTF_8));
        List<Case> cases = readCases(labels);
        Assumptions.assumeFalse(cases.isEmpty(), "평가 세트가 비어 있다");
        assertSetMatchesDeclaration(labels, cases);

        List<Path> missing = cases.stream()
                .map(Case::answerImage)
                .filter(image -> !Files.exists(image))
                .distinct()
                .toList();
        Assumptions.assumeTrue(missing.isEmpty(),
                "필기 이미지가 없다(활자로 대체하지 않는다): " + missing);

        List<Outcome> outcomes = new ArrayList<>();
        for (Case testCase : cases) {
            // B 를 먼저 돌린다. 순서가 값을 바꾸지는 않지만 로그를 읽을 때 대조가 앞에 오는 편이 낫다.
            outcomes.add(runGroup(testCase, false));
            outcomes.add(runGroup(testCase, true));
        }

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, new ReportWriter(cases, outcomes).render(), StandardCharsets.UTF_8);
    }

    /**
     * 라벨 파일이 스스로 선언한 케이스·항목 수와 실제로 읽힌 수를 맞춘다.
     *
     * <p>세트가 조용히 줄어드는 것이 가장 나쁘다 — 문항 파일 경로 오타 하나로 케이스가 빠져도
     * 표는 멀쩡해 보이고, 정확도만 이유 없이 올라간다. 여기서 크게 실패시킨다(금지 15).
     */
    private void assertSetMatchesDeclaration(JsonNode labels, List<Case> cases) {
        int items = cases.stream().mapToInt(testCase -> testCase.expectedByRubricKey().size()).sum();
        int declaredCases = labels.path("caseCount").asInt(cases.size());
        int declaredItems = labels.path("itemCount").asInt(items);
        assertThat(cases).as("선언한 케이스 수와 읽힌 수가 다르다").hasSize(declaredCases);
        assertThat(items).as("선언한 라벨 항목 수와 읽힌 수가 다르다").isEqualTo(declaredItems);

        // 라벨이 문항의 rubricKey 를 전부 덮는지도 본다. 빠진 항목은 판정을 받고도 채점되지 않는다.
        for (Case testCase : cases) {
            assertThat(testCase.expectedByRubricKey().keySet())
                    .as("케이스 %s 의 라벨이 문항 rubricKey 를 덮지 않는다", testCase.caseId())
                    .containsExactlyInAnyOrderElementsOf(testCase.rubricKeys());
        }
    }

    private Outcome runGroup(Case testCase, boolean withTools) throws Exception {
        String dataUri = toDataUri(testCase.answerImage());
        EssayGradingCommand command = new EssayGradingCommand(dataUri, testCase.criteria());
        EssayGradingRun run = adapter.run(command, withTools, SEED);
        return new Outcome(testCase, withTools, run);
    }

    // ===== 평가 세트 읽기 =====

    /**
     * 라벨 파일이 <b>평가 세트의 정본</b>이다. 케이스 수를 코드에 적지 않는다 — 적어 두면 파일과
     * 어긋났을 때 어느 쪽이 맞는지 알 수 없다.
     *
     * <p>골든셋에는 {@code problem_rubric_item.id} 가 없고 {@code rubricKey}(R1·R2…)만 있다.
     * 어댑터에는 숫자 id 가 필요하므로 <b>표시 순서대로 1부터</b> 붙이고, 라벨도 같은 규칙으로
     * 맞춘다. 판정을 되짚을 때 쓰는 것은 id 가 아니라 {@code rubricKey} 다.
     */
    private List<Case> readCases(JsonNode labels) throws Exception {
        List<Case> cases = new ArrayList<>();
        for (JsonNode node : labels.path("cases")) {
            Path questionPath = GOLDENSET.resolve(node.path("question").asString(""));
            JsonNode question = objectMapper.readTree(
                    Files.readString(questionPath, StandardCharsets.UTF_8));

            List<RubricCriterion> criteria = new ArrayList<>();
            List<String> rubricKeys = new ArrayList<>();
            long rubricItemId = 1;
            for (JsonNode item : question.path("rubricItems")) {
                criteria.add(new RubricCriterion(rubricItemId++, item.path("criterion").asString("")));
                rubricKeys.add(item.path("rubricKey").asString(""));
            }

            Map<String, String> expected = new LinkedHashMap<>();
            for (JsonNode item : node.path("expected")) {
                expected.put(item.path("rubricKey").asString(""), item.path("verdict").asString(""));
            }

            cases.add(new Case(
                    node.path("caseId").asString(""),
                    node.path("question").asString(""),
                    GOLDENSET.resolve(node.path("answerImage").asString("")),
                    node.path("note").asString(""),
                    criteria, rubricKeys, expected));
        }
        return cases;
    }

    private static String toDataUri(Path image) throws Exception {
        String name = image.getFileName().toString().toLowerCase();
        String mime = name.endsWith(".png") ? "image/png" : "image/jpeg";
        return "data:" + mime + ";base64,"
                + Base64.getEncoder().encodeToString(Files.readAllBytes(image));
    }

    // ===== 값 =====

    private record Case(String caseId, String question, Path answerImage, String note,
                        List<RubricCriterion> criteria, List<String> rubricKeys,
                        Map<String, String> expectedByRubricKey) {

        /** 표시 순서로 붙인 id 를 {@code rubricKey} 로 되돌린다. */
        String rubricKeyOf(long rubricItemId) {
            int index = (int) rubricItemId - 1;
            return index >= 0 && index < rubricKeys.size() ? rubricKeys.get(index) : "?";
        }
    }

    private record Outcome(Case testCase, boolean withTools, EssayGradingRun run) {

        String group() {
            return withTools ? "A" : "B";
        }

        /** 라벨과 맞은 항목 수. 판정이 안 붙은 항목은 틀린 것으로 센다 — 빈칸도 결과다. */
        int matched() {
            int matched = 0;
            for (RubricJudgement judgement : run.result().judgements()) {
                String key = testCase.rubricKeyOf(judgement.rubricItemId());
                if (judgement.verdict().name().equals(testCase.expectedByRubricKey().get(key))) {
                    matched++;
                }
            }
            return matched;
        }

        int labelled() {
            return testCase.expectedByRubricKey().size();
        }

        /** 라벨 기준 판정별 정오. {@code 라벨 verdict -> [맞은 수, 전체]}. */
        void foldByExpected(Map<String, int[]> into) {
            Map<Long, RubricJudgement> byId = new LinkedHashMap<>();
            run.result().judgements().forEach(j -> byId.put(j.rubricItemId(), j));
            for (int index = 0; index < testCase.rubricKeys().size(); index++) {
                String key = testCase.rubricKeys().get(index);
                String expected = testCase.expectedByRubricKey().get(key);
                if (expected == null) {
                    continue;
                }
                RubricJudgement judgement = byId.get((long) (index + 1));
                int[] tally = into.computeIfAbsent(expected, ignored -> new int[2]);
                tally[1]++;
                if (judgement != null && judgement.verdict().name().equals(expected)) {
                    tally[0]++;
                }
            }
        }
    }

    // ===== 보고서 =====

    /** 표만 쓴다. 해석 문장을 넣지 않는다(§6). */
    private record ReportWriter(List<Case> cases, List<Outcome> outcomes) {

        String render() {
            StringBuilder out = new StringBuilder();
            out.append("# 단계 4 — 도구가 값을 하는가 (A: math 도구 루프 / B: 도구 없음)\n\n")
                    .append("- seed: ").append(SEED).append(" (D18 — 측정 전용)\n")
                    .append("- 케이스: ").append(cases.size())
                    .append(" · 라벨 항목: ").append(cases.stream().mapToInt(c -> c.expectedByRubricKey().size()).sum())
                    .append("\n\n> 값만 적는다. 해석은 보고 이후에 붙인다.\n\n");
            appendAccuracy(out);
            appendDivergence(out);
            appendUnreadable(out);
            appendCost(out);
            appendSpacing(out);
            appendPerCase(out);
            return out.toString();
        }

        /** 1. 군별 항목 판정 정확도 — 전체 / 라벨 verdict 별. */
        private void appendAccuracy(StringBuilder out) {
            out.append("## 1. 군별 항목 판정 정확도\n\n")
                    .append("| 군 | 전체 | SATISFIED | NOT_SATISFIED | UNJUDGEABLE |\n")
                    .append("|---|---|---|---|---|\n");
            for (String group : List.of("B", "A")) {
                Map<String, int[]> byExpected = new LinkedHashMap<>();
                int matched = 0;
                int total = 0;
                for (Outcome outcome : outcomes) {
                    if (!outcome.group().equals(group)) {
                        continue;
                    }
                    matched += outcome.matched();
                    total += outcome.labelled();
                    outcome.foldByExpected(byExpected);
                }
                out.append("| ").append(group).append(" | ").append(ratio(matched, total));
                for (String verdict : List.of("SATISFIED", "NOT_SATISFIED", "UNJUDGEABLE")) {
                    int[] tally = byExpected.getOrDefault(verdict, new int[2]);
                    out.append(" | ").append(ratio(tally[0], tally[1]));
                }
                out.append(" |\n");
            }
            out.append('\n');
        }

        /** 2. 케이스별 대조 — 두 군의 판정이 갈린 항목만. */
        private void appendDivergence(StringBuilder out) {
            out.append("## 2. A·B 판정이 갈린 항목\n\n")
                    .append("| 케이스 | 항목 | 라벨 | B | A | 비고 |\n|---|---|---|---|---|---|\n");
            int rows = 0;
            for (Case testCase : cases) {
                Outcome b = find(testCase, false);
                Outcome a = find(testCase, true);
                if (b == null || a == null) {
                    continue;
                }
                for (int index = 0; index < testCase.rubricKeys().size(); index++) {
                    String key = testCase.rubricKeys().get(index);
                    String expected = testCase.expectedByRubricKey().get(key);
                    String bVerdict = verdictOf(b, index + 1);
                    String aVerdict = verdictOf(a, index + 1);
                    if (bVerdict.equals(aVerdict)) {
                        continue;
                    }
                    rows++;
                    out.append("| ").append(testCase.caseId()).append(" | ").append(key)
                            .append(" | ").append(expected == null ? "-" : expected)
                            .append(" | ").append(bVerdict).append(" | ").append(aVerdict)
                            .append(" | ").append(testCase.note()).append(" |\n");
                }
            }
            if (rows == 0) {
                out.append("| (없음) | | | | | |\n");
            }
            out.append('\n');
        }

        /** 3. UNREADABLE 사유 분포 — D9 판단 근거. */
        private void appendUnreadable(StringBuilder out) {
            Map<String, Integer> counts = new TreeMap<>();
            for (Outcome outcome : outcomes) {
                outcome.run().trace().toolStatusCounts().forEach((k, v) -> counts.merge(k, v, Integer::sum));
            }
            int unreadable = counts.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("UNREADABLE"))
                    .mapToInt(Map.Entry::getValue).sum();
            out.append("## 3. 도구 상태 분포 (A 군만 도구를 부른다)\n\n")
                    .append("| 상태 | 횟수 | UNREADABLE 중 비율 |\n|---|---|---|\n");
            counts.forEach((status, count) -> out.append("| ").append(status).append(" | ").append(count)
                    .append(" | ").append(status.startsWith("UNREADABLE") ? ratio(count, unreadable) : "-")
                    .append(" |\n"));
            if (counts.isEmpty()) {
                out.append("| (도구 호출 없음) | 0 | - |\n");
            }
            out.append('\n');
        }

        /** 4. 토큰·시간 — B 대비 A 의 배수. */
        private void appendCost(StringBuilder out) {
            out.append("## 4. 토큰·시간\n\n")
                    .append("| 군 | 차수 합 | 도구호출 | 프롬프트 | 완성 | 추론 | 소요ms 합 | 케이스당 평균ms |\n")
                    .append("|---|---|---|---|---|---|---|---|\n");
            long[] bTotals = null;
            for (String group : List.of("B", "A")) {
                long calls = 0;
                long tools = 0;
                long prompt = 0;
                long completion = 0;
                long reasoning = 0;
                long millis = 0;
                int n = 0;
                for (Outcome outcome : outcomes) {
                    if (!outcome.group().equals(group)) {
                        continue;
                    }
                    EssayGradingRun.Trace trace = outcome.run().trace();
                    calls += trace.modelCalls();
                    tools += trace.toolCalls();
                    prompt += orZero(trace.promptTokens());
                    completion += orZero(trace.completionTokens());
                    reasoning += orZero(trace.reasoningTokens());
                    millis += trace.elapsedMillis();
                    n++;
                }
                out.append("| ").append(group).append(" | ").append(calls).append(" | ").append(tools)
                        .append(" | ").append(prompt).append(" | ").append(completion)
                        .append(" | ").append(reasoning).append(" | ").append(millis)
                        .append(" | ").append(n == 0 ? 0 : millis / n).append(" |\n");
                if (group.equals("B")) {
                    bTotals = new long[] {prompt, completion, reasoning, millis};
                } else if (bTotals != null) {
                    out.append("| A/B 배수 | - | - | ").append(times(prompt, bTotals[0]))
                            .append(" | ").append(times(completion, bTotals[1]))
                            .append(" | ").append(times(reasoning, bTotals[2]))
                            .append(" | ").append(times(millis, bTotals[3])).append(" | - |\n");
                }
            }
            out.append('\n');
        }

        /** 5. 공백 축 — {@code net != len} 인 도구 호출 비율은 도구 로그에서 읽는다. */
        private void appendSpacing(StringBuilder out) {
            out.append("## 5. 공백 축 (net != len)\n\n")
                    .append("도구 호출마다 `[도구] math len=.. net=.. status=.. reason=..` 로 남는다.\n")
                    .append("이 표는 로그에서 집계한다 — 도구 인자를 프로세스 밖으로 들고 나오지 않기 때문이다(D11).\n\n")
                    .append("```\n")
                    .append("grep '\\[도구\\] math' <로그> | awk '{split($3,l,\"=\"); split($4,n,\"=\"); ")
                    .append("t++; if (l[2]!=n[2]) s++} END {print \"공백 있던 호출\", s\"/\"t}'\n")
                    .append("```\n\n");
        }

        /** 케이스별 원값. transcription 전문이 여기 남는다. */
        private void appendPerCase(StringBuilder out) {
            out.append("## 6. 케이스별 원값\n\n");
            for (Case testCase : cases) {
                out.append("### ").append(testCase.caseId()).append(" — ").append(testCase.question())
                        .append("\n\n- 이미지: `").append(testCase.answerImage().getFileName()).append("`\n")
                        .append("- 비고: ").append(testCase.note()).append("\n\n")
                        .append("| 항목 | 라벨 | B | A |\n|---|---|---|---|\n");
                Outcome b = find(testCase, false);
                Outcome a = find(testCase, true);
                for (int index = 0; index < testCase.rubricKeys().size(); index++) {
                    String key = testCase.rubricKeys().get(index);
                    out.append("| ").append(key).append(" | ")
                            .append(testCase.expectedByRubricKey().getOrDefault(key, "-")).append(" | ")
                            .append(b == null ? "-" : verdictOf(b, index + 1)).append(" | ")
                            .append(a == null ? "-" : verdictOf(a, index + 1)).append(" |\n");
                }
                out.append('\n');
                appendRunDetail(out, "B", b);
                appendRunDetail(out, "A", a);
            }
        }

        private void appendRunDetail(StringBuilder out, String group, Outcome outcome) {
            if (outcome == null) {
                return;
            }
            EssayGradingRun.Trace trace = outcome.run().trace();
            out.append("<details><summary>").append(group).append(" 군 — status=")
                    .append(outcome.run().result().status())
                    .append(" 차수=").append(trace.modelCalls())
                    .append(" 도구=").append(trace.toolCalls())
                    .append(" 버림=").append(trace.droppedItems())
                    .append(" JSON실패=").append(trace.malformedOutputs())
                    .append(" ms=").append(trace.elapsedMillis())
                    .append("</summary>\n\n")
                    .append("도구 상태: ").append(trace.toolStatusCounts()).append("\n\n")
                    .append("**transcription**\n\n```\n")
                    .append(outcome.run().result().transcription()).append("\n```\n\n")
                    .append("**evidence**\n\n");
            for (RubricJudgement judgement : outcome.run().result().judgements()) {
                out.append("- ").append(outcome.testCase().rubricKeyOf(judgement.rubricItemId()))
                        .append(" `").append(judgement.verdict()).append("` ")
                        .append(judgement.evidence()).append('\n');
            }
            out.append("\n</details>\n\n");
        }

        private Outcome find(Case testCase, boolean withTools) {
            return outcomes.stream()
                    .filter(o -> o.testCase() == testCase && o.withTools() == withTools)
                    .findFirst().orElse(null);
        }

        private static String verdictOf(Outcome outcome, long rubricItemId) {
            return outcome.run().result().judgements().stream()
                    .filter(j -> j.rubricItemId() == rubricItemId)
                    .map(j -> j.verdict().name())
                    .findFirst().orElse("(없음)");
        }

        private static String ratio(int matched, int total) {
            return total == 0 ? "-" : "%d/%d (%.1f%%)".formatted(matched, total, 100.0 * matched / total);
        }

        private static String times(long value, long base) {
            return base == 0 ? "-" : "%.2fx".formatted((double) value / base);
        }

        private static long orZero(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
