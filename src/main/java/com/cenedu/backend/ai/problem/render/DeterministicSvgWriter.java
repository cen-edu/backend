package com.cenedu.backend.ai.problem.render;

public final class DeterministicSvgWriter {
    private DeterministicSvgWriter() {
    }

    public static String empty(int w, int h, String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + w + " " + h + "\">" + body + "</svg>";
    }
}
