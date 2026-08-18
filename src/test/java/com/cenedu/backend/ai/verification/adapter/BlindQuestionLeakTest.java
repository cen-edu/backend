package com.cenedu.backend.ai.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Blind 변환에서 정답이 새는지 본다. <b>이 파일이 이 작업의 핵심이다.</b>
 *
 * <p>세 층으로 짠다. 층마다 잡는 실패가 다르다.
 * <ol>
 *   <li><b>필드명</b> — 금지 필드가 이름째로 넘어갔는가. 필드를 그대로 복사한 실수를 잡는다.</li>
 *   <li><b>값</b> — 정답 문자열이 다른 필드에 섞여 넘어갔는가. 이름을 바꿔 옮긴 실수와
 *       발문·altText 에 정답이 묻어 있는 경우를 잡는다. <b>부분 문자열까지</b> 본다.</li>
 *   <li><b>구조</b> — S1 에 필드가 생겼는데 아무도 판단하지 않았는가. 이게 핵심이다.
 *       조용히 통과하는 것이 가장 나쁜 결과다.</li>
 * </ol>
 *
 * <p>3층이 실패하면 사람이 {@link BlindFieldPolicy} 의 두 목록 중 하나에 필드를 넣어야 한다.
 * 자동으로 한쪽에 넣는 기본값을 두지 않는다 — 기본값이 있으면 판단이 일어나지 않는다.
 */
class BlindQuestionLeakTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BlindQuestionFactory factory = new BlindQuestionFactory();

    /** 직렬화 결과에 이름이 보이면 안 되는 필드. {@link BlindFieldPolicy#EXCLUDED} 에서 뽑는다. */
    private static Set<String> forbiddenFieldNames() {
        Set<String> names = new HashSet<>();
        for (String key : BlindFieldPolicy.EXCLUDED.keySet()) {
            names.add(key.substring(key.indexOf('.') + 1));
        }
        return names;
    }

    @Test
    @DisplayName("1층 — 직렬화 결과에 금지 필드명이 없다")
    void blindQuestionMustNotContainForbiddenFieldNames() {
        assertThat(forbiddenFieldNames()).isNotEmpty();

        for (QuestionSnapshotV1 snapshot : allSnapshots()) {
            Set<String> names = fieldNames(serialize(snapshot));
            for (String forbidden : forbiddenFieldNames()) {
                assertThat(names)
                        .as("%s 에서 금지 필드 '%s' 가 Blind 결과에 있다",
                                snapshot.metadata().questionType(), forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("2층 — 원본의 정답 문자열이 부분 문자열로도 남지 않는다")
    void blindQuestionMustNotContainAnswerValues() {
        for (QuestionSnapshotV1 snapshot : allSnapshots()) {
            List<String> values = textValues(serialize(snapshot));
            List<String> secrets = secretsOf(snapshot);

            // 픽스처가 정답류를 아예 안 담고 있으면 이 층은 아무것도 검사하지 않은 것이다.
            assertThat(secrets)
                    .as("%s 픽스처에 정답류 값이 없다 — 2층이 무력하다",
                            snapshot.metadata().questionType())
                    .isNotEmpty();

            for (String secret : secrets) {
                for (String value : values) {
                    assertThat(value)
                            .as("%s 에서 정답 값 '%s' 가 Blind 결과에 남았다",
                                    snapshot.metadata().questionType(), secret)
                            .doesNotContain(secret);
                }
            }
        }
    }

    /**
     * 객관식의 {@code answerRaw} 는 2층 대상이 아니다.
     *
     * <p>그 값은 {@code choiceKey} 이고, 보기 목록은 Solver 가 답을 고르기 위해 반드시 봐야 한다.
     * 즉 정답 보기의 키는 <b>구조상 항상 Blind 안에 있다.</b> C1~C4 중 하나라는 사실은 유출이 아니다.
     * 유출은 그 키가 "정답"으로 표시되어 넘어갈 때이고, 그건 1층(금지 필드명)이 잡는다.
     *
     * <p>이 예외를 목록에서 조용히 빼지 않고 여기서 단언한다 — 예외를 안 보이게 처리하면
     * 나중에 MC 의 정답이 실제로 새어도 아무 층도 울지 않는다.
     */
    @Test
    @DisplayName("2층 예외 — 객관식 정답은 choiceKey 이며 보기 키 집합 안에서만 드러난다")
    void multipleChoiceAnswerIsOnlyExposedAsAChoiceKey() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.multipleChoiceSnapshot();
        String answerRaw = snapshot.answerUnits().getFirst().answerRaw();

        List<String> choiceKeys = snapshot.choices().stream()
                .map(choice -> choice.choiceKey())
                .toList();
        assertThat(choiceKeys)
                .as("객관식 정답이 choiceKey 가 아니면 2층에서 정상 검사해야 한다")
                .contains(answerRaw);

        BlindQuestion blind = factory.from(snapshot);
        assertThat(blind.choices())
                .as("Blind 는 보기를 전부 담고, 어느 것이 정답인지는 담지 않는다")
                .hasSize(snapshot.choices().size());
        assertThat(serialize(snapshot).toString())
                .as("Blind 에 정답 표시 필드가 생겼다")
                .doesNotContain("answerRaw")
                .doesNotContain("correct");
    }

    @Test
    @DisplayName("3층 — S1 의 모든 필드가 허용 또는 명시제외 목록에 있다")
    void everySnapshotFieldMustBeClassified() {
        List<String> unclassified = new ArrayList<>();

        // 허용한 레코드 필드만 따라 내려간다. 제외한 레코드의 내부는 옮기지 않으므로 열거하지 않는다.
        Deque<Class<?>> pending = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        pending.add(QuestionSnapshotV1.class);

        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (!visited.add(type)) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                String key = type.getSimpleName() + "." + component.getName();
                boolean allowed = BlindFieldPolicy.ALLOWED.contains(key);
                if (!allowed && !BlindFieldPolicy.EXCLUDED.containsKey(key)) {
                    unclassified.add(key);
                    continue;
                }
                if (allowed) {
                    nestedRecord(component).ifPresent(pending::add);
                }
            }
        }

        assertThat(unclassified)
                .as("""
                        S1 에 분류되지 않은 필드가 있다. BlindFieldPolicy 의 ALLOWED 또는 EXCLUDED 에 \
                        넣어라. 어느 쪽인지는 사람이 정한다 — 정답이나 저작측 의도가 담기면 EXCLUDED 다.""")
                .isEmpty();
    }

    @Test
    @DisplayName("3층 — 목록에 실재하지 않는 필드가 남아 있지 않다")
    void policyMustNotReferenceRemovedFields() {
        Set<String> actual = new TreeSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        pending.add(QuestionSnapshotV1.class);

        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (!visited.add(type)) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                String key = type.getSimpleName() + "." + component.getName();
                actual.add(key);
                if (BlindFieldPolicy.ALLOWED.contains(key)) {
                    nestedRecord(component).ifPresent(pending::add);
                }
            }
        }

        Set<String> stale = new TreeSet<>();
        BlindFieldPolicy.ALLOWED.stream().filter(key -> !actual.contains(key)).forEach(stale::add);
        BlindFieldPolicy.EXCLUDED.keySet().stream()
                .filter(key -> !actual.contains(key)).forEach(stale::add);

        // 필드가 사라졌거나 이름이 바뀌었는데 표만 남으면, 표를 읽는 사람이 실제와 다른 것을 본다.
        assertThat(stale).as("BlindFieldPolicy 에 S1 에 없는 필드가 남아 있다").isEmpty();
    }

    @Test
    @DisplayName("3층 — 허용과 명시제외가 겹치지 않는다")
    void allowedAndExcludedMustBeDisjoint() {
        Set<String> overlap = new TreeSet<>(BlindFieldPolicy.ALLOWED);
        overlap.retainAll(BlindFieldPolicy.EXCLUDED.keySet());

        assertThat(overlap).as("한 필드가 허용과 제외에 동시에 있다 — 어느 쪽이 이기는지 알 수 없다")
                .isEmpty();
    }

    @Test
    @DisplayName("명시제외 사유가 비어 있지 않다")
    void everyExclusionMustCarryAReason() {
        BlindFieldPolicy.EXCLUDED.forEach((key, reason) ->
                assertThat(reason).as("%s 의 제외 사유가 비었다", key).isNotBlank());
    }

    @Test
    @DisplayName("모르는 스키마 버전은 변환하지 않고 멈춘다")
    void unsupportedSchemaVersionMustFailFast() {
        QuestionSnapshotV1 future = VerificationFixtures.withSchemaVersion(
                VerificationFixtures.shortInputSnapshot(), 99);

        assertThatThrownBy(() -> factory.from(future))
                .isInstanceOf(UnsupportedSnapshotVersionException.class)
                .hasMessageContaining("99");
    }


    private static List<QuestionSnapshotV1> allSnapshots() {
        return List.of(
                VerificationFixtures.multipleChoiceSnapshot(),
                VerificationFixtures.shortInputSnapshot(),
                VerificationFixtures.stepFillSnapshot(),
                VerificationFixtures.essaySnapshot(),
                VerificationFixtures.figureSnapshot());
    }

    /**
     * Blind 결과를 JSON 트리로 만든다.
     *
     * <p>문자열이 아니라 트리를 보는 이유: JSON 은 백슬래시를 이스케이프한다. 원본 정답
     * {@code 2^2 \times 3} 은 직렬화 문자열 안에서 {@code 2^2 \\times 3} 이 되어,
     * 원본 문자열로 {@code contains} 를 걸면 <b>실제로 새어도 통과한다.</b>
     */
    private JsonNode serialize(QuestionSnapshotV1 snapshot) {
        return MAPPER.valueToTree(factory.from(snapshot));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        collect(node, names, null);
        return names;
    }

    private static List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        collect(node, new HashSet<>(), values);
        return values;
    }

    private static void collect(
            JsonNode node, Set<String> names, List<String> values
    ) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                names.add(entry.getKey());
                collect(entry.getValue(), names, values);
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(element -> collect(element, names, values));
            return;
        }
        if (values != null && node.isString()) {
            values.add(node.stringValue());
        }
    }

    /** 원본에 있는 정답류 값. 객관식 answerRaw 는 별도 테스트가 다룬다. */
    private static List<String> secretsOf(QuestionSnapshotV1 snapshot) {
        List<String> secrets = new ArrayList<>();
        List<String> choiceKeys = snapshot.choices().stream()
                .map(choice -> choice.choiceKey())
                .toList();
        snapshot.answerUnits().forEach(unit -> {
            if (unit.answerRaw() != null && !choiceKeys.contains(unit.answerRaw())) {
                secrets.add(unit.answerRaw());
            }
            if (unit.answerNormalized() != null) {
                secrets.add(unit.answerNormalized());
            }
        });
        if (snapshot.explanation() != null) {
            secrets.add(snapshot.explanation());
        }
        if (snapshot.learningGuide() != null) {
            secrets.add(snapshot.learningGuide().summary());
        }
        snapshot.rubricItems().forEach(rubric -> secrets.add(rubric.criterion()));
        return secrets;
    }

    /** 필드 타입이 authoring 모델 레코드면 그 타입을, 리스트면 원소 타입을 돌려준다. */
    private static java.util.Optional<Class<?>> nestedRecord(RecordComponent component) {
        Class<?> raw = component.getType();
        if (raw.isRecord() && isAuthoringModel(raw)) {
            return java.util.Optional.of(raw);
        }
        if (List.class.isAssignableFrom(raw)
                && component.getGenericType() instanceof java.lang.reflect.ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element
                && element.isRecord()
                && isAuthoringModel(element)) {
            return java.util.Optional.of(element);
        }
        return java.util.Optional.empty();
    }

    /** enum·String 같은 잎 타입까지 내려가면 순회가 JDK 전체로 번진다. */
    private static boolean isAuthoringModel(Class<?> type) {
        return type.getPackageName()
                .equals("com.cenedu.backend.domain.problem.authoring.model");
    }
}
