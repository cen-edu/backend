package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionCommand;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionResult;

public interface ProblemSemanticExtractionPort {
    SemanticExtractionResult extract(SemanticExtractionCommand command);
}
