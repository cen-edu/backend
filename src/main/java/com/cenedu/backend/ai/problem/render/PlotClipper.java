package com.cenedu.backend.ai.problem.render;

public final class PlotClipper {
    private PlotClipper() {
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static boolean inside(double x, double y, double minX, double maxX, double minY, double maxY) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
