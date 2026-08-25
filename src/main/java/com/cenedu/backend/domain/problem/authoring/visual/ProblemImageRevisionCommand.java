package com.cenedu.backend.domain.problem.authoring.visual;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;

/** 자산 검증 피드백으로 문항 본문은 유지한 채 이미지만 다시 만드는 요청이다. */
public record ProblemImageRevisionCommand(
        ProblemCandidateDraft candidate,
        ProblemGenerationCommand generationCommand,
        int attempt,
        List<VerificationFinding> findings
) {
    public ProblemImageRevisionCommand {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
