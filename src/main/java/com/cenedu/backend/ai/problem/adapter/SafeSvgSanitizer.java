package com.cenedu.backend.ai.problem.adapter;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** 임시 SVG에 실행 코드나 외부 자원 참조가 들어가는 것을 저장 전에 차단한다. */
@Component
public class SafeSvgSanitizer {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "<(?:script|foreignObject|iframe|object|embed|image|use)\\b|\\bon[a-z]+\\s*=|(?:href|src|style)\\s*=|javascript:|url\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("</?([A-Za-z][A-Za-z0-9]*)\\b");

    /** 제한된 SVG 요소만 포함된 문서를 반환한다. */
    public String sanitize(String svg) {
        if (svg == null || !svg.stripLeading().startsWith("<svg") || FORBIDDEN.matcher(svg).find()) {
            throw new IllegalArgumentException("안전하지 않은 SVG입니다.");
        }
        var tags = TAG.matcher(svg);
        var allowed = java.util.Set.of("svg","g","line","rect","circle","path","polyline","polygon","text","tspan","defs","marker");
        while (tags.find()) if (!allowed.contains(tags.group(1))) throw new IllegalArgumentException("허용되지 않은 SVG 요소입니다.");
        return svg;
    }
}
