package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

public final class NumberLineSvgRenderer {
    public String render(NumberLineDiagramSpecV1 s) {
        return render(s, Map.of());
    }

    public String render(NumberLineDiagramSpecV1 s, Map<String, SemanticResolvedValue> values) {
        int p = s.viewport().padding(), end = s.viewport().width() - p, y = s.viewport().height() / 2;
        double min = num(values, s.minKey(), 0), max = num(values, s.maxKey(), 10);
        var b = new StringBuilder("<line x1=\"").append(p).append("\" y1=\"").append(y).append("\" x2=\"").append(end).append("\" y2=\"").append(y).append("\" stroke=\"#000000\"/>");
        if (s.startArrow())
            b.append("<path d=\"M ").append(p).append(" ").append(y).append(" l 8 -5 M ").append(p).append(" ").append(y).append(" l 8 5\" stroke=\"#000000\"/>");
        if (s.endArrow())
            b.append("<path d=\"M ").append(end).append(" ").append(y).append(" l -8 -5 M ").append(end).append(" ").append(y).append(" l -8 5\" stroke=\"#000000\"/>");
        double tick = num(values, s.tickIntervalKey(), 0);
        if (tick > 0) for (double n = min; n <= max; n += tick) {
            int q = pos(n, min, max, p, end);
            b.append("<line x1=\"").append(q).append("\" y1=\"").append(y - 5).append("\" x2=\"").append(q).append("\" y2=\"").append(y + 5).append("\" stroke=\"#000000\"/>");
        }
        var occupied = new ArrayList<int[]>();
        for (var point : s.points()) {
            int q = pos(num(values, point.positionKey(), min), min, max, p, end);
            if (point.marker() == PointMarker.CROSS)
                b.append("<path d=\"M ").append(q - 5).append(" ").append(y - 5).append(" L ").append(q + 5).append(" ").append(y + 5).append(" M ").append(q + 5).append(" ").append(y - 5).append(" L ").append(q - 5).append(" ").append(y + 5).append("\" stroke=\"#000000\"/>");
            else
                b.append("<circle cx=\"").append(q).append("\" cy=\"").append(y).append("\" r=\"5\" fill=\"").append(point.marker() == PointMarker.CLOSED_CIRCLE ? "#000000" : "#FFFFFF").append("\" stroke=\"#000000\"/>");
            if (point.labelTemplate() != null) {
                int w = Math.max(12, point.labelTemplate().length() * 7);
                int[] a = new DeterministicLabelLayout().place(q, y, s.viewport().width(), s.viewport().height(), w, 14, occupied);
                occupied.add(new int[]{a[0], a[1] - 14, a[0] + w, a[1]});
                b.append("<text x=\"").append(a[0]).append("\" y=\"").append(a[1]).append("\">").append(escape(point.labelTemplate())).append("</text>");
            }
        }
        for (var in : s.intervals()) {
            int a = pos(num(values, in.startKey(), min), min, max, p, end), c = pos(num(values, in.endKey(), max), min, max, p, end);
            b.append("<line x1=\"").append(a).append("\" y1=\"").append(y).append("\" x2=\"").append(c).append("\" y2=\"").append(y).append("\" stroke=\"#FF0000\" stroke-width=\"2\"/>");
            b.append("<circle cx=\"").append(a).append("\" cy=\"").append(y).append("\" r=\"4\" fill=\"").append(in.includeStart() ? "#FF0000" : "#FFFFFF").append("\" stroke=\"#FF0000\"/><circle cx=\"").append(c).append("\" cy=\"").append(y).append("\" r=\"4\" fill=\"").append(in.includeEnd() ? "#FF0000" : "#FFFFFF").append("\" stroke=\"#FF0000\"/>");
        }
        return b.toString();
    }

    private int pos(double n, double min, double max, int p, int end) {
        return (int) (p + (n - min) / (max == min ? 1 : max - min) * (end - p));
    }

    private double num(Map<String, SemanticResolvedValue> v, String k, double d) {
        try {
            return Double.parseDouble(v.get(k).canonicalValue());
        } catch (Exception e) {
            return d;
        }
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
