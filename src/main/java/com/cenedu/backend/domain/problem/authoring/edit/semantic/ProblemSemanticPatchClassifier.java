package com.cenedu.backend.domain.problem.authoring.edit.semantic;
public class ProblemSemanticPatchClassifier {
    public SemanticEditMode classify(ProblemSemanticPatch patch) {
        if (patch == null || patch.mode() == null) return SemanticEditMode.REJECTED;
        if (patch.mode() == SemanticEditMode.STRUCTURAL_REGENERATION || patch.mode() == SemanticEditMode.RESTORE || patch.mode() == SemanticEditMode.REJECTED)
            return patch.operations().isEmpty() ? patch.mode() : SemanticEditMode.REJECTED;
        boolean parameter=false, presentation=false;
        for (var op: patch.operations()) {
            if (op == null || !ProblemSemanticPatchPath.isAllowed(op.path())) return SemanticEditMode.STRUCTURAL_REGENERATION;
            if (op.type()==SemanticPatchOperationType.SET_PARAMETER_VALUE || op.type()==SemanticPatchOperationType.SET_PARAMETER_UNIT) parameter=true;
            else if (op.type()==SemanticPatchOperationType.SET_TEMPLATE_TEXT || op.type()==SemanticPatchOperationType.SET_DIAGRAM_STYLE || op.type()==SemanticPatchOperationType.SET_LABEL_TEXT) presentation=true;
        }
        if (parameter && presentation) return SemanticEditMode.REJECTED;
        if (parameter) return SemanticEditMode.PARAMETRIC_PATCH;
        if (presentation) return SemanticEditMode.PRESENTATIONAL_PATCH;
        return SemanticEditMode.REJECTED;
    }
    public SemanticEditMode classifyRequestedPath(String path) { return ProblemSemanticPatchPath.isStructural(path) || !ProblemSemanticPatchPath.isAllowed(path) ? SemanticEditMode.STRUCTURAL_REGENERATION : ProblemSemanticPatchPath.isParameter(path) ? SemanticEditMode.PARAMETRIC_PATCH : SemanticEditMode.PRESENTATIONAL_PATCH; }
}
