package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

/** resolved 도식에서 학생이 실제로 볼 수 있는 정보만 접근성 설명으로 만든다. */
public final class SemanticVisualDescriptionFactory {
    private final SemanticTemplateEngine templates = new SemanticTemplateEngine();

    public Map<String, String> create(List<DiagramSpecV1> specs,
                                      Map<String, SemanticResolvedValue> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (DiagramSpecV1 spec : specs == null ? List.<DiagramSpecV1>of() : specs) {
            result.put(spec.assetKey(), describe(spec, values == null ? Map.of() : values));
        }
        return Map.copyOf(result);
    }

    public String describe(DiagramSpecV1 spec, Map<String, SemanticResolvedValue> values) {
        if (spec instanceof CoordinateGraphDiagramSpecV1 graph) return coordinateGraph(graph, values);
        if (spec instanceof DataTableDiagramSpecV1 table) return dataTable(table, values);
        return "문항에 포함된 " + spec.kind().name() + " 도식";
    }

    private String coordinateGraph(CoordinateGraphDiagramSpecV1 graph,
                                   Map<String, SemanticResolvedValue> values) {
        List<String> parts = new ArrayList<>();
        parts.add("x축과 y축이 있는 좌표평면");
        for (CoordinateFunctionSpec function : graph.functions()) {
            String shape = function.functionKind() == CoordinateFunctionKind.INVERSE_PROPORTION
                    ? "두 갈래로 나뉜 곡선" : "원점을 지나는 직선";
            parts.add(shape);
            String label = renderLabel(function.labelTemplate(), values);
            if (!label.isBlank()) {
                parts.add("그림에 표시된 라벨 " + label);
            }
        }
        for (CoordinatePointSpec point : graph.points()) {
            parts.add("점 (" + value(values, point.xKey()) + ", " + value(values, point.yKey()) + ")");
        }
        for (CoordinateLineSpec line : graph.lines()) {
            parts.add("두 점을 잇는 " + renderLabel(line.labelTemplate(), values) + " 직선");
        }
        for (CoordinateSegmentSpec segment : graph.segments()) {
            parts.add("두 점을 잇는 " + renderLabel(segment.labelTemplate(), values) + " 선분");
        }
        return String.join(". ", parts) + ".";
    }

    private String dataTable(DataTableDiagramSpecV1 table,
                             Map<String, SemanticResolvedValue> values) {
        List<String> parts = new ArrayList<>();
        parts.add("행과 열로 구성된 표");
        if (!table.columnHeaderTemplates().isEmpty()) {
            parts.add("열 제목: " + renderAll(table.columnHeaderTemplates(), values));
        }
        if (!table.rowHeaderTemplates().isEmpty()) {
            parts.add("행 제목: " + renderAll(table.rowHeaderTemplates(), values));
        }
        for (TableCellSpec cell : table.cells()) {
            String value = cell.valueKey() == null
                    ? renderLabel(cell.textTemplate(), values)
                    : value(values, cell.valueKey());
            parts.add("(" + cell.row() + "," + cell.column() + ") 셀은 " + value);
        }
        return String.join(". ", parts) + ".";
    }

    public String renderLabel(String template, Map<String, SemanticResolvedValue> values) {
        if (template == null || template.isBlank()) return "";
        return templates.render(template, values);
    }

    private String renderAll(List<String> templates, Map<String, SemanticResolvedValue> values) {
        return String.join(", ", templates.stream().map(t -> renderLabel(t, values)).toList());
    }

    private String value(Map<String, SemanticResolvedValue> values, String key) {
        if (key == null || key.isBlank()) return "값 없음";
        SemanticResolvedValue resolved = values.get(key);
        return resolved == null ? key : resolved.canonicalValue();
    }
}
