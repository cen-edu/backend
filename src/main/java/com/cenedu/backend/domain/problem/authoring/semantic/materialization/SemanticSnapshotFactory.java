package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.*;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * semantic model과 계산된 값으로 문항 Snapshot을 만든다.
 *
 * <p>{@code SnapshotStructuralValidator}가 요구하는 논리 키 형식(ST1·B1·R1·C1)과 displayOrder
 * 연속성은 model이 아니라 여기서 부여한다. model의 키는 LLM이 정하는 자유 문자열이고 patch
 * path가 그 키를 참조하므로, 그대로 내보내면 검증에 걸리거나 키가 버전마다 흔들린다.
 *
 * <p>구성이 유형 규칙을 만족하지 못하면 빈 값을 내보내지 않고 즉시 실패한다. 이 예외 메시지는
 * {@code ProblemSemanticExtractionService}가 UNSUPPORTED로 분류해 저장하므로, 지원할 수 없는
 * 문항은 매 수정 턴마다 추출을 재시도하지 않고 곧바로 재생성 경로로 넘어간다.
 */
public final class SemanticSnapshotFactory {

    private static final int MAX_STEPS = 4;
    private static final int MAX_BLANKS = 8;
    private static final int MIN_RUBRICS = 2;
    private static final int MAX_RUBRICS = 5;

