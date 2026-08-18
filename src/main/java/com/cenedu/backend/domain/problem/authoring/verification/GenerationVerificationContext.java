package com.cenedu.backend.domain.problem.authoring.verification;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;

/** 생성 목적과 참고 문제를 생성 후보 검증의 맥락으로 제공한다. */
public record GenerationVerificationContext(
        GenerationPurpose generationPurpose,
        List<GenerationReference> references
) implements VerificationContext {
}
