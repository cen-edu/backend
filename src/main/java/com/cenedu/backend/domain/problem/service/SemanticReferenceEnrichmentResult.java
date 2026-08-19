package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;

/** ORIGIN semantic extraction 결과와 생성 command를 함께 전달하는 내부 조정 결과다. */
public record SemanticReferenceEnrichmentResult(
        ProblemGenerationCommand command,
        boolean unsupportedOrigin
) { }
