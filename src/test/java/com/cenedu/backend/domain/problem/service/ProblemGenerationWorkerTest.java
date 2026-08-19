package com.cenedu.backend.domain.problem.service;

import static com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures.shortInput;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationWorkItem;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProblemGenerationWorkerTest {

    private ProblemGenerationJobService jobService;
    private ProblemCandidateProcessingService candidateService;
    private ProblemGenerationPort generationPort;
    private ProblemGenerationWorker worker;
    private ProblemGenerationWorkItem workItem;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jobService = mock(ProblemGenerationJobService.class);
        candidateService = mock(ProblemCandidateProcessingService.class);
        generationPort = mock(ProblemGenerationPort.class);
        ObjectProvider<ProblemGenerationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(generationPort);
        worker = new ProblemGenerationWorker(jobService, candidateService, provider,
                new ProblemAiConcurrencyLimiter(4, 30));
        ProblemGenerationCommand command = new ProblemGenerationCommand(
                UUID.randomUUID(),
                null,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(
                        QuestionType.SHORT_INPUT, "mid", null, List.of()),
                new CurriculumScope(
                        "2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"),
                List.of(), List.of());
        workItem = new ProblemGenerationWorkItem(1L, 2L, 7L, 3L, command);
        when(jobService.tryClaim(1L)).thenReturn(Optional.of(workItem));
    }

    @Test
    @DisplayName("검증 실패를 2회 재생성한 뒤 성공하면 Item을 성공으로 종료한다")
    void retriesTwiceThenSucceeds() {
        when(generationPort.generate(any())).thenAnswer(invocation ->
                candidate(invocation.getArgument(0, ProblemGenerationCommand.class).requestId()));
        when(candidateService.process(any()))
                .thenReturn(result(VerificationOverallStatus.FAILED, false))
                .thenReturn(result(VerificationOverallStatus.FAILED, false))
                .thenReturn(result(VerificationOverallStatus.PASSED, true));
        when(jobService.prepareRetry(any(), any()))
                .thenReturn(true, true);

        worker.execute(1L);

        verify(generationPort, times(3)).generate(any());
        verify(jobService).succeed(workItem);
    }

    @Test
    @DisplayName("생성 재시도를 소진하면 Item을 실패로 종료한다")
    void failsAfterRetryExhaustion() {
        when(generationPort.generate(any()))
                .thenThrow(new IllegalStateException("provider"));
        when(jobService.prepareRetry(any(), any()))
                .thenReturn(true, true, false);

        worker.execute(1L);

        verify(generationPort, times(3)).generate(any());
        verify(jobService).fail(workItem, "GENERATION_FAILED");
    }

    @Test
    @DisplayName("검증 기술 오류는 새 후보를 생성하지 않고 Item을 실패로 종료한다")
    void verificationErrorDoesNotRegenerate() {
        when(generationPort.generate(any())).thenAnswer(invocation ->
                candidate(invocation.getArgument(0, ProblemGenerationCommand.class).requestId()));
        when(candidateService.process(any()))
                .thenReturn(result(VerificationOverallStatus.ERROR, false));

        worker.execute(1L);

        verify(generationPort, times(1)).generate(any());
        verify(jobService).fail(workItem, "VERIFICATION_ERROR");
    }

    @Test
    @DisplayName("멱등 재요청으로 이미 선점된 Item은 두 번 생성하지 않는다")
    void ignoresAlreadyClaimedItem() {
        when(jobService.tryClaim(1L)).thenReturn(Optional.empty());

        worker.execute(1L);

        verifyNoInteractions(generationPort, candidateService);
    }

    private ProblemCandidateDraft candidate(UUID requestId) {
        return new ProblemCandidateDraft(
                requestId, shortInput(), List.of(),
                new CandidateProvenance(
                        CandidateSourceType.AI_GENERATE, null, List.of()));
    }

    private CandidateProcessingResult result(
            VerificationOverallStatus status,
            boolean promoted
    ) {
        return new CandidateProcessingResult(
                10L, 1, UUID.randomUUID(), status, null, promoted);
    }
}
