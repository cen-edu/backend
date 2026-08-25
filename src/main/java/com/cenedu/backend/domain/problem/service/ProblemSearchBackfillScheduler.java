package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import com.cenedu.backend.domain.problem.entity.ProblemSearchBackfillState;
import com.cenedu.backend.domain.problem.repository.ProblemSearchBackfillStateRepository;
import com.cenedu.backend.domain.problem.authoring.search.SearchIndexMaintenancePort;
import java.time.OffsetDateTime;

@Component
public class ProblemSearchBackfillScheduler {
    private final ProblemSearchBackfillService service;
    private final ProblemRagProperties properties;
    private final ProblemSearchBackfillStateRepository stateRepository;
    private final SearchIndexMaintenancePort maintenancePort;
    private static final String STATE_KEY = "problem-search";
    private static final String VISUAL_STATE_KEY = "problem-search-visual-coordinate-v1";
    private static final int MAX_VISUAL_BATCHES_PER_RUN = 100;
    private static final Logger log = LoggerFactory.getLogger(ProblemSearchBackfillScheduler.class);
    public ProblemSearchBackfillScheduler(ProblemSearchBackfillService service, ProblemRagProperties properties,
            ProblemSearchBackfillStateRepository stateRepository, SearchIndexMaintenancePort maintenancePort) {
        this.service = service; this.properties = properties; this.stateRepository = stateRepository;
        this.maintenancePort = maintenancePort;
    }

    /** RAG와 인덱싱이 모두 활성화된 경우에만 커서 기반 backfill을 실행한다. */
    @Scheduled(initialDelayString = "${app.problem.rag.indexing.backfill-initial-delay:10m}",
            fixedDelayString = "${app.problem.rag.indexing.backfill-delay:1h}")
    @Transactional
    public void run() {
        if (!properties.enabled() || !properties.indexing().enabled()) return;
        var reconciliation = maintenancePort.reconcile();
        var state = stateRepository.findByStateKeyForUpdate(STATE_KEY)
                .orElseGet(() -> stateRepository.save(new ProblemSearchBackfillState(STATE_KEY, 0, OffsetDateTime.now())));
        long cursor = state.getCursor();
        long before = cursor;
        long started = System.nanoTime();
        var result = service.enqueueBatch(cursor, properties.indexing().backfillBatchSize());
        long nextCursor = result.nextQuestionId();
        state.advance(nextCursor, OffsetDateTime.now());
        var visualResult = runVisualReclassification();
        log.info("event=problem_search_backfill cursorBefore={} cursorAfter={} scanned={} enqueued={} unchanged={} rejected={} exhausted={} deletedIndexes={} reactivatedTasks={} elapsedMs={}",
                before, nextCursor, result.scanned(), result.enqueued(), result.unchanged(), result.rejected(),
                result.exhausted(), reconciliation.deletedIndexes(), reconciliation.reactivatedTasks(),
                (System.nanoTime() - started) / 1_000_000);
        log.info("event=problem_search_visual_reclassification cursorBefore={} cursorAfter={} scanned={} enqueued={} unchanged={} rejected={} exhausted={}",
                visualResult.cursorBefore(), visualResult.cursorAfter(), visualResult.scanned(),
                visualResult.enqueued(), visualResult.unchanged(), visualResult.rejected(),
                visualResult.exhausted());
    }

    private VisualBackfillResult runVisualReclassification() {
        var state = stateRepository.findByStateKeyForUpdate(VISUAL_STATE_KEY)
                .orElseGet(() -> stateRepository.save(new ProblemSearchBackfillState(
                        VISUAL_STATE_KEY, 0, OffsetDateTime.now())));
        long before = state.getCursor();
        long cursor = before;
        int scanned = 0, enqueued = 0, unchanged = 0, rejected = 0;
        boolean exhausted = false;
        for (int batch = 0; batch < MAX_VISUAL_BATCHES_PER_RUN && !exhausted; batch++) {
            var result = service.enqueueVisualReclassificationBatch(cursor,
                    properties.indexing().backfillBatchSize());
            scanned += result.scanned();
            enqueued += result.enqueued();
            unchanged += result.unchanged();
            rejected += result.rejected();
            exhausted = result.exhausted();
            long next = result.nextQuestionId();
            if (next == cursor) break;
            cursor = next;
        }
        state.advance(cursor, OffsetDateTime.now());
        return new VisualBackfillResult(before, cursor, scanned, enqueued, unchanged, rejected,
                exhausted);
    }

    private record VisualBackfillResult(long cursorBefore, long cursorAfter, int scanned,
            int enqueued, int unchanged, int rejected, boolean exhausted) {}
}
