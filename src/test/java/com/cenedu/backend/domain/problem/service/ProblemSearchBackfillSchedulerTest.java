package com.cenedu.backend.domain.problem.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cenedu.backend.domain.problem.authoring.search.SearchIndexMaintenancePort;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.entity.ProblemSearchBackfillState;
import com.cenedu.backend.domain.problem.repository.ProblemSearchBackfillStateRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProblemSearchBackfillSchedulerTest {

    @Test
    void 전체_검사가_끝나도_마지막_커서를_유지해_격리_문항을_반복_검사하지_않는다() {
        ProblemSearchBackfillService service = mock(ProblemSearchBackfillService.class);
        ProblemSearchBackfillStateRepository stateRepository =
                mock(ProblemSearchBackfillStateRepository.class);
        SearchIndexMaintenancePort maintenancePort = mock(SearchIndexMaintenancePort.class);
        ProblemSearchBackfillState state = mock(ProblemSearchBackfillState.class);

        when(stateRepository.findByStateKeyForUpdate("problem-search"))
                .thenReturn(Optional.of(state));
        when(state.getCursor()).thenReturn(0L);
        when(maintenancePort.reconcile()).thenReturn(
                new SearchIndexMaintenancePort.SearchIndexReconciliationResult(0, 0));
        when(service.enqueueBatch(0L, 50)).thenReturn(
                new ProblemSearchBackfillService.BackfillBatchResult(
                        5598L, 40, 0, 0, 40, true));

        var scheduler = new ProblemSearchBackfillScheduler(service, properties(), stateRepository,
                maintenancePort);
        scheduler.run();

        verify(state).advance(org.mockito.ArgumentMatchers.eq(5598L), any(OffsetDateTime.class));
    }

    private ProblemRagProperties properties() {
        return new ProblemRagProperties(true, "A_DENSE_MMR_V1", 40, 3, 4, 0.70, 0.55,
                Duration.ofSeconds(2), new ProblemRagProperties.Indexing(true, 20, 3,
                Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMinutes(10),
                Duration.ofHours(1), 50));
    }
}
