package com.cenedu.backend.ai.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 수정 대상별 출력 schema가 OpenAI strict object 계약을 지키는지 검증한다. */
class ProblemModificationOutputSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 보기_수정은_CANDIDATE의_보기_배열_계약을_그대로_사용한다() throws Exception {
        JsonNode schema = schema(Set.of(EditTargetType.CHOICE));

        assertThat(fieldNames(schema.path("properties"))).containsExactly("choices");
        assertThat(textValues(schema.path("required"))).containsExactly("choices");
        assertThat(schema.at("/properties/choices/items/additionalProperties").asBoolean()).isFalse();
        assertStrictObjects(schema);
    }

    @Test
    void 본문_수정은_question과_contentBlocks만_필수로_요구한다() throws Exception {
        JsonNode schema = schema(Set.of(EditTargetType.CONTENT_BLOCK));

        assertThat(fieldNames(schema.path("properties"))).containsExactly("question", "contentBlocks");
        assertThat(textValues(schema.path("required"))).containsExactly("question", "contentBlocks");
        assertStrictObjects(schema);
    }

    @Test
    void 답안_수정은_answerUnits_계약만_노출한다() throws Exception {
        JsonNode schema = schema(Set.of(EditTargetType.ANSWER_UNIT));

        assertThat(fieldNames(schema.path("properties"))).containsExactly("answerUnits");
        assertThat(textValues(schema.path("required"))).containsExactly("answerUnits");
        assertStrictObjects(schema);
    }

    @Test
    void 대상이_없으면_추가_프로퍼티를_허용하지_않는_빈_객체다() throws Exception {
        JsonNode schema = schema(Set.of());

        assertThat(schema.path("properties").isEmpty()).isTrue();
        assertThat(schema.path("required").isEmpty()).isTrue();
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void 문항_전체_교체는_전체_CANDIDATE_계약을_사용한다() throws Exception {
        assertThat(schema(Set.of(EditTargetType.WHOLE_QUESTION)))
                .isEqualTo(mapper.readTree(ProblemStructuredOutputSchemas.CANDIDATE));
    }

    private JsonNode schema(Set<EditTargetType> targets) throws Exception {
        return mapper.readTree(ProblemStructuredOutputSchemas.modificationDeltaFor(targets));
    }

    private List<String> fieldNames(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private void assertStrictObjects(JsonNode root) {
        List<String> violations = new ArrayList<>();
        inspect(root, "$", violations);
        assertThat(violations).isEmpty();
    }

    private void inspect(JsonNode node, String path, List<String> violations) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())
                    && (!node.has("additionalProperties") || node.path("additionalProperties").asBoolean(true))) {
                violations.add(path);
            }
            node.fields().forEachRemaining(entry -> inspect(entry.getValue(), path + "." + entry.getKey(), violations));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) inspect(node.get(index), path + "[" + index + "]", violations);
        }
    }
}
