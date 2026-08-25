package com.cenedu.backend.ai.problem.adapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.ai.problem.render.ProblemDiagramRenderer;
import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.SemanticAssetPlanFactory;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 텍스트가 이미 생성된 문항에 좌표그래프 이미지를 문항 단위로 덧붙이는 생성 전략이다.
 *
 * <p>LLM에는 키·resolvedValues 같은 파라메트릭 표현이 아니라 <b>리터럴 좌표 그래프</b>(실제 숫자)만
 * 요구하고, 서버가 이를 {@link CoordinateGraphDiagramSpecV1} + resolvedValues로 변환한다. 이렇게 하면
 * 모델의 오류 표면이 크게 줄어 semantic 파이프라인의 파라메트릭 생성보다 안정적이다.
 *
 * <p>재시도 횟수와 지원 종류 선택은 {@link ProblemImageGenerationLoop}가 담당한다.
 */
@Component
public final class CoordinateGraphImageGenerator implements ProblemImageGenerator {

    private static final String ASSET_KEY = "F1";
    private static final DiagramViewport VIEWPORT = new DiagramViewport(640, 480, 24);
    private static final DiagramStyle STYLE = new DiagramStyle("#333333", "#FFFFFF", "#1F77B4", 2, "sans-serif", 14);

    static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["xMin","xMax","yMin","yMax","xTick","yTick","points","segments","lines","functions"],
             "properties":{
               "xMin":{"type":"number"},"xMax":{"type":"number"},
               "yMin":{"type":"number"},"yMax":{"type":"number"},
               "xTick":{"type":"number"},"yTick":{"type":"number"},
               "points":{"type":"array","items":{"type":"object","additionalProperties":false,
                 "required":["x","y","label","marker"],
                 "properties":{"x":{"type":"number"},"y":{"type":"number"},
                   "label":{"type":["string","null"]},
                   "marker":{"type":"string","enum":["CLOSED_CIRCLE","OPEN_CIRCLE","CROSS"]}}}},
               "segments":{"type":"array","items":{"type":"object","additionalProperties":false,
                 "required":["startPointIndex","endPointIndex","label"],
                 "properties":{"startPointIndex":{"type":"integer"},"endPointIndex":{"type":"integer"},
                   "label":{"type":["string","null"]}}}},
               "lines":{"type":"array","items":{"type":"object","additionalProperties":false,
                 "required":["pointAIndex","pointBIndex","startArrow","endArrow","label"],
                 "properties":{"pointAIndex":{"type":"integer"},"pointBIndex":{"type":"integer"},
                   "startArrow":{"type":"boolean"},"endArrow":{"type":"boolean"},"label":{"type":["string","null"]}}}},
               "functions":{"type":"array","items":{"type":"object","additionalProperties":false,
                 "required":["kind","coefficient","label"],
                 "properties":{"kind":{"type":"string","enum":["DIRECT_PROPORTION","INVERSE_PROPORTION"]},
                   "coefficient":{"type":"number"},"label":{"type":["string","null"]}}}}
             }}
            """;

    private final LlmClient client;
    private final ObjectMapper mapper;
    private final ProblemDiagramRenderer renderer = new ProblemDiagramRenderer(new SafeSvgSanitizer());
    private final DiagramSpecValidator diagramValidator = new DiagramSpecValidator();
    private final SemanticAssetPlanFactory assetPlanFactory = new SemanticAssetPlanFactory();
    private final com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator structural;

    public CoordinateGraphImageGenerator(LlmClient client, ObjectProvider<ObjectMapper> mapper,
            com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator structural) {
        this.client = client;
        this.mapper = mapper.getIfAvailable(ObjectMapper::new);
        this.structural = structural;
    }

    /** 이 전략이 생성하는 이미지 종류를 반환한다. */
    @Override
    public com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind kind() {
        return com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind.COORDINATE_GRAPH;
    }

    /** 좌표그래프를 한 번 생성·검증·부착한다. 실패 정보는 다음 이미지 생성 시도에 전달된다. */
    @Override
    public ProblemCandidateDraft generate(ProblemCandidateDraft candidate, String visualDescription,
                                          ProblemGenerationCommand command, int attempt,
                                          RuntimeException previousFailure) {
        String prompt = visiblePrompt(candidate.snapshot());
        LiteralGraph graph = requestGraph(prompt, visualDescription, attempt, previousFailure);
        CoordinateGraphDiagramSpecV1 spec = toSpec(graph);
        Map<String, SemanticResolvedValue> values = resolvedValues(graph);
        diagramValidator.validateAll(List.of(spec), values);
        renderer.render(spec, new DiagramRenderContext(values)); // 렌더 가능한지 조기 확인
        // altText는 visualDescription의 편집 지시가 아니라 최종 그래프 기하에서 조합한다.
        // 좌표·함수식·수치처럼 화면에 실제로 표시된 풀이 정보는 접근성 설명에도 그대로 보존한다.
        return attach(candidate, command, spec, values, visualDescription, factualAltText(graph));
    }

    private LiteralGraph requestGraph(String prompt, String description, int attempt, RuntimeException prior) {
        String system = """
                너는 중학교 좌표평면 그래프를 그리는 보조자다. 아래 문제와 시각 설명에 맞는 좌표그래프를
                리터럴 값(실제 숫자)으로만 출력하라. 규칙:
                - x/y 범위(xMin<xMax, yMin<yMax)는 모든 점·직선·함수가 보이도록 충분히 잡는다.
                - xTick, yTick는 양수 눈금 간격이다.
                - points의 x,y는 실제 좌표값이다. label은 A,B 같은 짧은 라벨 또는 null.
                - segments/lines의 인덱스는 points 배열의 0부터의 인덱스다.
                - functions.kind는 DIRECT_PROPORTION(정비례, y=ax) 또는 INVERSE_PROPORTION(반비례, y=a/x)이고
                  coefficient는 a값, label은 식 표기(예: "y=2x").
                - 문제가 요구하지 않는 요소는 빈 배열로 둔다. 값은 가능하면 정수로 한다.
                """;
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("PROBLEM_TEXT\n" + prompt));
        messages.add(ChatMessage.user("VISUAL_DESCRIPTION\n" + (description == null ? "" : description)));
        if (attempt > 0 && prior != null) {
            messages.add(ChatMessage.user("PREVIOUS_ERROR\n직전 시도가 실패했다. 아래 오류를 고쳐 다시 그려라:\n"
                    + truncate(prior.getMessage())));
        }
        String json = client.completeStructured(system, messages, SCHEMA).text();
        try {
            return mapper.readValue(json, LiteralGraph.class);
        } catch (Exception e) {
            throw new IllegalStateException("좌표그래프 JSON을 해석할 수 없습니다.", e);
        }
    }

    /** 리터럴 그래프를 서버 키 기반 스펙으로 변환한다. */
    private CoordinateGraphDiagramSpecV1 toSpec(LiteralGraph g) {
        List<CoordinatePointSpec> points = new ArrayList<>();
        for (int i = 0; i < g.points().size(); i++) {
            LiteralPoint p = g.points().get(i);
            points.add(new CoordinatePointSpec("P" + i, "P" + i + "_X", "P" + i + "_Y",
                    p.label() == null ? "" : p.label(), PointMarker.valueOf(p.marker())));
        }
        List<CoordinateSegmentSpec> segments = new ArrayList<>();
        for (int i = 0; i < g.segments().size(); i++) {
            LiteralSegment s = g.segments().get(i);
            segments.add(new CoordinateSegmentSpec("SEG" + i, "P" + s.startPointIndex(),
                    "P" + s.endPointIndex(), s.label()));
        }
        List<CoordinateLineSpec> lines = new ArrayList<>();
        for (int i = 0; i < g.lines().size(); i++) {
            LiteralLine l = g.lines().get(i);
            lines.add(new CoordinateLineSpec("LN" + i, "P" + l.pointAIndex(), "P" + l.pointBIndex(),
                    l.startArrow(), l.endArrow(), l.label()));
        }
        List<CoordinateFunctionSpec> functions = new ArrayList<>();
        for (int i = 0; i < g.functions().size(); i++) {
            LiteralFunction f = g.functions().get(i);
            functions.add(new CoordinateFunctionSpec("FN" + i, CoordinateFunctionKind.valueOf(f.kind()),
                    "FN" + i + "_C", f.label() == null ? "" : f.label()));
        }
        return new CoordinateGraphDiagramSpecV1(1, ASSET_KEY, DiagramKind.COORDINATE_GRAPH, VIEWPORT, STYLE,
                "X_MIN", "X_MAX", "Y_MIN", "Y_MAX", "X_TICK", "Y_TICK", points, segments, lines, functions);
    }

    private Map<String, SemanticResolvedValue> resolvedValues(LiteralGraph g) {
        Map<String, SemanticResolvedValue> values = new LinkedHashMap<>();
        put(values, "X_MIN", g.xMin()); put(values, "X_MAX", g.xMax());
        put(values, "Y_MIN", g.yMin()); put(values, "Y_MAX", g.yMax());
        put(values, "X_TICK", g.xTick()); put(values, "Y_TICK", g.yTick());
        for (int i = 0; i < g.points().size(); i++) {
            put(values, "P" + i + "_X", g.points().get(i).x());
            put(values, "P" + i + "_Y", g.points().get(i).y());
        }
        for (int i = 0; i < g.functions().size(); i++) {
            put(values, "FN" + i + "_C", g.functions().get(i).coefficient());
        }
        return values;
    }

    private void put(Map<String, SemanticResolvedValue> values, String key, BigDecimal number) {
        if (number == null) throw new IllegalStateException("좌표값이 비어 있습니다: " + key);
        BigDecimal stripped = number.stripTrailingZeros();
        boolean integral = stripped.scale() <= 0;
        values.put(key, new SemanticResolvedValue(
                integral ? SemanticValueType.INTEGER : SemanticValueType.DECIMAL,
                stripped.toPlainString(), null));
    }

    /** 검증·렌더가 끝난 스펙을 asset plan과 스냅샷(FIGURE 블록·asset)으로 후보에 부착한다. */
    private ProblemCandidateDraft attach(ProblemCandidateDraft candidate, ProblemGenerationCommand command,
            CoordinateGraphDiagramSpecV1 spec, Map<String, SemanticResolvedValue> values,
            String visualDescription, String altText) {
        var plans = assetPlanFactory.create(List.<DiagramSpecV1>of(spec), values,
                Map.of(ASSET_KEY, altText == null ? "좌표그래프" : altText));
        var plan = plans.getFirst();
        var generation = plan.specification();
        plans = List.of(new com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan(
                plan.assetKey(), plan.role(), plan.productionMode(), plan.outputFormat(), plan.altText(),
                new com.cenedu.backend.domain.problem.authoring.asset.AssetGenerationSpecification(
                        generation.schemaVersion(),
                        visualDescription == null ? generation.visualDescription() : visualDescription,
                        generation.requiredElements(), generation.forbiddenElements(), generation.renderData(),
                        generation.resolvedValues(), generation.diagramSpec())));
        QuestionSnapshotV1 base = candidate.snapshot();
        List<SnapshotContentBlock> blocks = new ArrayList<>(base.contentBlocks());
        int nextOrder = blocks.stream().mapToInt(SnapshotContentBlock::displayOrder).max().orElse(-1) + 1;
        blocks.add(new SnapshotContentBlock("CB" + (blocks.size() + 1), SnapshotBlockKind.FIGURE,
                nextOrder, null, ASSET_KEY, null));
        List<SnapshotAssetReference> assets = List.of(
                new SnapshotAssetReference(ASSET_KEY, altText == null ? "좌표그래프" : altText));
        SnapshotMetadata metadata = new SnapshotMetadata(base.metadata().questionType(),
                QuestionPresentation.WITH_FIGURE, base.metadata().difficulty(), base.metadata().subUnitId(),
                base.metadata().topicCode(), base.metadata().evaluationArea(), base.metadata().derivedFromQuestionId());
        QuestionSnapshotV1 snapshot = new QuestionSnapshotV1(base.schemaVersion(), metadata,
                List.copyOf(blocks), assets, base.choices(), base.steps(), base.answerUnits(),
                base.explanation(), base.learningGuide(), base.rubricItems());
        structural.validate(snapshot);
        return ProblemCandidateDraft.legacy(candidate.requestId(), snapshot, plans, candidate.provenance());
    }

    /**
     * 그래프 기하에서 사실만 뽑아 접근성 altText를 만든다. 좌표축 범위·눈금, 점의 좌표와 라벨,
     * 선분·직선·정비례/반비례 곡선 등 화면에 실제로 보이는 것을 기술한다. 풀이에 결정적인 좌표나
     * 함수식도 그림에 표시되어 있다면 생략하지 않는다.
     */
    private String factualAltText(LiteralGraph g) {
        StringBuilder sb = new StringBuilder();
        sb.append("좌표평면. x축 ").append(num(g.xMin())).append("부터 ").append(num(g.xMax()))
          .append(", y축 ").append(num(g.yMin())).append("부터 ").append(num(g.yMax()))
          .append(", 눈금 간격 x=").append(num(g.xTick())).append(" y=").append(num(g.yTick())).append(".");
        for (LiteralPoint p : g.points()) {
            sb.append(" 점 ");
            if (p.label() != null && !p.label().isBlank()) sb.append(p.label()).append(' ');
            sb.append('(').append(num(p.x())).append(", ").append(num(p.y())).append(").");
        }
        for (LiteralSegment s : g.segments()) {
            sb.append(" 두 점을 잇는 선분.");
        }
        for (LiteralLine ln : g.lines()) {
            sb.append(" 두 점을 지나는 직선.");
        }
        for (LiteralFunction f : g.functions()) {
            boolean inverse = "INVERSE_PROPORTION".equals(f.kind());
            sb.append(inverse ? " 반비례 곡선 y=" + num(f.coefficient()) + "/x."
                              : " 정비례 직선 y=" + num(f.coefficient()) + "x.");
        }
        return sb.toString();
    }

    /** BigDecimal을 불필요한 0 없이 사람이 읽는 문자열로 만든다(예: 6.0 → 6). */
    private static String num(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private static String visiblePrompt(QuestionSnapshotV1 snapshot) {
        return snapshot.contentBlocks().stream()
                .filter(b -> b.blockKind() == SnapshotBlockKind.TEXT && b.text() != null)
                .map(SnapshotContentBlock::text)
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LiteralGraph(BigDecimal xMin, BigDecimal xMax, BigDecimal yMin, BigDecimal yMax,
                        BigDecimal xTick, BigDecimal yTick, List<LiteralPoint> points,
                        List<LiteralSegment> segments, List<LiteralLine> lines, List<LiteralFunction> functions) {
        LiteralGraph {
            points = points == null ? List.of() : List.copyOf(points);
            segments = segments == null ? List.of() : List.copyOf(segments);
            lines = lines == null ? List.of() : List.copyOf(lines);
            functions = functions == null ? List.of() : List.copyOf(functions);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LiteralPoint(BigDecimal x, BigDecimal y, String label, String marker) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LiteralSegment(int startPointIndex, int endPointIndex, String label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LiteralLine(int pointAIndex, int pointBIndex, boolean startArrow, boolean endArrow, String label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LiteralFunction(String kind, BigDecimal coefficient, String label) {}
}
