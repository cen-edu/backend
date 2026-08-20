package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationWorkItem;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationItemResult;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationJobResult;
import com.cenedu.backend.domain.problem.entity.ProblemGenerationItem;
import com.cenedu.backend.domain.problem.entity.ProblemGenerationJob;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemGenerationItemRepository;
import com.cenedu.backend.domain.problem.repository.ProblemGenerationJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ProblemGenerationJobServiceTest {

    @Test
    @DisplayName("여러 Item 중 성공과 실패가 섞이면 Job을 PARTIALLY_FAILED로 집계한다")
    void aggregatesPartialFailure() {
        ProblemGenerationJobRepository jobRepository =
                mock(ProblemGenerationJobRepository.class);
        ProblemGenerationItemRepository itemRepository =
                mock(ProblemGenerationItemRepository.class);
        ProblemGenerationJob job = ProblemGenerationJob.create(
                7L, UUID.randomUUID(), GenerationJobType.GENERAL_LEARNING);
        ReflectionTestUtils.setField(job, "id", 5L);
        job.start();
        ProblemGenerationItem success = item(1L, 1);
        success.startGeneration();
        success.startVerification();
        ProblemGenerationItem failure = item(2L, 2);
        failure.startGeneration();
        failure.fail("GENERATION_FAILED");

        when(itemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(success));
        when(jobRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(job));
        when(itemRepository.findAllByJobIdOrderByItemOrder(5L))
                .thenReturn(List.of(success, failure));
        ProblemGenerationJobService service = new ProblemGenerationJobService(
                jobRepository,
                itemRepository,
                mock(ProblemAuthoringSessionRepository.class),
                new ProblemAuthoringJsonCodec(new ObjectMapper()),
                mock(ProblemAuthoringVersionService.class));
        ProblemGenerationWorkItem workItem = new ProblemGenerationWorkItem(
                1L, 5L, 7L, 11L, mock(ProblemGenerationCommand.class));

        service.succeed(workItem);

        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.PARTIALLY_FAILED);
    }

    @Test
    @DisplayName("Job 조회 결과에 맞춤 단계와 기준 문항을 매핑한다")
    void mapsCustomMetadataToResult() {
        ProblemGenerationItem item = ProblemGenerationItem.create(
                5L, 1, UUID.randomUUID(), 11L,
                com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose
                        .PERSONALIZED_APPLICATION,
                1, "{}", CustomStage.ADVANCED, 30L);
        ReflectionTestUtils.setField(item, "id", 9L);
        ProblemGenerationJob job = ProblemGenerationJob.create(
                7L, UUID.randomUUID(), GenerationJobType.PERSONALIZED);
        ReflectionTestUtils.setField(job, "id", 5L);
        ProblemGenerationItemRepository itemRepository = mock(ProblemGenerationItemRepository.class);
        when(itemRepository.findAllByJobIdOrderByItemOrder(5L)).thenReturn(List.of(item));
        ProblemGenerationJobService service = new ProblemGenerationJobService(
                mock(ProblemGenerationJobRepository.class),
                itemRepository,
                mock(ProblemAuthoringSessionRepository.class),
                new ProblemAuthoringJsonCodec(new ObjectMapper()),
                mock(ProblemAuthoringVersionService.class));

        ProblemGenerationJobResult jobResult = ReflectionTestUtils.invokeMethod(
                service, "toResult", job);
        ProblemGenerationItemResult result = jobResult.items().getFirst();

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo(GenerationSlotSource.AI_GENERATION);
        assertThat(result.originQuestionId()).isEqualTo(30L);
        assertThat(result.customStage()).isEqualTo(CustomStage.ADVANCED);
    }

    private ProblemGenerationItem item(Long id, int order) {
        ProblemGenerationItem item = ProblemGenerationItem.create(
                5L, order, UUID.randomUUID(), 10L + order,
                com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose
                        .GENERAL_LEARNING_SHORTAGE,
                1, "{}");
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
