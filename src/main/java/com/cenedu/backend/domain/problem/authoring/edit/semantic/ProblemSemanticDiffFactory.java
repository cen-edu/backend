package com.cenedu.backend.domain.problem.authoring.edit.semantic;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import java.util.*;
public class ProblemSemanticDiffFactory {
    public ProblemSemanticDiff create(ProblemSemanticModelV1 base, ProblemSemanticModelV1 changed, SemanticEditMode mode) {
        Map<String,com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticParameter> after=new HashMap<>(); changed.parameters().forEach(p->after.put(p.key(),p));
        List<SemanticValueChange> changes=new ArrayList<>(); for(var p:base.parameters()){var q=after.get(p.key());if(q!=null&&(!Objects.equals(p.value(),q.value())||!Objects.equals(p.unit(),q.unit())))changes.add(new SemanticValueChange(p.key(),p.value(),q.value(),p.unit(),q.unit()));}
        EnumSet<SemanticImpactArea> areas=EnumSet.noneOf(SemanticImpactArea.class);
        if (!changes.isEmpty()) { areas.add(SemanticImpactArea.ANSWERS); areas.add(SemanticImpactArea.EXPLANATION); }
        if(!Objects.equals(base.presentation().questionTemplate(),changed.presentation().questionTemplate())) areas.add(SemanticImpactArea.STEM);
        if(!Objects.equals(base.presentation().choices(),changed.presentation().choices())) areas.add(SemanticImpactArea.CHOICES);
        if(!Objects.equals(base.presentation().steps(),changed.presentation().steps())) areas.add(SemanticImpactArea.STEPS);
        if(!Objects.equals(base.presentation().explanationTemplate(),changed.presentation().explanationTemplate())) areas.add(SemanticImpactArea.EXPLANATION);
        if(!Objects.equals(base.presentation().learningGuide(),changed.presentation().learningGuide())) areas.add(SemanticImpactArea.LEARNING_GUIDE);
        if(!Objects.equals(base.presentation().rubrics(),changed.presentation().rubrics())) areas.add(SemanticImpactArea.RUBRICS);
        if(!Objects.equals(base.diagrams(),changed.diagrams())) areas.add(SemanticImpactArea.ASSETS);
        boolean structural = mode==SemanticEditMode.STRUCTURAL_REGENERATION
                || !Objects.equals(base.intent(), changed.intent())
                || !Objects.equals(base.curriculum(), changed.curriculum())
                || !Objects.equals(base.computations(), changed.computations())
                || !Objects.equals(base.parameters().stream().map(x->x.key()).toList(), changed.parameters().stream().map(x->x.key()).toList())
                || !Objects.equals(base.diagrams().stream().map(x->x.kind()).toList(), changed.diagrams().stream().map(x->x.kind()).toList());
        boolean revalidation = structural || mode==SemanticEditMode.PARAMETRIC_PATCH || areas.contains(SemanticImpactArea.ASSETS) || areas.contains(SemanticImpactArea.ANSWERS);
        return new ProblemSemanticDiff(changes,areas,structural,revalidation);
    }
}
