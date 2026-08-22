package com.cenedu.backend.domain.problem.authoring.diagram;

import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

public final class DiagramSpecValidator {
    public void validate(DiagramSpecV1 s, Map<String, SemanticResolvedValue> v) {
        if (v == null) throw new DiagramValidationException("resolved values");
        if (s == null || s.schemaVersion() != 1) throw new DiagramValidationException("schemaVersion");
        if (s.assetKey() == null || !s.assetKey().matches("[A-Z][A-Z0-9_]{0,63}"))
            throw new DiagramValidationException("assetKey");
        if (s.viewport() == null || s.viewport().width() < 240 || s.viewport().width() > 1200 || s.viewport().height() < 120 || s.viewport().height() > 900 || s.viewport().padding() < 8 || s.viewport().padding() > 96)
            throw new DiagramValidationException("viewport");
        var st = s.style();
        if (st == null) throw new DiagramValidationException("style is missing");
        if (!color(st.strokeColor())) throw new DiagramValidationException("style.strokeColor must be #RRGGBB");
        if (!color(st.fillColor())) throw new DiagramValidationException("style.fillColor must be #RRGGBB");
        if (!color(st.accentColor())) throw new DiagramValidationException("style.accentColor must be #RRGGBB");
        if (st.strokeWidth() < 1 || st.strokeWidth() > 8) throw new DiagramValidationException("style.strokeWidth must be 1..8");
        if (!"sans-serif".equals(st.fontFamily())) throw new DiagramValidationException("style.fontFamily must be sans-serif");
        if (st.fontSize() < 10 || st.fontSize() > 32) throw new DiagramValidationException("style.fontSize must be 10..32");
        if (s instanceof NumberLineDiagramSpecV1 n) validateNumberLine(n, v);
        if (s instanceof CoordinateGraphDiagramSpecV1 n) validateCoordinateGraph(n, v);
        if (s instanceof DataTableDiagramSpecV1 t) validateTable(t, v);
        if (s instanceof PlaneGeometryDiagramSpecV1 p) validatePlane(p, v);
        if (s instanceof SolidGeometryDiagramSpecV1 g) validateSolid(g, v);
        labels(s);
    }

    public void validateAll(List<DiagramSpecV1> specs, Map<String, SemanticResolvedValue> v) {
        var keys = new HashSet<String>();
        for (var s : specs) {
            validate(s, v);
            if (!keys.add(s.assetKey())) throw new DiagramValidationException("duplicate assetKey");
        }
    }

    private void validateNumberLine(NumberLineDiagramSpecV1 n, Map<String, SemanticResolvedValue> v) {
        double min = number(v, n.minKey()), max = number(v, n.maxKey()), tick = number(v, n.tickIntervalKey());
        if (!(min < max) || !(tick > 0)) throw new DiagramValidationException("number line range");
        n.points().forEach(p -> numberInRange(v, p.positionKey(), min, max));
        n.intervals().forEach(i -> {
            double start = number(v, i.startKey()), end = number(v, i.endKey());
            if (start < min || end > max || start > end) throw new DiagramValidationException("number line interval");
        });
    }

    private void validateCoordinateGraph(CoordinateGraphDiagramSpecV1 n, Map<String, SemanticResolvedValue> v) {
        double xMin = number(v, n.xMinKey()), xMax = number(v, n.xMaxKey());
        double yMin = number(v, n.yMinKey()), yMax = number(v, n.yMaxKey());
        if (!(xMin < xMax) || !(yMin < yMax) || !(optionalPositive(v, n.xTickKey())) || !(optionalPositive(v, n.yTickKey()))) {
            throw new DiagramValidationException("coordinate graph range");
        }
        var points = new HashSet<String>();
        n.points().forEach(p -> {
            if (p.pointKey() == null || !points.add(p.pointKey())) throw new DiagramValidationException("coordinate point");
            double x = number(v, p.xKey()), y = number(v, p.yKey());
            if (x < xMin || x > xMax || y < yMin || y > yMax) throw new DiagramValidationException("coordinate point range");
        });
        n.segments().forEach(s -> {
            if (!points.contains(s.startPointKey()) || !points.contains(s.endPointKey())) throw new DiagramValidationException("coordinate segment reference");
        });
        n.functions().forEach(f -> {
            if (f.functionKind() == null) throw new DiagramValidationException("coordinate function kind");
            number(v, f.coefficientKey());
        });
    }

