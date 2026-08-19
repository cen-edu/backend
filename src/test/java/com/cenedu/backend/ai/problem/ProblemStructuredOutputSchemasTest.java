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
        JsonNode definitions = root.path("$defs");
        assertItemsRef(definitions, "coordinateGraph", "points", "coordinatePoint");
        assertItemsRef(definitions, "coordinateGraph", "segments", "coordinateSegment");
        assertItemsRef(definitions, "coordinateGraph", "lines", "coordinateLine");
        assertItemsRef(definitions, "coordinateGraph", "functions", "function");
        assertItemsRef(definitions, "planeGeometry", "points", "planePoint");
        assertItemsRef(definitions, "planeGeometry", "segments", "planeSegment");
        assertItemsRef(definitions, "planeGeometry", "angles", "angle");
        assertItemsRef(definitions, "planeGeometry", "polygons", "polygon");
        assertItemsRef(definitions, "planeGeometry", "arcs", "arc");
        assertItemsRef(definitions, "planeGeometry", "measurements", "measurement");
        assertItemsRef(definitions, "solidGeometry", "labels", "solidLabel");
        assertItemsRef(definitions, "dataTable", "cells", "cell");
        assertItemsRef(definitions, "dataTable", "highlightedCells", "address");
        JsonNode solidKinds = definitions.path("solidGeometry").path("allOf").get(1)
                .path("properties").path("solidKind").path("enum");
        assertThat(solidKinds).hasSize(6);
        assertThat(solidKinds.get(0).asText()).isEqualTo("RECTANGULAR_PRISM");
        assertThat(solidKinds.get(5).asText()).isEqualTo("SPHERE");
        assertEveryObjectIsClosed(root);
    }

    private void assertItemsRef(JsonNode definitions, String definition, String property, String expectedDefinition) {
        JsonNode schema = definitions.path(definition);
        JsonNode properties = schema.path("properties");
        if (properties.isMissingNode()) properties = schema.path("allOf").get(1).path("properties");
        assertThat(properties.path(property).path("items").path("$ref").asText())
                .isEqualTo("#/$defs/" + expectedDefinition);
    }

    private void assertEveryObjectIsClosed(JsonNode node) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())) assertThat(node.path("additionalProperties").asBoolean()).isFalse();
            node.elements().forEachRemaining(this::assertEveryObjectIsClosed);
        } else if (node.isArray()) node.elements().forEachRemaining(this::assertEveryObjectIsClosed);
    }
}
