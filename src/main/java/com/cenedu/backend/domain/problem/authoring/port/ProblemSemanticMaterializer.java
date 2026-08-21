package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;

public interface ProblemSemanticMaterializer {
    MaterializedProblem materialize(ProblemSemanticModelV1 model);
}
