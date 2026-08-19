package com.cenedu.backend.domain.problem.authoring.semantic.evaluation;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;import java.util.*;
public record SemanticEvaluation(ProblemSemanticModelV1 normalizedModel,Map<String,SemanticResolvedValue> values,List<String> topologicalOrder){}
