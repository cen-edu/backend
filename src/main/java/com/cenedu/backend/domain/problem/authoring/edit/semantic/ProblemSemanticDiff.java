package com.cenedu.backend.domain.problem.authoring.edit.semantic;
import java.util.*;
public record ProblemSemanticDiff(List<SemanticValueChange> parameterChanges, Set<SemanticImpactArea> impactedAreas, boolean structuralChange, boolean revalidationRequired) {
    public ProblemSemanticDiff { parameterChanges=parameterChanges==null?List.of():List.copyOf(parameterChanges); impactedAreas=impactedAreas==null?Set.of():Set.copyOf(impactedAreas); }
}
