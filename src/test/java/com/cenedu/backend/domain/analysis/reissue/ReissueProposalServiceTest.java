package com.cenedu.backend.domain.analysis.reissue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.reissue.row.DiagnosticStageEvidenceRow;
import com.cenedu.backend.domain.analysis.reissue.row.EvaluationAreaEvidenceRow;
import com.cenedu.backend.domain.analysis.reissue.row.IncorrectQuestionRow;
import com.cenedu.backend.domain.analysis.reissue.row.LatestSimilarResultRow;
import com.cenedu.backend.domain.analysis.reissue.row.PlacementTallyRow;
import com.cenedu.backend.domain.analysis.reissue.row.QuestionOwnershipRow;
import com.cenedu.backend.domain.analysis.reissue.row.SubUnitRow;
import com.cenedu.backend.domain.analysis.reissue.row.SubUnitWeaknessRow;
import com.cenedu.backend.domain.analysis.service.AnalysisClassQueryService;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import org.junit.jupiter.api.Test;

class ReissueProposalServiceTest {

    private static final long ASSIGNMENT_ID = 120L;
    private static final long STUDENT_ID = 35L;

    @Test
    void 유사_오답_ORIGIN이_없으면_유사_제안을_0건으로_보정한다() {
        ReissueProposalService service = serviceWith(List.of());

        ReissueProposalResponse.SimilarProposal similar = service
                .getProposal(7L, ASSIGNMENT_ID, STUDENT_ID)
                .subcategories().getFirst().similar();

        assertThat(similar.proposedCount()).isZero();
        assertThat(similar.maxCount()).isZero();
        assertThat(similar.referenceQuestions()).isEmpty();
    }

    @Test
    void 유사_오답_ORIGIN이_있으면_기존_기본값을_유지한다() {
        ReissueProposalService service = serviceWith(List.of(
                new IncorrectQuestionRow(14L, 501L, true, 1, OffsetDateTime.now())));

        ReissueProposalResponse.SimilarProposal similar = service
                .getProposal(7L, ASSIGNMENT_ID, STUDENT_ID)
                .subcategories().getFirst().similar();

        assertThat(similar.proposedCount()).isEqualTo(5);
        assertThat(similar.maxCount()).isEqualTo(10);
        assertThat(similar.referenceQuestions())
                .extracting(ReissueProposalResponse.ReferenceQuestion::questionId)
                .containsExactly(501L);
    }

    private ReissueProposalService serviceWith(List<IncorrectQuestionRow> incorrectQuestions) {
        AnalysisClassQueryService classQueryService = mock(AnalysisClassQueryService.class);
        ReissueProposalRepository repository = mock(ReissueProposalRepository.class);

        when(repository.findRootAssignmentStatus(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.of(AssignmentStatus.GRADED.name()));
        when(repository.findSubUnits(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.of(new SubUnitRow(14L, "소인수분해")));
        when(repository.findPlacementTallies(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.<PlacementTallyRow>of());
        when(repository.findLatestSimilarResults(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.<LatestSimilarResultRow>of());
        when(repository.findIncorrectQuestions(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(incorrectQuestions);
        when(repository.findAnsweredQuestions(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.<QuestionOwnershipRow>of());
        when(repository.findSubUnitWeakness(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.<SubUnitWeaknessRow>of());
        when(repository.findEvaluationAreaEvidence(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.<EvaluationAreaEvidenceRow>of());
        when(repository.findDiagnosticStageEvidence(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(List.<DiagnosticStageEvidenceRow>of());
        when(repository.countCustomSessions(ASSIGNMENT_ID, STUDENT_ID)).thenReturn(0);

        return new ReissueProposalService(classQueryService, repository);
    }
}
