package com.cenedu.backend.domain.problem.authoring.semantic.model;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecV1;
import java.util.List;
public record ProblemSemanticModelV1(int schemaVersion, CurriculumScope curriculum, SemanticProblemIntent intent, List<SemanticParameter> parameters, List<SemanticComputation> computations, List<SemanticConstraint> constraints, SemanticPresentationPlan presentation, List<DiagramSpecV1> diagrams, List<SemanticAssertion> assertions) { public static final int CURRENT_SCHEMA_VERSION=1; public ProblemSemanticModelV1 { parameters=List.copyOf(parameters); computations=List.copyOf(computations); constraints=List.copyOf(constraints); diagrams=List.copyOf(diagrams); assertions=List.copyOf(assertions); } }
