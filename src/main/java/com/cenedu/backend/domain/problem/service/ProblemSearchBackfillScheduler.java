package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import com.cenedu.backend.domain.problem.entity.ProblemSearchBackfillState;
import com.cenedu.backend.domain.problem.repository.ProblemSearchBackfillStateRepository;
import java.time.OffsetDateTime;

@Component
public class ProblemSearchBackfillScheduler {
    private final ProblemSearchBackfillService service;
    private final ProblemRagProperties properties;
    private final ProblemSearchBackfillStateRepository stateRepository;
    private static final String STATE_KEY = "problem-search";
    private static final Logger log = LoggerFactory.getLogger(ProblemSearchBackfillScheduler.class);
    public ProblemSearchBackfillScheduler(ProblemSearchBackfillService service, ProblemRagProperties properties,
            ProblemSearchBackfillStateRepository stateRepository) { this.service = service; this.properties = properties; this.stateRepository = stateRepository; }

    /** RAG와 인덱싱이 모두 활성화된 경우에만 커서 기반 backfill을 실행한다. */
    @Scheduled(initialDelayString = "${app.problem.rag.indexing.backfill-initial-delay:10m}",
            fixedDelayString = "${app.problem.rag.indexing.backfill-delay:1h}")
    @Transactional
    public void run() {
        if (!properties.enabled() || !properties.indexing().enabled()) return;
        var state = stateRepository.findByStateKeyForUpdate(STATE_KEY)
                .orElseGet(() -> stateRepository.save(new ProblemSearchBackfillState(STATE_KEY, 0, OffsetDateTime.now())));
        long cursor = state.getCursor();
        long before = cursor;
        long started = System.nanoTime();
        var result = service.enqueueBatch(cursor, properties.indexing().backfillBatchSize());
        state.advance(result.nextQuestionId(), OffsetDateTime.now());
        log.info("event=problem_search_backfill cursorBefore={} cursorAfter={} scanned={} enqueued={} rejected={} exhausted={} elapsedMs={}",
                before, cursor, result.scanned(), result.enqueued(), result.rejected(), result.exhausted(),
                (System.nanoTime() - started) / 1_000_000);
    }
}
