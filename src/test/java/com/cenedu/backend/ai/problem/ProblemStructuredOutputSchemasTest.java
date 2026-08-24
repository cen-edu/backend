package com.cenedu.backend.ai.problem;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProblemStructuredOutputSchemasTest {
    @Test void editTurnRequiresSemanticPatchFieldForStrictStructuredOutputs() throws Exception {
        JsonNode root = new ObjectMapper().readTree(ProblemStructuredOutputSchemas.EDIT_TURN);
        JsonNode result = root.path("properties").path("problemEditResult");

        assertThat(result.path("required").toString()).contains("semanticPatch");
    }

    @Test void semanticSchemaIsStrictAtEveryObjectNode() throws Exception {
        JsonNode root = new ObjectMapper().readTree(ProblemStructuredOutputSchemas.SEMANTIC_MODEL);
        assertThat(root.path("additionalProperties").asBoolean()).isFalse();
        assertThat(root.path("properties").has("schemaVersion")).isTrue();
        assertThat(root.path("properties").path("diagrams").path("items").path("anyOf").size()).isEqualTo(5);
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
        JsonNode solidKinds = definitions.path("solidGeometry")
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
            if (node.has("required")) {
                var required = new java.util.HashSet<String>();
                node.path("required").forEach(value -> assertThat(required.add(value.asText())).isTrue());
            }
            node.elements().forEachRemaining(this::assertEveryObjectIsClosed);
        } else if (node.isArray()) node.elements().forEachRemaining(this::assertEveryObjectIsClosed);
    }

    @Test void repairDeltaHasTypeOnEveryReplacementFieldAndOnlyTargetedFields() throws Exception {
        var targets = java.util.Set.of(
                com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.EXPLANATION,
                com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.STEPS,
                com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.CHOICES);
        JsonNode root = new ObjectMapper().readTree(ProblemStructuredOutputSchemas.repairDeltaFor(targets));

        JsonNode replacementProps = root.path("properties").path("replacements").path("properties");
        // 계획 대상만 포함한다.
        var fields = new java.util.HashSet<String>();
        replacementProps.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("EXPLANATION", "STEPS", "CHOICES");
        // 모든 대체 필드에 type 키가 있어야 한다 — 빈 {}(type 없음)는 OpenAI가 400으로 거부했다.
        replacementProps.fieldNames().forEachRemaining(field ->
                assertThat(replacementProps.path(field).has("type"))
                        .as("replacements.%s must declare a type", field).isTrue());
        // replacements와 rationale은 필수, 대상 필드도 required에 담긴다.
        assertThat(root.path("required").toString()).contains("replacements").contains("rationale");
        assertThat(root.path("properties").path("replacements").path("required").toString())
                .contains("EXPLANATION").contains("STEPS").contains("CHOICES");
        assertThat(root.path("properties").path("replacements").path("additionalProperties").asBoolean()).isFalse();
    }

    @Test void semanticSchemaDoesNotContainUnsupportedComposition() {
        String schema = ProblemStructuredOutputSchemas.SEMANTIC_MODEL;
        assertThat(schema).doesNotContain("\"allOf\"");
        assertThat(schema).doesNotContain("\"oneOf\"");
        assertThat(schema).doesNotContain("\"not\"");
    }

    @Test void semanticSchemaMatchesServerOwnedChoiceAndAssetContracts() throws Exception {
        JsonNode root = new ObjectMapper().readTree(ProblemStructuredOutputSchemas.SEMANTIC_MODEL);
        JsonNode definitions = root.path("$defs");
        assertThat(definitions.path("base").path("properties").path("assetKey").path("pattern").asText())
                .isEqualTo("^F[1-9][0-9]*$");
        JsonNode valueKey = definitions.path("choice").path("properties").path("valueKey");
        assertThat(valueKey.path("type").asText()).isEqualTo("string");
        assertThat(valueKey.has("anyOf")).isFalse();
        assertThat(definitions.path("presentation").path("properties").path("learningGuide").path("type").asText())
                .isEqualTo("object");
    }
}
