package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationJobResult;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.dto.request.AssessmentGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.request.AsyncAssessmentGenerationRequest;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;

class ProblemGenerationVisualPolicyTest {

    @Test
    void assessmentRequestKeepsOneNonForcedRequirement() {
        ProblemGenerationPlanningService planning = mock(ProblemGenerationPlanningService.class);
        ProblemGenerationJobService jobs = mock(ProblemGenerationJobService.class);
        ProblemGenerationAsyncRunner runner = mock(ProblemGenerationAsyncRunner.class);
        CurriculumUnitQueryService curriculum = mock(CurriculumUnitQueryService.class);
        ProblemGenerationPlan plan = mock(ProblemGenerationPlan.class);
        UUID requestId = UUID.randomUUID();

        when(curriculum.getPathsBySubUnitIds(Set.of(20L))).thenReturn(Map.of(20L, coordinatePath()));
        when(jobs.findByClientRequestId(7L, requestId)).thenReturn(Optional.empty());
        when(planning.plan(eq(requestId), eq(GenerationJobType.COMPREHENSIVE_ASSESSMENT), anyList()))
                .thenReturn(plan);
        when(jobs.create(7L, plan)).thenReturn(
                new ProblemGenerationJobResult(41L, GenerationJobStatus.COMPLETED, List.of()));

        var service = new ProblemAsyncGenerationService(planning, jobs, runner,
                mock(ProblemSnapshotQueryService.class), curriculum);
        service.startAssessment(7L, new AsyncAssessmentGenerationRequest(requestId, List.of(
                new AssessmentGenerationItemRequest(20L, QuestionType.MULTIPLE_CHOICE, (short) 2, 5))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProblemGenerationRequirement>> requirements = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(planning).plan(eq(requestId),
                eq(GenerationJobType.COMPREHENSIVE_ASSESSMENT), requirements.capture());
        assertThat(requirements.getValue()).singleElement().satisfies(requirement -> {
            assertThat(requirement.count()).isEqualTo(5);
            assertThat(requirement.specification().visualRequirement().mode())
                    .isEqualTo(VisualGenerationMode.NONE);
        });
    }

    @Test
    void explicitGraphRequirementStillReusesValidBankProblem() {
        ProblemQuestionSelector selector = mock(ProblemQuestionSelector.class);
        ProblemBankSnapshotQueryService snapshots = mock(ProblemBankSnapshotQueryService.class);
        ProblemQuestion bankQuestion = mock(ProblemQuestion.class);
        when(bankQuestion.getId()).thenReturn(101L);
        when(selector.selectAvailable(eq(20L), eq((short) 2), eq(QuestionType.MULTIPLE_CHOICE),
                eq(Integer.MAX_VALUE), any())).thenReturn(List.of(bankQuestion));
        when(snapshots.getSnapshots(List.of(101L))).thenReturn(List.of(
                new BankSnapshotResult(101L, snapshot(), true, List.of())));

        ProblemGenerationRequirement requirement = new ProblemGenerationRequirement(
                20L, (short) 2, QuestionType.MULTIPLE_CHOICE, 1,
                GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE,
                new GenerationSpecification(QuestionType.MULTIPLE_CHOICE, "mid", null, List.of(), false,
                        new VisualGenerationRequirement(VisualGenerationMode.REQUIRED,
                                VisualReferenceKind.COORDINATE_GRAPH)),
                scope(), List.of(), List.of());

        ProblemGenerationPlan plan = new ProblemGenerationPlanningService(selector, snapshots)
                .plan(UUID.randomUUID(), GenerationJobType.COMPREHENSIVE_ASSESSMENT, List.of(requirement));

        assertThat(plan.slots()).singleElement().satisfies(slot -> {
            assertThat(slot.source()).isEqualTo(GenerationSlotSource.BANK_REUSE);
            assertThat(slot.sourceQuestionId()).isEqualTo(101L);
        });
    }

    private static CurriculumPathResponse coordinatePath() {
        return new CurriculumPathResponse(1L, "변화와 관계", 2L, "좌표와 그래프", 20L,
                "좌표평면과 그래프", "2022_REVISED", "MIDDLE", (short) 1, (short) 1, null);
    }

    private static CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 20L,
                "변화와 관계", "좌표와 그래프", "좌표평면과 그래프");
    }

    private static QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE, QuestionPresentation.TEXT_ONLY,
                        "mid", 20L, null, null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null, List.of());
    }
}
