package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

public final class DataTableSvgRenderer {
    public String render(DataTableDiagramSpecV1 s) {
        return render(s, Map.of());
    }

    public String render(DataTableDiagramSpecV1 s, Map<String, SemanticResolvedValue> values) {
        int p = s.viewport().padding(), w = s.viewport().width() - 2 * p, h = s.viewport().height() - 2 * p, cols = Math.max(1, s.columnHeaderTemplates().size()), rows = Math.max(1, s.rowHeaderTemplates().size()), cw = w / cols, rh = h / rows;
        validate(s, rows, cols);
        var b = new StringBuilder("<rect x=\"").append(p).append("\" y=\"").append(p).append("\" width=\"").append(w).append("\" height=\"").append(h).append("\" fill=\"#FFFFFF\" stroke=\"#000000\"/>");
        for (int i = 1; i < cols; i++)
            b.append("<line x1=\"").append(p + i * cw).append("\" y1=\"").append(p).append("\" x2=\"").append(p + i * cw).append("\" y2=\"").append(p + h).append("\" stroke=\"#000000\"/>");
        for (int i = 1; i < rows; i++)
            b.append("<line x1=\"").append(p).append("\" y1=\"").append(p + i * rh).append("\" x2=\"").append(p + w).append("\" y2=\"").append(p + i * rh).append("\" stroke=\"#000000\"/>");
        for (int i = 0; i < s.columnHeaderTemplates().size(); i++)
            b.append("<text x=\"").append(p + i * cw + 4).append("\" y=\"").append(p + 14).append("\">").append(escape(s.columnHeaderTemplates().get(i))).append("</text>");
        for (int i = 0; i < s.rowHeaderTemplates().size(); i++)
            b.append("<text x=\"").append(p + 4).append("\" y=\"").append(p + i * rh + 14).append("\">").append(escape(s.rowHeaderTemplates().get(i))).append("</text>");
        for (var c : s.cells()) {
            String t = c.valueKey() != null && values.containsKey(c.valueKey()) ? values.get(c.valueKey()).canonicalValue() : c.textTemplate();
            b.append("<text x=\"").append(p + c.column() * cw + 4).append("\" y=\"").append(p + c.row() * rh + 14).append("\">").append(escape(t)).append("</text>");
        }
        for (var c : s.highlightedCells())
            b.append("<rect x=\"").append(p + c.column() * cw).append("\" y=\"").append(p + c.row() * rh).append("\" width=\"").append(cw).append("\" height=\"").append(rh).append("\" fill=\"none\" stroke=\"#FF0000\"/>");
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
}
