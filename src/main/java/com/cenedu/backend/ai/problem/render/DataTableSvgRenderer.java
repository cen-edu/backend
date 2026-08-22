package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.SemanticTemplateEngine;

import java.util.*;

public final class DataTableSvgRenderer {
    private final SemanticTemplateEngine templates = new SemanticTemplateEngine();
    public String render(DataTableDiagramSpecV1 s) {
        return render(s, Map.of());
    }

    public String render(DataTableDiagramSpecV1 s, Map<String, SemanticResolvedValue> values) {
        int p = s.viewport().padding(), w = s.viewport().width() - 2 * p, h = s.viewport().height() - 2 * p, cols = Math.max(1, s.columnHeaderTemplates().size()), rows = Math.max(1, s.rowHeaderTemplates().size()), cw = w / cols, rh = h / rows;
        validate(s, rows, cols);
        DiagramStyle style = s.style();
        String stroke = style == null || style.strokeColor() == null ? "#000000" : style.strokeColor();
        String fill = style == null || style.fillColor() == null ? "#FFFFFF" : style.fillColor();
        String accent = style == null || style.accentColor() == null ? "#FF0000" : style.accentColor();
        int strokeWidth = style == null ? 1 : Math.max(1, style.strokeWidth());
        int fontSize = style == null ? 12 : Math.max(10, style.fontSize());
        var b = new StringBuilder("<rect x=\"").append(p).append("\" y=\"").append(p).append("\" width=\"").append(w).append("\" height=\"").append(h).append("\" fill=\"").append(fill).append("\" stroke=\"").append(stroke).append("\" stroke-width=\"").append(strokeWidth).append("\"/>");
        for (int i = 1; i < cols; i++)
            b.append("<line x1=\"").append(p + i * cw).append("\" y1=\"").append(p).append("\" x2=\"").append(p + i * cw).append("\" y2=\"").append(p + h).append("\" stroke=\"").append(stroke).append("\"/>");
        for (int i = 1; i < rows; i++)
            b.append("<line x1=\"").append(p).append("\" y1=\"").append(p + i * rh).append("\" x2=\"").append(p + w).append("\" y2=\"").append(p + i * rh).append("\" stroke=\"").append(stroke).append("\"/>");
        for (int i = 0; i < s.columnHeaderTemplates().size(); i++)
            b.append(text(p + i * cw + 4, p + fontSize + 2, render(s.columnHeaderTemplates().get(i), values), stroke, fontSize));
        for (int i = 0; i < s.rowHeaderTemplates().size(); i++)
            b.append(text(p + 4, p + i * rh + fontSize + 2, render(s.rowHeaderTemplates().get(i), values), stroke, fontSize));
        for (var c : s.cells()) {
            if (c.valueKey() != null && !values.containsKey(c.valueKey())) throw new IllegalArgumentException("table resolved value가 없습니다.");
            String t = c.valueKey() != null ? values.get(c.valueKey()).canonicalValue() : render(c.textTemplate(), values);
            b.append(text(p + c.column() * cw + 4, p + c.row() * rh + fontSize + 2, t, stroke, fontSize));
        }
        for (var c : s.highlightedCells())
            b.append("<rect x=\"").append(p + c.column() * cw).append("\" y=\"").append(p + c.row() * rh).append("\" width=\"").append(cw).append("\" height=\"").append(rh).append("\" fill=\"none\" stroke=\"").append(accent).append("\"/>");
        return b.toString();
    }

    private void validate(DataTableDiagramSpecV1 s, int rows, int cols) {
        for (var c : s.cells())
            if (c.row() < 0 || c.row() >= rows || c.column() < 0 || c.column() >= cols)
                throw new IllegalArgumentException("table cell 좌표가 범위를 벗어났습니다.");
        for (var c : s.highlightedCells())
            if (c.row() < 0 || c.row() >= rows || c.column() < 0 || c.column() >= cols)
                throw new IllegalArgumentException("highlight 좌표가 범위를 벗어났습니다.");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String render(String template, Map<String, SemanticResolvedValue> values) {
        return template == null ? "" : templates.render(template, values);
    }

    private String text(int x, int y, String value, String color, int fontSize) {
        return "<text x=\"" + x + "\" y=\"" + y + "\" fill=\"" + color + "\" font-size=\"" + fontSize + "\">"
                + escape(value) + "</text>";
    }
}
