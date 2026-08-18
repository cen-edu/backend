package com.cenedu.backend.ai.problem.adapter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SafeSvgSanitizerTest {
    private final SafeSvgSanitizer sanitizer = new SafeSvgSanitizer();

    @Test
    void acceptsStaticSvg() {
        assertDoesNotThrow(() -> sanitizer.sanitize(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><circle cx=\"5\" cy=\"5\" r=\"2\"/></svg>"));
    }

    @Test
    void rejectsScriptEventAndExternalReference() {
        assertThrows(IllegalArgumentException.class,
                () -> sanitizer.sanitize("<svg><script>alert(1)</script></svg>"));
        assertThrows(IllegalArgumentException.class,
                () -> sanitizer.sanitize("<svg><path onclick=\"x()\"/></svg>"));
        assertThrows(IllegalArgumentException.class,
                () -> sanitizer.sanitize("<svg><image href=\"https://example.com/x\"/></svg>"));
    }
}
