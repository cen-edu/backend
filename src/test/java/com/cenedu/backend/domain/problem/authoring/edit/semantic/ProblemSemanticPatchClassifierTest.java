package com.cenedu.backend.domain.problem.authoring.edit.semantic;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
class ProblemSemanticPatchClassifierTest {
    private final ProblemSemanticPatchClassifier classifier=new ProblemSemanticPatchClassifier();
    @Test void parameter와_structural_path를_분류한다(){
        var p=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PARAMETRIC_PATCH,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE,"/parameters/RADIUS/value","3","5")),"ok");
        assertThat(classifier.classify(p)).isEqualTo(SemanticEditMode.PARAMETRIC_PATCH);
        assertThat(classifier.classifyRequestedPath("/intent/questionType")).isEqualTo(SemanticEditMode.STRUCTURAL_REGENERATION);
    }
    @Test void mixed_patch는_rejected다(){
        var p=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.REJECTED,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE,"/parameters/A/value","1","2"),new SemanticPatchOperation(SemanticPatchOperationType.SET_TEMPLATE_TEXT,"/presentation/questionTemplate","A","B")),"no");
        assertThat(classifier.classify(p)).isEqualTo(SemanticEditMode.REJECTED);
    }
}
