package com.cenedu.backend.domain.problem.support;

import java.util.ArrayDeque;
import java.util.Deque;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;

/** 비동기 생성 파이프라인 테스트에서 외부 LLM을 대체하는 Fake Port다. */
public final class FakeProblemGenerationPort implements ProblemGenerationPort {
    private final Deque<RuntimeException> failures = new ArrayDeque<>();
    private int calls;

    /** 다음 생성 호출을 예외로 종료하도록 예약한다. */
    public FakeProblemGenerationPort failNext(RuntimeException failure) {
        failures.addLast(failure);
        return this;
    }

    /** Fake가 실제로 수행한 생성 호출 횟수를 반환한다. */
    public int callCount() {
        return calls;
    }

    /** 모든 문제 유형에 공통으로 사용할 수 있는 정상 후보를 반환한다. */
    @Override
    public ProblemCandidateDraft generate(ProblemGenerationCommand command) {
        calls++;
        if (!failures.isEmpty()) {
            throw failures.removeFirst();
        }
        return new ProblemCandidateDraft(command.requestId(), ProblemSnapshotFixtures.shortInput(),
                java.util.List.of(), new CandidateProvenance(CandidateSourceType.AI_GENERATE, null,
                command.references() == null ? java.util.List.of() : command.references().stream()
                        .map(reference -> reference.sourceQuestionId()).toList()));
    }
}
