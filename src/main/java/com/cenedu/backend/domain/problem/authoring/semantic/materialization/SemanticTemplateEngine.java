package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;
import java.util.regex.*;

public final class SemanticTemplateEngine {
    /** LaTeX의 달러 구분자와 충돌하지 않는 semantic placeholder 문법. */
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([A-Z][A-Z0-9_]*?)(_UNIT)?\\}\\}");
    private static final Pattern LEGACY_TOKEN = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*(?:_UNIT)?}");

    public String render(String template, Map<String, SemanticResolvedValue> values) {
        if (template == null) return null;
        if (LEGACY_TOKEN.matcher(template).find()) {
            throw new SemanticMaterializationException("legacy placeholder 문법(${KEY})은 사용할 수 없습니다. {{KEY}}를 사용하세요.");
        }
        var m = TOKEN.matcher(template);
        var b = new StringBuffer();
        while (m.find()) {
            var token = m.group(1);
            var unit = m.group(2) != null;
            var v = values.get(token);
            if (v == null) throw new SemanticMaterializationException("unknown placeholder: " + token);
            m.appendReplacement(b, Matcher.quoteReplacement(unit ? Objects.requireNonNullElse(v.unit(), "") : v.canonicalValue()));
        }
        m.appendTail(b);
        if (b.indexOf("{{") >= 0 || b.indexOf("}}") >= 0) throw new SemanticMaterializationException("unresolved placeholder");
        return b.toString();
    }
}
