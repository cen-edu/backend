package com.cenedu.backend.ai.problem;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProblemStructuredOutputSchemasTest {
    @Test void semanticSchemaIsStrictAtEveryObjectNode() throws Exception {
        JsonNode root = new ObjectMapper().readTree(ProblemStructuredOutputSchemas.SEMANTIC_MODEL);
        assertThat(root.path("additionalProperties").asBoolean()).isFalse();
        assertThat(root.path("properties").has("schemaVersion")).isTrue();
        assertThat(root.path("properties").path("diagrams").path("items").path("oneOf").size()).isEqualTo(5);
        assertEveryObjectIsClosed(root);
    }

    private void assertEveryObjectIsClosed(JsonNode node) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())) assertThat(node.path("additionalProperties").asBoolean()).isFalse();
            node.elements().forEachRemaining(this::assertEveryObjectIsClosed);
        } else if (node.isArray()) node.elements().forEachRemaining(this::assertEveryObjectIsClosed);
    }
}