    public QuestionSnapshotV1 create(ProblemSemanticModelV1 m, Map<String, SemanticResolvedValue> v) {
        var e = m.intent();
        var p = m.presentation();
        var type = e.questionType();
        // 유형이 쓰지 않는 영역은 model에 값이 남아 있어도 내보내지 않는다.
        // 구조 검증이 "객관식에 steps가 있으면 위반"처럼 배타를 요구하기 때문이다.
        var choices = type == QuestionType.MULTIPLE_CHOICE ? choices(p, v) : List.<SnapshotChoice>of();
        var steps = type == QuestionType.STEP_FILL ? steps(p, v) : List.<SnapshotStep>of();
        var rubrics = type == QuestionType.ESSAY ? rubrics(p, v) : List.<SnapshotRubricItem>of();
        var answers = switch (type) {
            case MULTIPLE_CHOICE -> List.of(choiceAnswer(e, p, v));
            case SHORT_INPUT -> List.of(shortInputAnswer(e, v));
            case STEP_FILL -> blankAnswers(p, v);
            // RUBRIC 채점 단위는 정답 문자열을 갖지 않는다. 값을 채우면 구조 검증이
            // "RUBRIC은 answerRaw가 null이어야 한다"로 막는다.
            case ESSAY -> List.of(new SnapshotAnswerUnit(
                    "MAIN", null, 0, null, null, CompareMethod.RUBRIC, null, null));
        };
        QuestionPresentation presentation = m.diagrams().isEmpty()
                ? QuestionPresentation.TEXT_ONLY
                : m.diagrams().stream().anyMatch(d -> d.kind() == com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind.DATA_TABLE)
                ? QuestionPresentation.WITH_TABLE : QuestionPresentation.WITH_FIGURE;
        var meta = new SnapshotMetadata(type, presentation, e.difficulty(), m.curriculum().subUnitId(), null, e.evaluationArea(), null);
        SnapshotLearningGuide guide = p.learningGuide() == null
                ? new SnapshotLearningGuide(m.curriculum().subUnitName(),
                m.curriculum().subUnitName() + "의 핵심 개념을 확인하고 문제의 조건과 관계를 해석한다.",
                List.of("문제에서 주어진 양과 조건을 확인한다."))
                : new SnapshotLearningGuide(render(p.learningGuide().conceptTitleTemplate(), v),
                render(p.learningGuide().summaryTemplate(), v),
                p.learningGuide().keyPointTemplates().stream().map(x -> render(x, v)).toList());
        return new QuestionSnapshotV1(1, meta,
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0, render(p.questionTemplate(), v), null, null)),
                List.of(), choices, steps, answers, render(p.explanationTemplate(), v), guide, rubrics);
    }

    private List<SnapshotChoice> choices(SemanticPresentationPlan p, Map<String, SemanticResolvedValue> v) {
        var ordered = ordered(p.choices(), SemanticChoiceTemplate::displayOrder);
        return java.util.stream.IntStream.range(0, ordered.size())
                .mapToObj(index -> new SnapshotChoice("C" + (index + 1), index,
                        render(ordered.get(index).contentTemplate(), v)))
                .toList();
    }

    private SnapshotAnswerUnit choiceAnswer(SemanticProblemIntent e, SemanticPresentationPlan p,
            Map<String, SemanticResolvedValue> v) {
        var ordered = ordered(p.choices(), SemanticChoiceTemplate::displayOrder);
        var target = requireValue(v, e.targetKey(), "객관식 target");
        var matches = ordered.stream()
                .filter(choice -> target.canonicalValue().equals(
                        requireValue(v, choice.valueKey(), "choice " + choice.choiceKey()).canonicalValue()))
                .toList();
        if (matches.size() != 1) {
            throw new SemanticMaterializationException("객관식 target과 일치하는 choice는 정확히 1개여야 합니다.");
        }
        return new SnapshotAnswerUnit("MAIN", null, 0,
                "C" + (ordered.indexOf(matches.get(0)) + 1), null, CompareMethod.CHOICE, null, null);
    }

    private SnapshotAnswerUnit shortInputAnswer(SemanticProblemIntent e, Map<String, SemanticResolvedValue> v) {
        var resolved = requireValue(v, e.targetKey(), "short input target");
        return new SnapshotAnswerUnit("MAIN", null, 0, resolved.canonicalValue(),
                resolved.canonicalValue(), CompareMethod.VALUE, null, resolved.unit());
    }

    /** 단계 순서와 세그먼트 구성을 그대로 두고 논리 키만 ST1·B1 형식으로 정규화한다. */
    private List<SnapshotStep> steps(SemanticPresentationPlan p, Map<String, SemanticResolvedValue> v) {
        var ordered = orderedSteps(p);
        Map<String, String> blankKeys = blankKeys(ordered);
        var result = new ArrayList<SnapshotStep>();
        for (int index = 0; index < ordered.size(); index++) {
            var template = ordered.get(index);
            var segments = new ArrayList<SnapshotSegment>();
            for (var segment : template.segments()) {
                segments.add(switch (segment.type()) {
                    case TEXT -> new SnapshotSegment(SnapshotSegmentType.TEXT,
                            render(segment.textTemplate(), v), null);
                    case BLANK -> new SnapshotSegment(SnapshotSegmentType.BLANK, null,
                            blankKey(blankKeys, segment));
                    case ANSWER_REF -> new SnapshotSegment(SnapshotSegmentType.ANSWER_REF, null,
                            blankKey(blankKeys, segment));
                });
            }
            if (segments.isEmpty()) {
                throw unsupported("빈칸형 단계에는 세그먼트가 최소 1개 필요합니다.");
            }
            result.add(new SnapshotStep("ST" + (index + 1), index,
                    render(template.labelTemplate(), v), List.copyOf(segments)));
        }
        return List.copyOf(result);
    }

    /** BLANK가 등장하는 순서대로 답안 단위를 만들고 자신이 속한 단계 키를 붙인다. */
    private List<SnapshotAnswerUnit> blankAnswers(SemanticPresentationPlan p,
            Map<String, SemanticResolvedValue> v) {
        var ordered = orderedSteps(p);
        Map<String, String> blankKeys = blankKeys(ordered);
        var units = new ArrayList<SnapshotAnswerUnit>();
        for (int index = 0; index < ordered.size(); index++) {
            for (var segment : ordered.get(index).segments()) {
                if (segment.type() != SemanticSegmentType.BLANK) continue;
                var resolved = requireValue(v, segment.valueKey(), "빈칸 " + segment.unitKey());
                units.add(new SnapshotAnswerUnit(blankKey(blankKeys, segment), "ST" + (index + 1),
                        units.size(), resolved.canonicalValue(), resolved.canonicalValue(),
                        blankCompareMethod(segment), blankDiagnosticType(segment),
                        blankDisplayUnit(segment, resolved, v)));
            }
        }
        return List.copyOf(units);
    }

    private List<SemanticStepTemplate> orderedSteps(SemanticPresentationPlan p) {
        var ordered = ordered(p.steps(), SemanticStepTemplate::displayOrder);
        if (ordered.isEmpty() || ordered.size() > MAX_STEPS) {
            throw unsupported("빈칸형 단계는 1개 이상 " + MAX_STEPS + "개 이하여야 합니다.");
        }
        return ordered;
    }

    /**
     * model이 정한 빈칸 키를 등장 순서대로 B1, B2로 다시 매긴다.
     *
     * <p>ANSWER_REF가 같은 매핑을 통해 BLANK를 가리키므로 참조 무결성이 유지된다.
     */
    private Map<String, String> blankKeys(List<SemanticStepTemplate> orderedSteps) {
        var keys = new LinkedHashMap<String, String>();
        for (var step : orderedSteps) {
            for (var segment : step.segments()) {
                if (segment.type() == SemanticSegmentType.BLANK && segment.unitKey() != null) {
                    keys.putIfAbsent(segment.unitKey(), "B" + (keys.size() + 1));
                }
            }
        }
        if (keys.isEmpty() || keys.size() > MAX_BLANKS) {
            throw unsupported("빈칸형 빈칸은 1개 이상 " + MAX_BLANKS + "개 이하여야 합니다.");
        }
        return keys;
    }

    private String blankKey(Map<String, String> blankKeys, SemanticSegmentTemplate segment) {
        String key = segment.unitKey() == null ? null : blankKeys.get(segment.unitKey());
        if (key == null) {
            throw unsupported("빈칸 참조 " + segment.unitKey() + "에 해당하는 BLANK가 없습니다.");
        }
        return key;
    }

    private CompareMethod blankCompareMethod(SemanticSegmentTemplate segment) {
        CompareMethod method = segment.compareMethod() == null ? CompareMethod.VALUE : segment.compareMethod();
        if (method != CompareMethod.VALUE && method != CompareMethod.EXACT
                && method != CompareMethod.SET && method != CompareMethod.SUBST) {
            throw unsupported("빈칸형 채점 방법은 VALUE·EXACT·SET·SUBST만 허용합니다.");
        }
        return method;
    }

    private com.cenedu.backend.domain.problem.entity.enums.DiagnosticType blankDiagnosticType(
            SemanticSegmentTemplate segment) {
        if (segment.diagnosticType() == null) {
            throw unsupported("빈칸형 답안 단위에는 diagnosticType이 필요합니다.");
        }
        return segment.diagnosticType();
    }

    /** 표시 단위는 template이 있으면 렌더하고, 없으면 계산된 값의 단위를 쓴다. */
    private String blankDisplayUnit(SemanticSegmentTemplate segment, SemanticResolvedValue resolved,
            Map<String, SemanticResolvedValue> v) {
        String rendered = segment.displayUnitTemplate() == null
                ? resolved.unit() : render(segment.displayUnitTemplate(), v);
        return rendered == null || rendered.isBlank() ? null : rendered;
    }

    /**
     * 서술형 채점 기준을 R1 형식 키로 정규화한다.
     *
     * <p>개수와 배점 합을 여기서 막는 이유는, 채점 기준이 없거나 배점이 맞지 않는 model로
     * 문항을 만들면 채점 자체가 성립하지 않기 때문이다. 특히 원본에 채점 기준이 없는 문항을
     * 추출할 때 LLM이 그럴듯한 기준을 지어내면, 원본에 없던 채점 기준이 수정 결과에 조용히
     * 생겨난다. 이 검사와 extraction의 구조 일치 검사가 함께 그것을 막는다.
     */
    private List<SnapshotRubricItem> rubrics(SemanticPresentationPlan p, Map<String, SemanticResolvedValue> v) {
        var ordered = ordered(p.rubrics(), SemanticRubricTemplate::displayOrder);
        if (ordered.size() < MIN_RUBRICS || ordered.size() > MAX_RUBRICS) {
            throw unsupported("서술형 채점 기준은 " + MIN_RUBRICS + "개 이상 " + MAX_RUBRICS + "개 이하여야 합니다.");
        }
        int weightSum = ordered.stream().mapToInt(SemanticRubricTemplate::weightPercent).sum();
        if (weightSum != 100) {
            throw unsupported("서술형 채점 기준 배점의 합은 100이어야 합니다.");
        }
        return java.util.stream.IntStream.range(0, ordered.size())
                .mapToObj(index -> new SnapshotRubricItem("R" + (index + 1), index,
                        render(ordered.get(index).criterionTemplate(), v),
                        ordered.get(index).weightPercent()))
                .toList();
    }

    /** displayOrder로 정렬하되 값이 겹치면 model의 목록 순서를 유지한다. */
    private <T> List<T> ordered(List<T> items, ToIntFunction<T> displayOrder) {
        if (items == null || items.isEmpty()) return List.of();
        var copy = new ArrayList<>(items);
        copy.sort(Comparator.comparingInt(displayOrder));
        return List.copyOf(copy);
    }

    private SemanticResolvedValue requireValue(Map<String, SemanticResolvedValue> values, String key, String label) {
        SemanticResolvedValue value = key == null ? null : values.get(key);
        if (value == null) throw new SemanticMaterializationException(label + " resolved value가 없습니다.");
        return value;
    }

    /** 추출 경계가 UNSUPPORTED로 분류하도록 "지원하지" 문구를 포함한 예외를 만든다. */
    private SemanticMaterializationException unsupported(String reason) {
        return new SemanticMaterializationException("지원하지 않는 문항 구성입니다: " + reason);
    }

    private String render(String s, Map<String, SemanticResolvedValue> v) {
        return new SemanticTemplateEngine().render(s, v);
    }
}
