package com.cenedu.backend.domain.problem.authoring.diagram;

import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

public final class DiagramSpecValidator {
    public void validate(DiagramSpecV1 s, Map<String, SemanticResolvedValue> v) {
        if (s == null || s.schemaVersion() != 1) throw new DiagramValidationException("schemaVersion");
        if (s.assetKey() == null || !s.assetKey().matches("[A-Z][A-Z0-9_]{0,63}"))
            throw new DiagramValidationException("assetKey");
        if (s.viewport() == null || s.viewport().width() < 240 || s.viewport().width() > 1200 || s.viewport().height() < 120 || s.viewport().height() > 900 || s.viewport().padding() < 8 || s.viewport().padding() > 96)
            throw new DiagramValidationException("viewport");
        var st = s.style();
        if (st == null || !color(st.strokeColor()) || !color(st.fillColor()) || !color(st.accentColor()) || st.strokeWidth() < 1 || st.strokeWidth() > 8 || !"sans-serif".equals(st.fontFamily()) || st.fontSize() < 10 || st.fontSize() > 32)
            throw new DiagramValidationException("style");
        if (s instanceof DataTableDiagramSpecV1 t) validateTable(t);
        labels(s);
    }

    public void validateAll(List<DiagramSpecV1> specs, Map<String, SemanticResolvedValue> v) {
        var keys = new HashSet<String>();
        for (var s : specs) {
            validate(s, v);
            if (!keys.add(s.assetKey())) throw new DiagramValidationException("duplicate assetKey");
        }
    }

    private void validateTable(DataTableDiagramSpecV1 t) {
        int rows = t.rowHeaderTemplates().size(), cols = t.columnHeaderTemplates().size();
        if (rows < 1 || cols < 1 || rows > 12 || cols > 12) throw new DiagramValidationException("table dimensions");
        var seen = new HashSet<String>();
        for (var c : t.cells()) {
            if (c.row() < 0 || c.row() >= rows || c.column() < 0 || c.column() >= cols || !seen.add(c.row() + ":" + c.column()))
                throw new DiagramValidationException("table cell coordinates");
        }
        for (var c : t.highlightedCells())
            if (c.row() < 0 || c.row() >= rows || c.column() < 0 || c.column() >= cols)
                throw new DiagramValidationException("highlight coordinates");
    }

    private boolean color(String s) {
        return s != null && s.matches("#[0-9A-F]{6}");
    }

    private void labels(DiagramSpecV1 s) {
        var all = new ArrayList<String>();
        if (s instanceof NumberLineDiagramSpecV1 n) n.points().forEach(x -> all.add(x.labelTemplate()));
        if (s instanceof CoordinateGraphDiagramSpecV1 n) n.points().forEach(x -> all.add(x.labelTemplate()));
        if (s instanceof PlaneGeometryDiagramSpecV1 n) n.points().forEach(x -> all.add(x.labelTemplate()));
        if (s instanceof DataTableDiagramSpecV1 n) {
            all.addAll(n.rowHeaderTemplates());
            all.addAll(n.columnHeaderTemplates());
            n.cells().forEach(x -> all.add(x.textTemplate()));
        }
        for (var x : all)
            if (x != null && (x.codePoints().count() > 80 || x.contains("<script") || x.contains("http://") || x.contains("https://") || x.contains("${")))
                throw new DiagramValidationException("unsafe label");
    }
}
