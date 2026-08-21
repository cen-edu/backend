package com.cenedu.backend.domain.problem.authoring.edit.semantic;

import java.util.regex.Pattern;

public final class ProblemSemanticPatchPath {
    private static final Pattern PARAM = Pattern.compile("/parameters/[A-Z][A-Z0-9_]{0,63}/(value|unit)");
    private static final Pattern ALLOWED = Pattern.compile("/presentation/(questionTemplate|explanationTemplate|learningGuide/(conceptTitleTemplate|summaryTemplate|keyPointTemplates/[0-9]+)|choices/[A-Z][A-Z0-9_]*/contentTemplate|steps/[A-Z][A-Z0-9_]*/labelTemplate|steps/[A-Z][A-Z0-9_]*/segments/[0-9]+/textTemplate|rubrics/[A-Z][A-Z0-9_]*/criterionTemplate)|/diagrams/[A-Z][A-Z0-9_]*/style/(strokeColor|fillColor|accentColor|strokeWidth|fontSize)|/diagrams/[A-Z][A-Z0-9_]*/labels/[A-Z][A-Z0-9_]*");

    private ProblemSemanticPatchPath() {
    }

    public static boolean isParameter(String path) {
        return path != null && PARAM.matcher(path).matches();
    }

    public static boolean isPresentational(String path) {
        return path != null && ALLOWED.matcher(path).matches();
    }

    public static boolean isStructural(String path) {
        return path != null && (path.startsWith("/intent/") || path.startsWith("/curriculum/") || path.startsWith("/computations") || path.startsWith("/constraints") || path.startsWith("/assertions"));
    }

    public static boolean isAllowed(String path) {
        return isParameter(path) || isPresentational(path);
    }
}
