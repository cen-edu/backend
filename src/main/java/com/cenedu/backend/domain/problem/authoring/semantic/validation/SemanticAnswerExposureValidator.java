package com.cenedu.backend.domain.problem.authoring.semantic.validation;

import com.cenedu.backend.domain.problem.authoring.diagram.CoordinateFunctionSpec;
import com.cenedu.backend.domain.problem.authoring.diagram.CoordinateGraphDiagramSpecV1;
import com.cenedu.backend.domain.problem.authoring.diagram.CoordinateLineSpec;
import com.cenedu.backend.domain.problem.authoring.diagram.CoordinatePointSpec;
import com.cenedu.backend.domain.problem.authoring.diagram.CoordinateSegmentSpec;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.SemanticTemplateEngine;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticChoiceTemplate;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticLearningGuideTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 학생이 그림만 보고 정답을 바로 알 수 있게 만드는 도식 라벨을 의미 모델 단계에서 차단한다. */
public final class SemanticAnswerExposureValidator {
    private final SemanticTemplateEngine templates = new SemanticTemplateEngine();

    /** 함수·직선·선분 라벨이 객관식 정답 또는 식 형태의 target을 직접 노출하는지 검증한다. */
    public void validate(ProblemSemanticModelV1 model, Map<String, SemanticResolvedValue> values) {
        if (model == null || values == null || model.presentation() == null || model.intent() == null) {
            return;
        }
        String target = canonical(values.get(model.intent().targetKey()));
        String correctChoice = correctChoiceValue(model, values, target);
        List<String> violations = new ArrayList<>();
        inspectLearningGuide(model.presentation().learningGuide(), values, target, correctChoice, violations);
        inspectVisualCoordinatesInExplanation(model, values, violations);
        for (var diagram : model.diagrams()) {
            if (!(diagram instanceof CoordinateGraphDiagramSpecV1 graph)) {
                continue;
            }
            for (CoordinateFunctionSpec function : graph.functions()) {
                inspect("diagrams[" + graph.assetKey() + "].functions[" + function.functionKey() + "].labelTemplate",
                        function.labelTemplate(), values, target, correctChoice, violations);
            }
            for (CoordinateLineSpec line : graph.lines()) {
                inspect("diagrams[" + graph.assetKey() + "].lines[" + line.lineKey() + "].labelTemplate",
                        line.labelTemplate(), values, target, correctChoice, violations);
            }
            for (CoordinateSegmentSpec segment : graph.segments()) {
                inspect("diagrams[" + graph.assetKey() + "].segments[" + segment.segmentKey() + "].labelTemplate",
                        segment.labelTemplate(), values, target, correctChoice, violations);
            }
            for (CoordinatePointSpec point : graph.points()) {
                inspect("diagrams[" + graph.assetKey() + "].points[" + point.pointKey() + "].labelTemplate",
                        point.labelTemplate(), values, target, correctChoice, violations);
            }
        }
        if (!violations.isEmpty()) {
            throw new SemanticValidationException(violations);
        }
    }

    private void inspectLearningGuide(SemanticLearningGuideTemplate guide,
                                      Map<String, SemanticResolvedValue> values,
                                      String target, String correctChoice, List<String> violations) {
        if (guide == null) return;
        inspectText("presentation.learningGuide.conceptTitleTemplate", guide.conceptTitleTemplate(), values,
                target, correctChoice, violations);
        inspectText("presentation.learningGuide.summaryTemplate", guide.summaryTemplate(), values,
                target, correctChoice, violations);
        for (int i = 0; i < guide.keyPointTemplates().size(); i++) {
            inspectText("presentation.learningGuide.keyPointTemplates[" + i + "]",
                    guide.keyPointTemplates().get(i), values, target, correctChoice, violations);
        }
    }

    private void inspectText(String path, String template, Map<String, SemanticResolvedValue> values,
                             String target, String correctChoice, List<String> violations) {
        if (template == null || template.isBlank()) return;
        String rendered = normalize(templates.render(template, values));
        if (!correctChoice.isBlank() && rendered.contains(correctChoice)) {
            violations.add(path + ": 학습 안내가 정답 보기 내용을 직접 노출합니다. 풀이에 필요한 개념만 설명하세요.");
        } else if (isFormula(target) && !target.isBlank() && rendered.contains(target)) {
            violations.add(path + ": 학습 안내가 정답 식을 직접 노출합니다. 최종 식이나 정답 값을 넣지 마세요.");
        }
    }

    private void inspectVisualCoordinatesInExplanation(ProblemSemanticModelV1 model,
                                                       Map<String, SemanticResolvedValue> values,
                                                       List<String> violations) {
        String explanation = model.presentation().explanationTemplate();
        if (explanation == null || explanation.isBlank()) return;
        String rendered = templates.render(explanation, values);
        for (var diagram : model.diagrams()) {
            if (!(diagram instanceof CoordinateGraphDiagramSpecV1 graph)) continue;
            for (CoordinatePointSpec point : graph.points()) {
                String x = canonical(values.get(point.xKey()));
                String y = canonical(values.get(point.yKey()));
                if (!x.isBlank() && !y.isBlank()
                        && Pattern.compile("\\(" + Pattern.quote(x) + "\\s*,\\s*" + Pattern.quote(y) + "\\)")
                        .matcher(rendered.replaceAll("\\s+", "")).find()) {
                    violations.add("presentation.explanationTemplate: 그림의 점 좌표를 직접 재인용하지 말고 그래프를 읽는 절차로 설명하세요.");
                    return;
                }
            }
        }
    }

    private void inspect(String path, String template, Map<String, SemanticResolvedValue> values,
                         String target, String correctChoice, List<String> violations) {
        if (template == null || template.isBlank()) {
            return;
        }
        String rendered = normalize(templates.render(template, values));
        if (!rendered.isBlank() && (!correctChoice.isBlank() && (rendered.equals(correctChoice) || rendered.contains(correctChoice)))) {
            violations.add(path + ": 그림 라벨이 정답 보기 내용을 직접 노출합니다. 정답을 추론할 수 있는 관찰 정보만 남기고 라벨을 비워 주세요.");
        } else if (isFormula(target) && !target.isBlank() && (rendered.equals(target) || rendered.contains(target))) {
            violations.add(path + ": 그림 라벨이 target 식을 직접 노출합니다. 정답 식을 라벨에 넣지 마세요.");
        }
    }

    private String correctChoiceValue(ProblemSemanticModelV1 model, Map<String, SemanticResolvedValue> values, String target) {
        if (target.isBlank() || model.presentation().choices() == null) {
            return "";
        }
        for (SemanticChoiceTemplate choice : model.presentation().choices()) {
            String value = canonical(values.get(choice.valueKey()));
            if (target.equals(value)) {
                return normalize(templates.render(choice.contentTemplate(), values));
            }
        }
        return "";
    }

    private String canonical(SemanticResolvedValue value) {
        return value == null ? "" : normalize(value.canonicalValue());
    }

    private boolean isFormula(String value) {
        return value.matches(".*[a-zA-Z].*[=+\\-*/].*") || value.contains("\\frac");
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace("$", "")
                .replace("\\(", "").replace("\\)", "")
                .replaceAll("\\\\frac\\s*\\{([^{}]+)}\\s*\\{([^{}]+)}", "($1)/($2)")
                .replaceAll("\\s+", "")
                .toLowerCase();
    }
}
