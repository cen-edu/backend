package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationItemResult;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationJobResult;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.dto.request.AssessmentGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.request.AsyncAssessmentGenerationRequest;
import com.cenedu.backend.domain.problem.dto.request.AsyncProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.request.ProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemAsyncGenerationServiceTest {

    private final ProblemGenerationPlanningService planningService =
            mock(ProblemGenerationPlanningService.class);
    private final ProblemGenerationJobService jobService = mock(ProblemGenerationJobService.class);
    private final ProblemGenerationAsyncRunner runner = mock(ProblemGenerationAsyncRunner.class);
    private final ProblemSnapshotQueryService snapshotQueryService =
            mock(ProblemSnapshotQueryService.class);
    private final CurriculumUnitQueryService curriculumQueryService =
            mock(CurriculumUnitQueryService.class);
    private final ProblemAsyncGenerationService service = new ProblemAsyncGenerationService(
            planningService, jobService, runner, snapshotQueryService, curriculumQueryService);

    @Test
    @DisplayName("비동기 종합평가도 동기 API와 동일하게 STEP_FILL을 차단한다")
    void rejectsUnsupportedAssessmentType() {
        AsyncAssessmentGenerationRequest request = new AsyncAssessmentGenerationRequest(
                UUID.randomUUID(),
                List.of(new AssessmentGenerationItemRequest(
                        30L, QuestionType.STEP_FILL, (short) 2, 1)));

        assertThatThrownBy(() -> service.startAssessment(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ASSESSMENT_QUESTION_TYPE_NOT_ALLOWED);
        verifyNoInteractions(curriculumQueryService, planningService, jobService, runner);
    }

    @Test
    @DisplayName("일반학습 비동기 접수는 대기 중인 AI 문항만 실행기에 전달한다")
    void dispatchesOnlyQueuedItems() {
        UUID clientRequestId = UUID.randomUUID();
        AsyncProblemGenerationRequest request = new AsyncProblemGenerationRequest(
                clientRequestId, List.of(new ProblemGenerationItemRequest(30L, (short) 2, 2)));
        CurriculumPathResponse path = new CurriculumPathResponse(
                1L, "대단원", 2L, "중단원", 30L, "소단원");
        ProblemGenerationPlan plan = mock(ProblemGenerationPlan.class);
        ProblemGenerationJobResult job = new ProblemGenerationJobResult(
                10L, GenerationJobStatus.RUNNING,
                List.of(
                        new ProblemGenerationItemResult(
                                101L, 201L, 1, GenerationItemStatus.QUEUED, (short) 0, null),
                        new ProblemGenerationItemResult(
                                102L, 202L, 2, GenerationItemStatus.SUCCEEDED, (short) 0, null)));
        when(curriculumQueryService.getPathsBySubUnitIds(eq(java.util.Set.of(30L))))
                .thenReturn(Map.of(30L, path));
        when(planningService.plan(eq(clientRequestId), eq(GenerationJobType.GENERAL_LEARNING), any()))
                .thenReturn(plan);
        when(jobService.create(7L, plan)).thenReturn(job);

        ProblemGenerationStartResponse response = service.startGeneral(7L, request);

        assertThat(response.jobId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(GenerationJobStatus.RUNNING);
        assertThat(response.totalCount()).isEqualTo(2);
        verify(runner).execute(101L);
    }
}
