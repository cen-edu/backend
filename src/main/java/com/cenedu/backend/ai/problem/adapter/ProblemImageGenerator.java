package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;

/** 이미지 종류 하나의 생성·검증·후보 부착을 담당하는 확장 전략이다. */
public interface ProblemImageGenerator {

    /** 이 생성기가 지원하는 이미지 종류를 반환한다. */
    VisualReferenceKind kind();

    /** 이미지 생성을 한 번 시도하고 완성된 후보를 반환한다. */
    ProblemCandidateDraft generate(ProblemCandidateDraft candidate, String description,
                                   ProblemGenerationCommand command, int attempt,
                                   RuntimeException previousFailure);
}
