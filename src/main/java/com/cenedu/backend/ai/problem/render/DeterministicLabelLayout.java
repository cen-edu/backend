package com.cenedu.backend.ai.problem.render;

import java.util.*;

public final class DeterministicLabelLayout {
    private static final int[][] OFFSETS = {{0, -12}, {8, -8}, {8, 12}, {0, 16}, {-8, 12}, {-8, -8}, {16, 0}, {-16, 0}};

    public int[] place(int x, int y, int width, int height, int labelWidth, int labelHeight, List<int[]> occupied) {
        for (var o : OFFSETS) {
            int nx = x + o[0], ny = y + o[1];
            if (nx < 0 || ny - labelHeight < 0 || nx + labelWidth > width || ny > height) continue;
            boolean hit = false;
            for (var b : occupied)
                if (nx < b[2] && nx + labelWidth > b[0] && ny - labelHeight < b[3] && ny > b[1]) {
                    hit = true;
                    break;
                }
            if (!hit) return new int[]{nx, ny};
        }
        throw new DiagramRenderException("label collision or viewport overflow");
    }
}
