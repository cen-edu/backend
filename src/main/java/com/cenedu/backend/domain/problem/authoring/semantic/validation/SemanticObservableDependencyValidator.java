package com.cenedu.backend.domain.problem.authoring.semantic.validation;

import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/** editable parameter가 계산 그래프를 거쳐 실제 문제 표현에 도달하는지 검증한다. */
public final class SemanticObservableDependencyValidator {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([A-Z][A-Z0-9_]*?)(?:_UNIT)?}}");
    private static final Pattern RAW_TOKEN = Pattern.compile("\\{\\{([A-Z][A-Z0-9_]*)}}");
    private final ObjectMapper mapper = new ObjectMapper();

    public void validate(ProblemSemanticModelV1 model) {
        java.util.ArrayList<String> violations = new java.util.ArrayList<>();
        appendDefinitionViolations(model, violations);
        if (!violations.isEmpty()) throw new SemanticValidationException(violations);
    }

    public void appendDefinitionViolations(ProblemSemanticModelV1 model, List<String> violations) {
        if (model == null || model.presentation() == null) return;
        Map<String, Set<String>> dependents = dependents(model);
        Set<String> conditionReferences = conditionReferences(model);
        Set<String> affectedByEditable = new HashSet<>();
        for (var parameter : model.parameters()) {
            if (!parameter.editable()) continue;
            Set<String> affected = reachable(parameter.key(), dependents);
            affectedByEditable.addAll(affected);
            if (affected.stream().noneMatch(conditionReferences::contains)) {
                violations.add("parameters." + parameter.key()
                        + ": editable parameter가 question·step·diagram 표현에 연결되지 않았습니다.");
            }
        }
        for (var choice : model.presentation().choices()) {
            if (choice.valueKey() != null && affectedByEditable.contains(choice.valueKey())
                    && !valuePlaceholderKeys(choice.contentTemplate()).contains(choice.valueKey())) {
                violations.add("presentation.choices." + choice.choiceKey()
                        + ".contentTemplate: editable 결과 valueKey를 placeholder로 참조해야 합니다.");
            }
        }
        appendLiteralBindingViolations(model, affectedByEditable, violations);
    }

    private Map<String, Set<String>> dependents(ProblemSemanticModelV1 model) {
        Map<String, Set<String>> result = new HashMap<>();
        for (var computation : model.computations()) {
            for (String operand : computation.operands()) {
                result.computeIfAbsent(operand, ignored -> new HashSet<>()).add(computation.key());
            }
        }
        return result;
    }

    private Set<String> conditionReferences(ProblemSemanticModelV1 model) {
        Set<String> result = new HashSet<>(placeholderKeys(model.presentation().questionTemplate()));
        for (var step : model.presentation().steps()) {
            result.addAll(placeholderKeys(step.labelTemplate()));
            for (var segment : step.segments()) {
                result.addAll(placeholderKeys(segment.textTemplate()));
                result.addAll(placeholderKeys(segment.displayUnitTemplate()));
                if (segment.valueKey() != null) result.add(segment.valueKey());
            }
        }
        JsonNode diagrams = mapper.valueToTree(model.diagrams());
        collectDiagramReferences(diagrams, allKeys(model), result);
        return result;
    }

    private Set<String> allKeys(ProblemSemanticModelV1 model) {
        Set<String> result = new HashSet<>();
        model.parameters().forEach(value -> result.add(value.key()));
        model.computations().forEach(value -> result.add(value.key()));
        return result;
    }

    private void collectDiagramReferences(JsonNode node, Set<String> keys, Set<String> result) {
        if (node == null) return;
        if (node.isTextual()) {
            result.addAll(placeholderKeys(node.asText()));
            if (keys.contains(node.asText())) result.add(node.asText());
            return;
        }
        node.elements().forEachRemaining(child -> collectDiagramReferences(child, keys, result));
    }

    private Set<String> reachable(String start, Map<String, Set<String>> dependents) {
        Set<String> result = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String key = queue.removeFirst();
            if (!result.add(key)) continue;
            dependents.getOrDefault(key, Set.of()).forEach(queue::addLast);
        }
        return result;
    }

    private Set<String> placeholderKeys(String template) {
        Set<String> result = new HashSet<>();
        if (template == null) return result;
        var matcher = TOKEN.matcher(template);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    /** UNIT placeholder는 값 자체를 렌더링했다고 간주하지 않는다. */
    private Set<String> valuePlaceholderKeys(String template) {
        Set<String> result = new HashSet<>();
        if (template == null) return result;
        var matcher = RAW_TOKEN.matcher(template);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!key.endsWith("_UNIT")) result.add(key);
        }
        return result;
    }

    /**
     * 현재 값을 문구에 문자열로 박아 둔 모델은 최초 물질화는 같아도 다음 patch에서 이전 값을 노출한다.
     * 편집으로 영향받는 key의 현재 값이 표시 template에 그대로 있다면 반드시 해당 value placeholder를 쓰게 한다.
     */
    private void appendLiteralBindingViolations(ProblemSemanticModelV1 model,
            Set<String> affectedByEditable, List<String> violations) {
        Map<String, String> values = new HashMap<>();
        Map<String, String> units = new HashMap<>();
        model.parameters().forEach(parameter -> {
            values.put(parameter.key(), parameter.value());
            units.put(parameter.key(), parameter.unit());
        });
        model.computations().forEach(computation -> {
            values.put(computation.key(), computation.result());
            units.put(computation.key(), computation.unit());
        });

        BiConsumer<String, String> inspect = (path, template) -> {
            Set<String> referencedValues = valuePlaceholderKeys(template);
            for (String key : affectedByEditable) {
                if (referencedValues.contains(key)) continue;
                if (containsLiteral(template, values.get(key), units.get(key))) {
                    violations.add(path + ": editable 값 " + key
                            + "를 문자열로 고정하지 말고 {{" + key + "}}를 사용해야 합니다.");
                }
            }
        };

        inspect.accept("presentation.questionTemplate", model.presentation().questionTemplate());
        inspect.accept("presentation.explanationTemplate", model.presentation().explanationTemplate());
        for (var choice : model.presentation().choices()) {
            inspect.accept("presentation.choices." + choice.choiceKey() + ".contentTemplate",
                    choice.contentTemplate());
        }
        for (var step : model.presentation().steps()) {
            inspect.accept("presentation.steps." + step.stepKey() + ".labelTemplate", step.labelTemplate());
            for (int index = 0; index < step.segments().size(); index++) {
                var segment = step.segments().get(index);
                inspect.accept("presentation.steps." + step.stepKey() + ".segments[" + index + "].textTemplate",
                        segment.textTemplate());
                inspect.accept("presentation.steps." + step.stepKey() + ".segments[" + index
                        + "].displayUnitTemplate", segment.displayUnitTemplate());
            }
        }
        var guide = model.presentation().learningGuide();
        if (guide != null) {
            inspect.accept("presentation.learningGuide.conceptTitleTemplate", guide.conceptTitleTemplate());
            inspect.accept("presentation.learningGuide.summaryTemplate", guide.summaryTemplate());
            for (int index = 0; index < guide.keyPointTemplates().size(); index++) {
                inspect.accept("presentation.learningGuide.keyPointTemplates[" + index + "]",
                        guide.keyPointTemplates().get(index));
            }
        }
        for (var rubric : model.presentation().rubrics()) {
            inspect.accept("presentation.rubrics." + rubric.rubricKey() + ".criterionTemplate",
                    rubric.criterionTemplate());
        }
    }

    private boolean containsLiteral(String template, String value, String unit) {
        if (template == null || value == null || value.isBlank()) return false;
        String leftBoundary = "(?<![\\p{L}\\p{N}_])";
        String rightBoundary = "(?![\\p{L}\\p{N}_])";
        String valuePattern = Pattern.quote(value.trim());
        if (unit != null && !unit.isBlank()) {
            Pattern withUnit = Pattern.compile(leftBoundary + valuePattern + "\\s*"
                    + Pattern.quote(unit.trim()) + rightBoundary);
            if (withUnit.matcher(template).find()) return true;
        }
        return Pattern.compile(leftBoundary + valuePattern + rightBoundary).matcher(template).find();
    }
}
