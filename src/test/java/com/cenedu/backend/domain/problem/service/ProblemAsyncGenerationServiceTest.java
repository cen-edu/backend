package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationJobResult;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import org.junit.jupiter.api.Test;

class ProblemAsyncGenerationServiceTest {
    @Test
    void rejectsNonPersonalizedPlanAtPersonalizedEntryPoint() {
        var plan = mock(ProblemGenerationPlan.class);
        var jobs = mock(ProblemGenerationJobService.class);
        whenType(plan, GenerationJobType.GENERAL_LEARNING);
        var service = new ProblemAsyncGenerationService(mock(ProblemGenerationPlanningService.class), jobs,
                mock(ProblemGenerationAsyncRunner.class), mock(ProblemSnapshotQueryService.class),
                mock(com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService.class));

        assertThatThrownBy(() -> service.startPersonalized(7L, plan))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void whenType(ProblemGenerationPlan plan, GenerationJobType type) {
        org.mockito.Mockito.when(plan.jobType()).thenReturn(type);
    }
}
