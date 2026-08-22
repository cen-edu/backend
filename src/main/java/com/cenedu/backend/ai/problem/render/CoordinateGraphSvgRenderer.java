package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.SemanticTemplateEngine;

import java.util.*;

/** resolved 값과 학생에게 보이는 좌표 그래프를 동일한 구조로 렌더링한다. */
public final class CoordinateGraphSvgRenderer {
    private final SemanticTemplateEngine templates = new SemanticTemplateEngine();

    public String render(CoordinateGraphDiagramSpecV1 spec) { return render(spec, Map.of()); }

    public String render(CoordinateGraphDiagramSpecV1 spec, Map<String, SemanticResolvedValue> values) {
        int p = spec.viewport().padding(), w = spec.viewport().width(), h = spec.viewport().height();
        double xmin = number(values, spec.xMinKey(), -10), xmax = number(values, spec.xMaxKey(), 10);
        double ymin = number(values, spec.yMinKey(), -10), ymax = number(values, spec.yMaxKey(), 10);
        int axisY = py(0, ymin, ymax, p, h - p), axisX = px(0, xmin, xmax, p, w - p);
        DiagramStyle style = spec.style();
        String stroke = color(style == null ? null : style.strokeColor(), "#000000");
        String accent = color(style == null ? null : style.accentColor(), stroke);
        String fill = color(style == null ? null : style.fillColor(), "#FFFFFF");
        int width = style == null ? 1 : Math.max(1, style.strokeWidth());
        int fontSize = style == null ? 12 : Math.max(10, style.fontSize());
        String font = style == null || style.fontFamily() == null ? "sans-serif" : escape(style.fontFamily());
        StringBuilder svg = new StringBuilder("<rect x=\"0\" y=\"0\" width=\"").append(w)
                .append("\" height=\"").append(h).append("\" fill=\"").append(fill).append("\"/>")
                .append("<g font-family=\"").append(font).append("\" font-size=\"").append(fontSize).append("\">")
                .append(line(p, axisY, w - p, axisY, stroke, width))
                .append(line(axisX, p, axisX, h - p, stroke, width))
                .append(text(w - p - 10, axisY - 8, "x", stroke))
                .append(text(axisX + 8, p + fontSize, "y", stroke))
                .append(text(axisX + 5, axisY - 5, "O", stroke));
        double xt = number(values, spec.xTickKey(), Math.max((xmax - xmin) / 10.0, 1));
        double yt = number(values, spec.yTickKey(), Math.max((ymax - ymin) / 10.0, 1));
        if (xt > 0) for (double x = Math.ceil(xmin / xt) * xt; x <= xmax; x += xt) {
            int q = px(x, xmin, xmax, p, w - p);
            svg.append(line(q, axisY - 3, q, axisY + 3, stroke, width));
            if (Math.abs(x) > 1e-9) svg.append(text(q - 5, axisY + fontSize + 2, numberText(x), stroke));
        }
        if (yt > 0) for (double y = Math.ceil(ymin / yt) * yt; y <= ymax; y += yt) {
            int q = py(y, ymin, ymax, p, h - p);
            svg.append(line(axisX - 3, q, axisX + 3, q, stroke, width));
            if (Math.abs(y) > 1e-9) svg.append(text(axisX + 6, q + 4, numberText(y), stroke));
        }
        for (CoordinateFunctionSpec function : spec.functions()) {
            double k = number(values, function.coefficientKey(), 1);
            if (function.functionKind() == CoordinateFunctionKind.INVERSE_PROPORTION) {
                svg.append(path(function, p, w, h, xmin, xmax, ymin, ymax, k, Double.NEGATIVE_INFINITY, 0, stroke, width));
                svg.append(path(function, p, w, h, xmin, xmax, ymin, ymax, k, 0, Double.POSITIVE_INFINITY, stroke, width));
            } else svg.append(path(function, p, w, h, xmin, xmax, ymin, ymax, k, xmin, xmax, stroke, width));
            String label = renderLabel(function.labelTemplate(), values);
            if (!label.isBlank()) svg.append(text(w - p - 100, p + fontSize, label, accent));
        }
        for (CoordinateSegmentSpec segment : spec.segments()) {
            CoordinatePointSpec a = point(spec.points(), segment.startPointKey()), b = point(spec.points(), segment.endPointKey());
            if (a != null && b != null) {
                svg.append(connect(a, b, values, xmin, xmax, ymin, ymax, p, w, h, accent, width));
                svg.append(pointLabel(segment.labelTemplate(), a, values, xmin, xmax, ymin, ymax, p, w, h, accent));
            }
        }
        for (CoordinateLineSpec line : spec.lines()) {
            CoordinatePointSpec a = point(spec.points(), line.pointAKey()), b = point(spec.points(), line.pointBKey());
            if (a != null && b != null) {
                svg.append(connect(a, b, values, xmin, xmax, ymin, ymax, p, w, h, accent, width));
                svg.append(pointLabel(line.labelTemplate(), a, values, xmin, xmax, ymin, ymax, p, w, h, accent));
            }
        }
        for (CoordinatePointSpec point : spec.points()) {
            int x = px(number(values, point.xKey(), 0), xmin, xmax, p, w - p);
            int y = py(number(values, point.yKey(), 0), ymin, ymax, p, h - p);
            svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"4\" fill=\"")
                    .append(point.marker() == PointMarker.OPEN_CIRCLE ? fill : accent).append("\" stroke=\"").append(accent).append("\"/>");
            String label = renderLabel(point.labelTemplate(), values);
            if (!label.isBlank()) svg.append(text(x + 6, y - 6, label, accent));
        }
        return svg.append("</g>").toString();
    }

    private String connect(CoordinatePointSpec a, CoordinatePointSpec b, Map<String, SemanticResolvedValue> v,
                           double xmin, double xmax, double ymin, double ymax, int p, int w, int h, String color, int width) {
        return line(px(number(v, a.xKey(), 0), xmin, xmax, p, w - p), py(number(v, a.yKey(), 0), ymin, ymax, p, h - p),
                px(number(v, b.xKey(), 0), xmin, xmax, p, w - p), py(number(v, b.yKey(), 0), ymin, ymax, p, h - p), color, width);
    }

    private String pointLabel(String template, CoordinatePointSpec point, Map<String, SemanticResolvedValue> v,
                              double xmin, double xmax, double ymin, double ymax, int p, int w, int h, String color) {
        String label = renderLabel(template, v);
        return label.isBlank() ? "" : text(px(number(v, point.xKey(), 0), xmin, xmax, p, w - p) + 6,
                py(number(v, point.yKey(), 0), ymin, ymax, p, h - p) - 6, label, color);
    }

    private String path(CoordinateFunctionSpec f, int p, int w, int h, double xmin, double xmax, double ymin, double ymax,
                        double k, double from, double to, String color, int width) {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i <= 64; i++) {
            double x = from == Double.NEGATIVE_INFINITY ? xmin + (0 - xmin) * i / 64.0
                    : to == Double.POSITIVE_INFINITY ? 0 + xmax * i / 64.0 : from + (to - from) * i / 64.0;
            if (x == 0 && f.functionKind() == CoordinateFunctionKind.INVERSE_PROPORTION) continue;
            double y = f.functionKind() == CoordinateFunctionKind.INVERSE_PROPORTION ? k / x : k * x;
            if (!Double.isFinite(y) || y < ymin || y > ymax) continue;
            d.append(d.isEmpty() ? "M " : " L ").append(px(x, xmin, xmax, p, w - p)).append(' ').append(py(y, ymin, ymax, p, h - p));
        }
        return d.isEmpty() ? "" : "<path d=\"" + d + "\" fill=\"none\" stroke=\"" + color + "\" stroke-width=\"" + width + "\"/>";
    }

    private CoordinatePointSpec point(List<CoordinatePointSpec> points, String key) { return points.stream().filter(p -> Objects.equals(p.pointKey(), key)).findFirst().orElse(null); }
    /** 식 형태의 라벨은 상위 의미 검증을 우회한 경우에도 최종 SVG에 노출하지 않는다. */
    private String renderLabel(String template, Map<String, SemanticResolvedValue> v) {
        if (template == null || template.isBlank()) return "";
        String rendered = templates.render(template, v);
        return isAnswerLikeFormula(rendered) ? "" : rendered;
    }
    private boolean isAnswerLikeFormula(String label) {
        String normalized = label == null ? "" : label.replace("$", "").replaceAll("\\s+", "").toLowerCase();
        return normalized.matches(".*[xy]=.*[a-z].*") || normalized.matches(".*[xy]=.*[0-9].*[xy].*");
    }
    private double number(Map<String, SemanticResolvedValue> v, String key, double fallback) {
        if (key == null) return fallback;
        SemanticResolvedValue value = v.get(key);
        if (value == null) throw new IllegalArgumentException("coordinate graph resolved value가 없습니다: " + key);
        try { return Double.parseDouble(value.canonicalValue()); } catch (RuntimeException e) { throw new IllegalArgumentException("coordinate graph numeric resolved value가 올바르지 않습니다: " + key, e); }
    }
    private int px(double x, double min, double max, int a, int b) { return (int) Math.round(a + (x - min) / (max - min) * (b - a)); }
    private int py(double y, double min, double max, int a, int b) { return (int) Math.round(b - (y - min) / (max - min) * (b - a)); }
    private String line(int x1, int y1, int x2, int y2, String c, int width) { return "<line x1=\"" + x1 + "\" y1=\"" + y1 + "\" x2=\"" + x2 + "\" y2=\"" + y2 + "\" stroke=\"" + c + "\" stroke-width=\"" + width + "\"/>"; }
    private String text(int x, int y, String value, String c) { return "<text x=\"" + x + "\" y=\"" + y + "\" fill=\"" + c + "\">" + escape(value) + "</text>"; }
    private String color(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String numberText(double value) { return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value); }
    private String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
