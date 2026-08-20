package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationSlotPlan;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;

class PersonalizedProblemGenerationPlanningServiceTest {

    @Test
    void reviewSlotsFollowProposalOrderAndUseCandidateOrder() {
        ProblemBankSnapshotQueryService snapshots = mock(ProblemBankSnapshotQueryService.class);
        when(snapshots.getSnapshots(List.of(101L, 102L))).thenReturn(List.of(
                new BankSnapshotResult(101L, snapshot(20L), true, List.of()),
                new BankSnapshotResult(102L, snapshot(20L), true, List.of())));
        when(snapshots.getSnapshots(List.of(201L))).thenReturn(List.of(
                new BankSnapshotResult(201L, snapshot(30L), true, List.of())));

        var service = new PersonalizedProblemGenerationPlanningService(snapshots);
        var plan = service.plan(UUID.randomUUID(), proposal(),
                List.of(new CustomProblemGenerationItemRequest(20L, 2, 0, 0),
                        new CustomProblemGenerationItemRequest(30L, 1, 0, 0)),
                Map.of(20L, path(20L), 30L, path(30L)));

        assertThat(plan.slots()).extracting(ProblemGenerationSlotPlan::slotIndex)
                .containsExactly(1, 2, 3);
        assertThat(plan.slots()).extracting(ProblemGenerationSlotPlan::source)
                .containsOnly(GenerationSlotSource.BANK_REUSE);
        assertThat(plan.slots()).extracting(ProblemGenerationSlotPlan::sourceQuestionId)
                .containsExactly(101L, 102L, 201L);
        assertThat(plan.slots()).extracting(ProblemGenerationSlotPlan::customStage)
                .containsOnly(CustomStage.REVIEW);
    }

    @Test
    void rejectsMissingOrUnusableReviewSnapshot() {
        ProblemBankSnapshotQueryService snapshots = mock(ProblemBankSnapshotQueryService.class);
        when(snapshots.getSnapshots(List.of(101L))).thenReturn(List.of());
        var service = new PersonalizedProblemGenerationPlanningService(snapshots);

        assertThatThrownBy(() -> service.plan(UUID.randomUUID(), proposal(),
                List.of(new CustomProblemGenerationItemRequest(20L, 1, 0, 0)),
                Map.of(20L, path(20L))))
                .isInstanceOf(com.cenedu.backend.global.common.BusinessException.class);
    }

    private static ReissueProposalResponse proposal() {
        return new ReissueProposalResponse(List.of(
                new ReissueProposalResponse.SubUnitProposal(20L, "소단원20", null, null,
                        new ReissueProposalResponse.ReviewProposal(2, 2, List.of(101L, 102L)),
                        new ReissueProposalResponse.SimilarProposal(0, 0, "mid", List.of(), List.of()),
                        new ReissueProposalResponse.AdvancedProposal(false, 0, 0, 0, 0,
                                null, null, List.of(), List.of())),
                new ReissueProposalResponse.SubUnitProposal(30L, "소단원30", null, null,
                        new ReissueProposalResponse.ReviewProposal(1, 1, List.of(201L)),
                        new ReissueProposalResponse.SimilarProposal(0, 0, "mid", List.of(), List.of()),
                        new ReissueProposalResponse.AdvancedProposal(false, 0, 0, 0, 0,
                                null, null, List.of(), List.of()))));
    }

    private static CurriculumPathResponse path(long subUnitId) {
        return new CurriculumPathResponse(1L, "대단원", 2L, "중단원", subUnitId,
                "소단원" + subUnitId, "2022_REVISED", "MIDDLE", (short) 1,
                (short) 1, null);
    }

    private static QuestionSnapshotV1 snapshot(long subUnitId) {
        return new QuestionSnapshotV1(1,
                new com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata(
                        QuestionType.STEP_FILL, QuestionPresentation.TEXT_ONLY, "mid",
                        subUnitId, null, null, null), List.of(), List.of(), List.of(),
                List.of(), List.of(), null, null, List.of());
    }
}
