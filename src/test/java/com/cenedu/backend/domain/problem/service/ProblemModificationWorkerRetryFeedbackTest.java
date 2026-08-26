package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditChangeNature;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.port.ProblemModificationPort;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.global.common.enums.QuestionType;

class ProblemModificationWorkerRetryFeedbackTest {

    @Test
    void 두번째_수정_시도에는_직전_실패_코드가_전달된다() {
        ProblemModificationPort port = mock(ProblemModificationPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProblemModificationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        ProblemCandidateProcessingService processing = mock(ProblemCandidateProcessingService.class);
        ProblemAuthoringStateService state = mock(ProblemAuthoringStateService.class);
        ProblemCandidateDraft candidate = candidate();
        when(port.modify(any())).thenReturn(candidate, candidate);
        when(processing.process(any())).thenReturn(failed(), passed());
        var worker = new ProblemModificationWorker(provider, processing,
                mock(ProblemAuthoringSessionRepository.class), state);
        var reference = new GenerationReference(
                GenerationReferenceRole.EXAMPLE, 99L, candidate.snapshot());
        ProblemModificationCommand command = new ProblemModificationCommand(
                UUID.randomUUID(), plan(), candidate.snapshot(), null, List.of(),
                scope(), List.of(reference), List.of());

        CandidateProcessingResult result = worker.execute(7L, command);

        ArgumentCaptor<ProblemModificationCommand> captor =
                ArgumentCaptor.forClass(ProblemModificationCommand.class);
        org.mockito.Mockito.verify(port, org.mockito.Mockito.times(2)).modify(captor.capture());
        assertThat(captor.getAllValues().get(1).previousIssueCodes())
                .containsExactly(VerificationIssueCode.EDIT_REQUIREMENT_MISSING);
        assertThat(captor.getAllValues().get(1).references()).containsExactly(reference);
        assertThat(captor.getAllValues().get(1).curriculum()).isEqualTo(scope());
        assertThat(result.promoted()).isTrue();
    }

    private CandidateProcessingResult failed() {
        UUID requestId = UUID.randomUUID();
        var finding = new VerificationFinding(VerificationCheckType.EDIT_REQUIREMENT,
                VerificationFindingStatus.FAIL, VerificationSeverity.ERROR,
                VerificationIssueCode.EDIT_REQUIREMENT_MISSING,
                "수정 요청이 반영되지 않았습니다.", "민감한 상세 근거");
        var report = new ProblemVerificationReport(requestId, VerificationScope.CONTENT,
                VerificationOverallStatus.FAILED, List.of(finding));
        return new CandidateProcessingResult(10L, 1, requestId,
                VerificationOverallStatus.FAILED,
                ProblemVerificationBundle.contentOnly(requestId, report), false);
    }

    private CandidateProcessingResult passed() {
        UUID requestId = UUID.randomUUID();
        var report = new ProblemVerificationReport(requestId, VerificationScope.CONTENT,
                VerificationOverallStatus.PASSED, List.of());
        return new CandidateProcessingResult(11L, 2, requestId,
                VerificationOverallStatus.PASSED,
                ProblemVerificationBundle.contentOnly(requestId, report), true);
    }

    private ProblemEditExecutionPlan plan() {
        return new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L, EditAction.REPLACE,
                ReplacementSourcePolicy.GENERATE_ONLY, null,
                List.of(new ProblemEditInstruction(EditTargetType.WHOLE_QUESTION, null,
                        EditChangeNature.STRUCTURAL, "조건을 반대로 변경")), null,
                List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), null);
    }

    private ProblemCandidateDraft candidate() {
        var snapshot = new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY,
                        "mid", 10L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "문제를 푸시오.", null, null)),
                List.of(), List.of(), List.of(), List.of(), "해설", null, List.of());
        return ProblemCandidateDraft.legacy(UUID.randomUUID(), snapshot, List.of(),
                new CandidateProvenance(CandidateSourceType.AI_MODIFY, null, List.of()));
    }

    private CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                null, 10L, "수와 연산", "정수와 유리수", "유리수의 계산");
    }
}
