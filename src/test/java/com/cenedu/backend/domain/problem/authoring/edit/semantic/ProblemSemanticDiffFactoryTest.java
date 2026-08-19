package com.cenedu.backend.domain.problem.authoring.edit.semantic;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
class ProblemSemanticDiffFactoryTest {
    @Test void same_model은_영향영역이_없다(){
        var model=model(); var diff=new ProblemSemanticDiffFactory().create(model,model,SemanticEditMode.PARAMETRIC_PATCH);
        assertThat(diff.parameterChanges()).isEmpty(); assertThat(diff.impactedAreas()).isEmpty();
    }
    @Test void parameter_change는_answer와_revalidation을_표시한다(){
        var base=model(); var changedParameter=new SemanticParameter("A",SemanticValueType.INTEGER,"4",null,true,null);
        var changed=new ProblemSemanticModelV1(1,base.curriculum(),base.intent(),List.of(changedParameter),base.computations(),base.constraints(),base.presentation(),base.diagrams(),base.assertions());
        var diff=new ProblemSemanticDiffFactory().create(base,changed,SemanticEditMode.PARAMETRIC_PATCH);
        assertThat(diff.impactedAreas()).contains(SemanticImpactArea.ANSWERS);
        assertThat(diff.revalidationRequired()).isTrue();
        assertThat(diff.impactedAreas()).contains(SemanticImpactArea.STEM);
    }
    @Test void non_editable_parameter는_answer_free_diff에서_제외한다(){
        var base=modelWithEditable(false);
        var locked=new SemanticParameter("A",SemanticValueType.INTEGER,"4",null,false,null);
        var changed=new ProblemSemanticModelV1(1,base.curriculum(),base.intent(),List.of(locked),base.computations(),base.constraints(),base.presentation(),base.diagrams(),base.assertions());
        var diff=new ProblemSemanticDiffFactory().create(base,changed,SemanticEditMode.PARAMETRIC_PATCH);
        assertThat(diff.parameterChanges()).isEmpty();
    }
    private ProblemSemanticModelV1 model(){var p=new SemanticParameter("A",SemanticValueType.INTEGER,"3",null,true,null);var c=new SemanticComputation("C",SemanticOperation.IDENTITY,List.of("A"),null,null,"3");var i=new SemanticProblemIntent(QuestionType.SHORT_INPUT,"mid",null,"identity","C",1,false);var v=new SemanticPresentationPlan("${A}",List.of(),List.of(),"${C}",null,List.of());return new ProblemSemanticModelV1(1,new CurriculumScope("2022_REVISED","MIDDLE",1,1,null,1L,"a","b","c"),i,List.of(p),List.of(c),List.of(),v,List.of(),List.of());}
    private ProblemSemanticModelV1 modelWithEditable(boolean editable){var base=model();var p=new SemanticParameter("A",SemanticValueType.INTEGER,"3",null,editable,null);return new ProblemSemanticModelV1(1,base.curriculum(),base.intent(),List.of(p),base.computations(),base.constraints(),base.presentation(),base.diagrams(),base.assertions());}
}
