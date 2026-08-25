package com.cenedu.backend.domain.problem.authoring.edit.semantic;

public class ProblemSemanticPatchClassifier {
    public SemanticEditMode classify(ProblemSemanticPatch patch) {
        if (patch == null || patch.mode() == null) return SemanticEditMode.REJECTED;
        if (patch.mode() == SemanticEditMode.STRUCTURAL_REGENERATION || patch.mode() == SemanticEditMode.RESTORE || patch.mode() == SemanticEditMode.REJECTED)
            return patch.operations().isEmpty() ? patch.mode() : SemanticEditMode.REJECTED;
        boolean parameter = false, presentation = false, choiceOrder = false;
        for (var op : patch.operations()) {
            if (op == null || !ProblemSemanticPatchPath.isAllowed(op.path()) || !typeMatches(op))
                return SemanticEditMode.STRUCTURAL_REGENERATION;
            if (op.type() == SemanticPatchOperationType.SET_PARAMETER_VALUE || op.type() == SemanticPatchOperationType.SET_PARAMETER_UNIT)
                parameter = true;
            else if (op.type() == SemanticPatchOperationType.SET_CHOICE_ORDER)
                choiceOrder = true;
            else if (op.type() == SemanticPatchOperationType.SET_TEMPLATE_TEXT || op.type() == SemanticPatchOperationType.SET_DIAGRAM_STYLE || op.type() == SemanticPatchOperationType.SET_LABEL_TEXT)
                presentation = true;
        }
        // 순서 변경은 정답이 가리키는 보기 키를 바꾸고, 값·표현 수정은 바꾸지 않는다.
        // 섞이면 어느 불변식으로 검증해야 할지 정할 수 없으므로 거부한다.
        if ((parameter ? 1 : 0) + (presentation ? 1 : 0) + (choiceOrder ? 1 : 0) > 1)
            return SemanticEditMode.REJECTED;
        if (parameter) return SemanticEditMode.PARAMETRIC_PATCH;
        if (choiceOrder) return SemanticEditMode.CHOICE_REORDER;
        if (presentation) return SemanticEditMode.PRESENTATIONAL_PATCH;
        return SemanticEditMode.REJECTED;
    }

    private boolean typeMatches(SemanticPatchOperation op) {
        if (ProblemSemanticPatchPath.isParameter(op.path()))
            return op.type() == SemanticPatchOperationType.SET_PARAMETER_VALUE || op.type() == SemanticPatchOperationType.SET_PARAMETER_UNIT;
        if (ProblemSemanticPatchPath.isChoiceOrder(op.path()))
            return op.type() == SemanticPatchOperationType.SET_CHOICE_ORDER;
        if (op.path().contains("/style/")) return op.type() == SemanticPatchOperationType.SET_DIAGRAM_STYLE;
        if (op.path().contains("/labels/")) return op.type() == SemanticPatchOperationType.SET_LABEL_TEXT;
        return op.type() == SemanticPatchOperationType.SET_TEMPLATE_TEXT;
    }

    public SemanticEditMode classifyRequestedPath(String path) {
        if (ProblemSemanticPatchPath.isStructural(path) || !ProblemSemanticPatchPath.isAllowed(path))
            return SemanticEditMode.STRUCTURAL_REGENERATION;
        if (ProblemSemanticPatchPath.isParameter(path)) return SemanticEditMode.PARAMETRIC_PATCH;
        if (ProblemSemanticPatchPath.isChoiceOrder(path)) return SemanticEditMode.CHOICE_REORDER;
        return SemanticEditMode.PRESENTATIONAL_PATCH;
    }
}
