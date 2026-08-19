package com.cenedu.backend.domain.problem.authoring.edit.semantic;
import static org.assertj.core.api.Assertions.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.*;
class ProblemSemanticPatchApplierTest {
    @Test void stale_expected_value는_부분적용하지_않는다(){
        var model=model(); var patch=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PARAMETRIC_PATCH,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE,"/parameters/A/value","9","5")),"x");
        assertThatThrownBy(()->new ProblemSemanticPatchApplier().apply(model,patch)).isInstanceOf(SemanticPatchConflictException.class);
        assertThat(model.parameters().getFirst().value()).isEqualTo("3");
    }
    @Test void expectedOldValue_null도_실제값과_엄격히_비교한다(){
        var model=model(); var patch=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PARAMETRIC_PATCH,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE,"/parameters/A/value",null,"5")),"x");
        assertThatThrownBy(()->new ProblemSemanticPatchApplier().apply(model,patch))
                .isInstanceOf(SemanticPatchConflictException.class);
    }
    @Test void editable_parameter를_copy_on_write로_적용한다(){
        var model=model(); var patch=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PARAMETRIC_PATCH,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE,"/parameters/A/value","3","5")),"x");
        var changed=new ProblemSemanticPatchApplier().apply(model,patch);
        assertThat(changed.parameters().getFirst().value()).isEqualTo("5"); assertThat(model.parameters().getFirst().value()).isEqualTo("3");
    }
    @Test void unit_patch도_적용한다(){
        var model=model(); var patch=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PARAMETRIC_PATCH,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_UNIT,"/parameters/A/unit",null,"cm")),"x");
        assertThat(new ProblemSemanticPatchApplier().apply(model,patch).parameters().getFirst().unit()).isEqualTo("cm");
    }
    @Test void editable이_아닌_parameter의_unit도_거부한다(){
        var model=modelWithEditable(false);
        var patch=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PARAMETRIC_PATCH,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_UNIT,"/parameters/A/unit",null,"cm")),"x");
        assertThatThrownBy(()->new ProblemSemanticPatchApplier().apply(model,patch))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("editable");
    }
    @Test void presentation_patch는_normalized_semantic_value를_변경하면_거부한다(){
        ProblemSemanticMaterializer materializer=model -> new MaterializedProblem(null,List.of(),
                new SemanticMaterializationReport(1,List.of(),Map.of("P",model.presentation().questionTemplate()),Set.of(),Set.of()));
        var patch=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PRESENTATIONAL_PATCH,List.of(
                new SemanticPatchOperation(SemanticPatchOperationType.SET_TEMPLATE_TEXT,"/presentation/questionTemplate","${A}","${A} changed")),"x");
        assertThatThrownBy(()->new ProblemSemanticPatchApplier(new ProblemSemanticPatchClassifier(),materializer).apply(model(),patch))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("normalized semantic value");
    }
    @Test void template_placeholder가_바뀌면_거부한다(){
        var model=model(); var patch=new ProblemSemanticPatch(1,UUID.randomUUID(),1L,SemanticEditMode.PRESENTATIONAL_PATCH,List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_TEMPLATE_TEXT,"/presentation/questionTemplate","${A}","${B}")),"x");
        assertThatThrownBy(()->new ProblemSemanticPatchApplier().apply(model,patch)).isInstanceOf(IllegalArgumentException.class);
    }
    private ProblemSemanticModelV1 model(){var p=new SemanticParameter("A",SemanticValueType.INTEGER,"3",null,true,null);var c=new SemanticComputation("C",SemanticOperation.IDENTITY,List.of("A"),null,null,"3");var i=new SemanticProblemIntent(QuestionType.SHORT_INPUT,"mid",null,"identity","C",1,false);var v=new SemanticPresentationPlan("${A}",List.of(),List.of(),"${C}",null,List.of());return new ProblemSemanticModelV1(1,new CurriculumScope("2022_REVISED","MIDDLE",1,1,null,1L,"a","b","c"),i,List.of(p),List.of(c),List.of(),v,List.of(),List.of());}
    private ProblemSemanticModelV1 modelWithEditable(boolean editable){var p=new SemanticParameter("A",SemanticValueType.INTEGER,"3",null,editable,null);var base=model();return new ProblemSemanticModelV1(1,base.curriculum(),base.intent(),List.of(p),base.computations(),base.constraints(),base.presentation(),base.diagrams(),base.assertions());}
}