    private void validateTable(DataTableDiagramSpecV1 t, Map<String, SemanticResolvedValue> v) {
        int rows = t.rowHeaderTemplates().size(), cols = t.columnHeaderTemplates().size();
        if (rows < 1 || cols < 1 || rows > 12 || cols > 12) throw new DiagramValidationException("table dimensions");
        var seen = new HashSet<String>();
        for (var c : t.cells()) {
            if (c.row() < 0 || c.row() >= rows || c.column() < 0 || c.column() >= cols || !seen.add(c.row() + ":" + c.column()))
                throw new DiagramValidationException("table cell coordinates");
            if (c.valueKey() == null && (c.textTemplate() == null || c.textTemplate().isBlank()))
                throw new DiagramValidationException("table cell value");
            if (c.valueKey() != null) require(v, c.valueKey());
        }
        for (var c : t.highlightedCells())
            if (c.row() < 0 || c.row() >= rows || c.column() < 0 || c.column() >= cols)
                throw new DiagramValidationException("highlight coordinates");
    }

    private void validatePlane(PlaneGeometryDiagramSpecV1 p, Map<String, SemanticResolvedValue> v) {
        var points = new HashSet<String>();
        p.points().forEach(point -> {
            if (point.pointKey() == null || !points.add(point.pointKey())) throw new DiagramValidationException("plane point");
            number(v, point.xKey());
            number(v, point.yKey());
        });
        p.segments().forEach(segment -> {
            if (!points.contains(segment.startPointKey()) || !points.contains(segment.endPointKey())) throw new DiagramValidationException("plane segment reference");
        });
        p.polygons().forEach(polygon -> {
            if (polygon.pointKeys().size() < 3 || polygon.pointKeys().stream().anyMatch(key -> !points.contains(key))) throw new DiagramValidationException("plane polygon");
        });
        p.circles().forEach(circle -> {
            if (!points.contains(circle.centerPointKey()) || number(v, circle.radiusKey()) <= 0) throw new DiagramValidationException("plane circle");
        });
        p.arcs().forEach(arc -> {
            if (!points.contains(arc.centerPointKey()) || number(v, arc.radiusKey()) <= 0) throw new DiagramValidationException("plane arc");
            number(v, arc.startAngleKey());
            number(v, arc.endAngleKey());
        });
        p.angles().forEach(angle -> {
            if (!points.contains(angle.vertexPointKey()) || !points.contains(angle.startPointKey()) || !points.contains(angle.endPointKey())) throw new DiagramValidationException("plane angle reference");
            number(v, angle.angleValueKey());
        });
        p.measurements().forEach(measurement -> {
            if (measurement.targetKey() == null) throw new DiagramValidationException("plane measurement target");
            number(v, measurement.valueKey());
        });
    }

    private void validateSolid(SolidGeometryDiagramSpecV1 s, Map<String, SemanticResolvedValue> v) {
        if (s.solidKind() == null) throw new DiagramValidationException("solid kind");
        switch (s.solidKind()) {
            case RECTANGULAR_PRISM, PRISM -> {
                positive(v, s.widthKey());
                positive(v, s.depthKey());
                positive(v, s.heightKey());
            }
            case PYRAMID, CONE -> {
                positive(v, s.widthKey());
                positive(v, s.depthKey());
                positive(v, s.heightKey());
                positive(v, s.slantHeightKey());
            }
            case CYLINDER -> {
                positive(v, s.radiusKey());
                positive(v, s.heightKey());
            }
            case SPHERE -> positive(v, s.radiusKey());
        }
        if (s.polygonSides() != null && (s.polygonSides() < 3 || s.polygonSides() > 12)) throw new DiagramValidationException("solid polygon sides");
        s.labels().forEach(label -> { if (label.valueKey() != null) require(v, label.valueKey()); });
    }

    private void numberInRange(Map<String, SemanticResolvedValue> v, String key, double min, double max) {
        double value = number(v, key);
        if (value < min || value > max) throw new DiagramValidationException("number line point range");
    }

    private void positive(Map<String, SemanticResolvedValue> v, String key) {
        if (!(number(v, key) > 0)) throw new DiagramValidationException("positive resolved value");
    }

    private boolean optionalPositive(Map<String, SemanticResolvedValue> v, String key) {
        return key == null || number(v, key) > 0;
    }

    private void require(Map<String, SemanticResolvedValue> v, String key) {
        if (key == null || !v.containsKey(key) || v.get(key) == null) throw new DiagramValidationException("missing resolved value");
    }

    private double number(Map<String, SemanticResolvedValue> v, String key) {
        require(v, key);
        try { return Double.parseDouble(v.get(key).canonicalValue()); }
        catch (RuntimeException exception) { throw new DiagramValidationException("numeric resolved value"); }
    }

    private boolean color(String s) {
        return s != null && s.matches("#[0-9A-Fa-f]{6}");
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
