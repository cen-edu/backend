package com.cenedu.backend.domain.problem.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationWorkItem;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemImageRevisionPort;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.global.common.enums.QuestionType;

class ProblemGenerationWorkerImageRevisionTest {

    @Test
    void 자산_불일치는_전체_문항_재생성_없이_이미지만_한번_환류한다() {
        ProblemGenerationJobService jobService = mock(ProblemGenerationJobService.class);
        ProblemCandidateProcessingService processingService = mock(ProblemCandidateProcessingService.class);
        ProblemGenerationPort generationPort = mock(ProblemGenerationPort.class);
        ProblemImageRevisionPort imageRevisionPort = mock(ProblemImageRevisionPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProblemGenerationPort> generationProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProblemImageRevisionPort> imageRevisionProvider = mock(ObjectProvider.class);

        ProblemGenerationCommand command = command();
        ProblemGenerationWorkItem workItem = new ProblemGenerationWorkItem(11L, 21L, 31L, 41L, command);
        ProblemCandidateDraft initial = candidate(command.requestId());
        ProblemCandidateDraft revised = candidate(UUID.randomUUID());
        CandidateProcessingResult firstFailure = imageFailure();
        CandidateProcessingResult passed = passed();
        when(jobService.tryClaim(11L)).thenReturn(Optional.of(workItem));
        when(generationProvider.getIfAvailable()).thenReturn(generationPort);
        when(imageRevisionProvider.getIfAvailable()).thenReturn(imageRevisionPort);
        when(generationPort.generate(command)).thenReturn(initial);
        when(imageRevisionPort.revise(any())).thenReturn(revised);
        when(processingService.process(any())).thenReturn(firstFailure, passed);

        ProblemGenerationWorker worker = new ProblemGenerationWorker(jobService, processingService,
                generationProvider, new ProblemAiConcurrencyLimiter(1, 1), null, null, null,
                imageRevisionProvider);

        worker.execute(11L);

        verify(generationPort, times(1)).generate(any());
        verify(imageRevisionPort, times(1)).revise(any());
        verify(processingService, times(2)).process(any());
        verify(jobService).succeed(workItem);
        verify(jobService, never()).prepareRetry(any(), any());
        verify(jobService, never()).fail(any(), any());
    }

    private ProblemCandidateDraft candidate(UUID requestId) {
        ProblemCandidateDraft candidate = mock(ProblemCandidateDraft.class);
        QuestionSnapshotV1 snapshot = mock(QuestionSnapshotV1.class);
        when(candidate.requestId()).thenReturn(requestId);
        when(candidate.snapshot()).thenReturn(snapshot);
        when(candidate.semanticModel()).thenReturn(null);
        when(snapshot.assets()).thenReturn(List.of());
        return candidate;
    }

    private CandidateProcessingResult imageFailure() {
        UUID id = UUID.randomUUID();
        ProblemVerificationReport content = new ProblemVerificationReport(id, VerificationScope.CONTENT,
                VerificationOverallStatus.PASSED, List.of());
        VerificationFinding finding = new VerificationFinding(VerificationCheckType.ASSET_CONSISTENCY,
                VerificationFindingStatus.FAIL, VerificationSeverity.ERROR,
                VerificationIssueCode.ASSET_IMAGE_REGENERATABLE, "그림 불일치", null);
        ProblemVerificationReport asset = new ProblemVerificationReport(id, VerificationScope.ASSET,
                VerificationOverallStatus.FAILED, List.of(finding));
        return new CandidateProcessingResult(101L, 1, id, VerificationOverallStatus.FAILED,
                ProblemVerificationBundle.merge(id, content, asset), false);
    }

    private CandidateProcessingResult passed() {
        UUID id = UUID.randomUUID();
        ProblemVerificationReport content = new ProblemVerificationReport(id, VerificationScope.CONTENT,
                VerificationOverallStatus.PASSED, List.of());
        return new CandidateProcessingResult(102L, 2, id, VerificationOverallStatus.PASSED,
                ProblemVerificationBundle.contentOnly(id, content), true);
    }

    private ProblemGenerationCommand command() {
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE,
                new GenerationSpecification(QuestionType.MULTIPLE_CHOICE, "mid", null,
                        List.of(), false, VisualGenerationRequirement.none()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                        null, 20L, "변화와 관계", "좌표와 그래프", "좌표평면과 그래프"),
                List.of(), List.of());
    }
}
