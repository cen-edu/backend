package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.dto.request.*;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.global.common.enums.CustomStage;
import org.junit.jupiter.api.Test;

class ProblemAsyncGenerationServiceTest {
    @Test
    void rejectsNonPersonalizedPlanAtPersonalizedEntryPoint() {
        var plan = mock(ProblemGenerationPlan.class);
        when(plan.jobType()).thenReturn(GenerationJobType.GENERAL_LEARNING);
        var service = service(mock(ProblemGenerationPlanningService.class),
                mock(ProblemGenerationJobService.class), mock(ProblemGenerationAsyncRunner.class),
                mock(com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService.class));
        assertThatThrownBy(() -> service.startPersonalized(7L, plan))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generalStartStillRunsQueuedItems() {
        var planning = mock(ProblemGenerationPlanningService.class);
        var jobs = mock(ProblemGenerationJobService.class);
        var runner = mock(ProblemGenerationAsyncRunner.class);
        var curriculum = mock(com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService.class);
        var plan = mock(ProblemGenerationPlan.class);
        when(curriculum.getPathsBySubUnitIds(any())).thenReturn(Map.of(20L, path()));
        when(planning.plan(any(), eq(GenerationJobType.GENERAL_LEARNING), anyList())).thenReturn(plan);
        ProblemGenerationJobResult queuedJob = queuedJob(42L);
        when(jobs.create(7L, plan)).thenReturn(queuedJob);
        service(planning, jobs, runner, curriculum).startGeneral(7L,
                new AsyncProblemGenerationRequest(UUID.randomUUID(),
                        List.of(new ProblemGenerationItemRequest(20L, (short) 2, 1))));
        verify(runner).execute(42L);
    }

    @Test
    void assessmentStartStillRunsQueuedItems() {
        var planning = mock(ProblemGenerationPlanningService.class);
        var jobs = mock(ProblemGenerationJobService.class);
        var runner = mock(ProblemGenerationAsyncRunner.class);
        var curriculum = mock(com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService.class);
        var plan = mock(ProblemGenerationPlan.class);
        when(curriculum.getPathsBySubUnitIds(any())).thenReturn(Map.of(20L, path()));
        when(planning.plan(any(), eq(GenerationJobType.COMPREHENSIVE_ASSESSMENT), anyList())).thenReturn(plan);
        ProblemGenerationJobResult queuedJob = queuedJob(43L);
        when(jobs.create(7L, plan)).thenReturn(queuedJob);
        service(planning, jobs, runner, curriculum).startAssessment(7L,
                new AsyncAssessmentGenerationRequest(UUID.randomUUID(), List.of(
                        new AssessmentGenerationItemRequest(20L, QuestionType.MULTIPLE_CHOICE, (short) 2, 1))));
        verify(runner).execute(43L);
    }

    @Test
    void pollingMapsCustomStageAndSourceMetadata() {
        var jobs = mock(ProblemGenerationJobService.class);
        var item = mock(ProblemGenerationItemResult.class);
        when(item.itemOrder()).thenReturn(1);
        when(item.itemId()).thenReturn(42L);
        when(item.sessionId()).thenReturn(11L);
        when(item.status()).thenReturn(GenerationItemStatus.QUEUED);
        when(item.customStage()).thenReturn(CustomStage.ADVANCED);
        when(item.sourceQuestionId()).thenReturn(null);
        when(item.originQuestionId()).thenReturn(901L);
        var job = mock(ProblemGenerationJobResult.class);
        when(job.jobId()).thenReturn(99L);
        when(job.status()).thenReturn(GenerationJobStatus.RUNNING);
        when(job.items()).thenReturn(List.of(item));
        when(jobs.get(7L, 99L)).thenReturn(job);
        var service = service(mock(ProblemGenerationPlanningService.class), jobs,
                mock(ProblemGenerationAsyncRunner.class),
                mock(com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService.class));

        var response = service.getStatus(7L, 99L);

        var slot = response.slots().getFirst();
        org.assertj.core.api.Assertions.assertThat(slot.customStage()).isEqualTo("advanced");
        org.assertj.core.api.Assertions.assertThat(slot.sourceQuestionId()).isNull();
        org.assertj.core.api.Assertions.assertThat(slot.originQuestionId()).isEqualTo(901L);
    }

    private static ProblemAsyncGenerationService service(ProblemGenerationPlanningService planning,
            ProblemGenerationJobService jobs, ProblemGenerationAsyncRunner runner,
            com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService curriculum) {
        return new ProblemAsyncGenerationService(planning, jobs, runner,
                mock(ProblemSnapshotQueryService.class), curriculum);
    }

    private static ProblemGenerationJobResult queuedJob(long itemId) {
        ProblemGenerationItemResult item = mock(ProblemGenerationItemResult.class);
        when(item.status()).thenReturn(GenerationItemStatus.QUEUED);
        when(item.itemId()).thenReturn(itemId);
        ProblemGenerationJobResult job = mock(ProblemGenerationJobResult.class);
        when(job.items()).thenReturn(List.of(item));
        return job;
    }

    private static CurriculumPathResponse path() {
        return new CurriculumPathResponse(1L, "대단원", 2L, "중단원", 20L, "소단원",
                "2022_REVISED", "MIDDLE", (short) 1, (short) 1, null);
    }
}
