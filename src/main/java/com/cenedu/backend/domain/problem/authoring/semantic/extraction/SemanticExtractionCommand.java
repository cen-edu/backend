package com.cenedu.backend.domain.problem.authoring.semantic.extraction;

import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;

public record SemanticExtractionCommand(UUID requestId, Long questionId,
        CurriculumScope curriculum, QuestionSnapshotV1 snapshot) { }
