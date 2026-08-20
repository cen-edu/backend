package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationSlotPlan;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceRetrievalPort;
import com.cenedu.backend.domain.problem.authoring.retrieval.RetrievedProblemReference;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

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

    @Test
    void similarPrefersFourBankItemsAndCreatesOnlyShortageAiItems() {
        ProblemBankSnapshotQueryService snapshots = mock(ProblemBankSnapshotQueryService.class);
        when(snapshots.getSnapshots(List.of(901L))).thenReturn(List.of(
                new BankSnapshotResult(901L, snapshot(20L), true, List.of())));
        when(snapshots.getSnapshots(List.of(301L, 302L, 303L))).thenReturn(List.of(
                new BankSnapshotResult(301L, snapshot(20L), true, List.of()),
                new BankSnapshotResult(302L, snapshot(20L), true, List.of()),
                new BankSnapshotResult(303L, snapshot(20L), true, List.of())));
        var retrieval = mock(ProblemReferenceRetrievalPort.class);
        var retrievalProvider = mock(ObjectProvider.class);
        var traceProvider = mock(ObjectProvider.class);
        var properties = mock(ProblemRagProperties.class);
        when(retrievalProvider.getIfAvailable()).thenReturn(retrieval);
        when(properties.enabled()).thenReturn(true);
        when(properties.candidateLimit()).thenReturn(40);
        when(retrieval.retrieve(any())).thenReturn(List.of(
                retrieved(301L), retrieved(302L), retrieved(303L)));
        var service = new PersonalizedProblemGenerationPlanningService(
                snapshots, retrievalProvider, traceProvider, properties);

        var plan = service.plan(UUID.randomUUID(), similarProposal(),
                List.of(new CustomProblemGenerationItemRequest(20L, 0, 5, 0)),
                Map.of(20L, path(20L)));

        assertThat(plan.slots()).extracting(ProblemGenerationSlotPlan::source)
                .containsExactly(GenerationSlotSource.BANK_REUSE, GenerationSlotSource.BANK_REUSE,
                        GenerationSlotSource.BANK_REUSE, GenerationSlotSource.AI_GENERATION,
                        GenerationSlotSource.AI_GENERATION);
        assertThat(plan.slots()).filteredOn(slot -> slot.source() == GenerationSlotSource.AI_GENERATION)
                .allSatisfy(slot -> {
                    assertThat(slot.sourceQuestionId()).isNull();
                    assertThat(slot.originQuestionId()).isEqualTo(901L);
                    assertThat(slot.customStage()).isEqualTo(CustomStage.SIMILAR);
                    assertThat(slot.generationCommand().references())
                            .extracting(reference -> reference.role())
                            .containsExactly(GenerationReferenceRole.ORIGIN,
                                    GenerationReferenceRole.EXAMPLE,
                                    GenerationReferenceRole.EXAMPLE,
                                    GenerationReferenceRole.EXAMPLE);
                });
        ArgumentCaptor<ProblemReferenceQuery> query = ArgumentCaptor.forClass(ProblemReferenceQuery.class);
        verify(retrieval).retrieve(query.capture());
        assertThat(query.getValue().originQuestionId()).isEqualTo(901L);
        assertThat(query.getValue().selectionLimit()).isEqualTo(4);
        assertThat(query.getValue().excludedQuestionIds()).containsExactly(999L);
    }

    @Test
    void advancedCommandCarriesStructuredWeaknessEvidence() {
        ProblemBankSnapshotQueryService snapshots = mock(ProblemBankSnapshotQueryService.class);
        when(snapshots.getSnapshots(List.of(901L))).thenReturn(List.of(
                new BankSnapshotResult(901L, snapshot(20L), true, List.of())));
        var service = new PersonalizedProblemGenerationPlanningService(snapshots);

        var plan = service.plan(UUID.randomUUID(), advancedProposal(),
                List.of(new CustomProblemGenerationItemRequest(20L, 0, 0, 1)),
                Map.of(20L, path(20L)));

        var command = plan.slots().getFirst().generationCommand();
        assertThat(command.purpose()).isEqualTo(
                com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose.PERSONALIZED_APPLICATION);
        assertThat(command.specification().difficulty()).isEqualTo("high");
        assertThat(command.specification().questionType()).isEqualTo(QuestionType.STEP_FILL);
        assertThat(command.specification().requiresSolutionStructure()).isTrue();
        assertThat(command.specification().targetDiagnosticTypes())
                .containsExactly(DiagnosticType.EXECUTE);
        assertThat(command.personalizedEvidence().historicalIncorrectItemCount()).isEqualTo(4);
        assertThat(command.personalizedEvidence().evaluationAreaEvidence().getFirst().evaluationArea())
                .isEqualTo(EvaluationArea.CALCULATION);
        assertThat(command.personalizedEvidence().diagnosticEvidence().getFirst().diagnosticType())
                .isEqualTo(DiagnosticType.EXECUTE);
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

    private static ReissueProposalResponse similarProposal() {
        return new ReissueProposalResponse(List.of(
                new ReissueProposalResponse.SubUnitProposal(20L, "소단원20", null, null,
                        new ReissueProposalResponse.ReviewProposal(0, 0, List.of()),
                        new ReissueProposalResponse.SimilarProposal(5, 5, "mid",
                                List.of(new ReissueProposalResponse.ReferenceQuestion(901L, 2, null)),
                                List.of(999L)),
                        new ReissueProposalResponse.AdvancedProposal(false, 0, 0, 0, 0,
                                null, null, List.of(), List.of()))));
    }

    private static ReissueProposalResponse advancedProposal() {
        return new ReissueProposalResponse(List.of(
                new ReissueProposalResponse.SubUnitProposal(20L, "소단원20", null, null,
                        new ReissueProposalResponse.ReviewProposal(0, 0, List.of()),
                        new ReissueProposalResponse.SimilarProposal(0, 0, "mid",
                                List.of(new ReissueProposalResponse.ReferenceQuestion(901L, 2, null)),
                                List.of()),
                        new ReissueProposalResponse.AdvancedProposal(true, 1, 1, 4, 2,
                                EvaluationArea.CALCULATION, DiagnosticStage.EXECUTE,
                                List.of(new ReissueProposalResponse.EvaluationAreaEvidence(
                                        EvaluationArea.CALCULATION, 5, 2, BigDecimal.valueOf(.4))),
                                List.of(new ReissueProposalResponse.DiagnosticStageEvidence(
                                        DiagnosticStage.EXECUTE, 5, 2, BigDecimal.valueOf(.4)))))));
    }

    private static RetrievedProblemReference retrieved(long questionId) {
        return new RetrievedProblemReference(questionId, snapshot(20L), .9, 1,
                "hash-" + questionId, "cluster-" + questionId, java.util.Set.of());
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
