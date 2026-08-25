package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.cenedu.backend.domain.problem.entity.ProblemSearchBackfillState;
import com.cenedu.backend.domain.problem.repository.ProblemSearchBackfillStateRepository;
import java.time.OffsetDateTime;

class ProblemSearchBackfillSchedulerTest {
    @Test void keepsCursorAfterExhaustedBatch() {
        var service = mock(ProblemSearchBackfillService.class);
        var states = mock(ProblemSearchBackfillStateRepository.class);
        var properties = new ProblemRagProperties(true, "v", 40, 3, 4, .7, .55, Duration.ofSeconds(2),
                new ProblemRagProperties.Indexing(true, 20, 3, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMinutes(10), Duration.ofHours(1), 50));
        when(service.enqueueBatch(0, 50)).thenReturn(new ProblemSearchBackfillService.BackfillBatchResult(17, 50, 2, 48, true));
        var state = new ProblemSearchBackfillState("problem-search", 0, OffsetDateTime.now());
        when(states.findByStateKeyForUpdate("problem-search")).thenReturn(java.util.Optional.of(state));
        var scheduler = new ProblemSearchBackfillScheduler(service, properties, states);
        scheduler.run();
        verify(service).enqueueBatch(0, 50);
        when(service.enqueueBatch(17, 50)).thenReturn(new ProblemSearchBackfillService.BackfillBatchResult(17, 0, 0, 0, true));
        scheduler.run();
        verify(service).enqueueBatch(17, 50);
        assertThat(properties.indexing().backfillBatchSize()).isEqualTo(50);
        assertThat(state.getCursor()).isEqualTo(17);
    }
}
