package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ProblemSearchBackfillScheduler {
    private final ProblemSearchBackfillService service;
    private final ProblemRagProperties properties;
    private long cursor;
    private static final Logger log = LoggerFactory.getLogger(ProblemSearchBackfillScheduler.class);
    public ProblemSearchBackfillScheduler(ProblemSearchBackfillService service, ProblemRagProperties properties) { this.service = service; this.properties = properties; }

    /** RAG와 인덱싱이 모두 활성화된 경우에만 커서 기반 backfill을 실행한다. */
    @Scheduled(initialDelayString = "${app.problem.rag.indexing.backfill-initial-delay:10m}",
            fixedDelayString = "${app.problem.rag.indexing.backfill-delay:1h}")
    public void run() {
        if (!properties.enabled() || !properties.indexing().enabled()) return;
        long before = cursor;
        long started = System.nanoTime();
        var result = service.enqueueBatch(cursor, properties.indexing().backfillBatchSize());
        cursor = result.nextQuestionId();
        log.info("event=problem_search_backfill cursorBefore={} cursorAfter={} scanned={} enqueued={} rejected={} exhausted={} elapsedMs={}",
                before, cursor, result.scanned(), result.enqueued(), result.rejected(), result.exhausted(),
                (System.nanoTime() - started) / 1_000_000);
    }
}
