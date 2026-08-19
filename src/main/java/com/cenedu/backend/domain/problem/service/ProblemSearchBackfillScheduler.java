package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProblemSearchBackfillScheduler {
    private final ProblemSearchBackfillService service;
    private final ProblemRagProperties properties;
    private long cursor;
    public ProblemSearchBackfillScheduler(ProblemSearchBackfillService service, ProblemRagProperties properties) { this.service = service; this.properties = properties; }

    /** RAG와 인덱싱이 모두 활성화된 경우에만 커서 기반 backfill을 실행한다. */
    @Scheduled(fixedDelayString = "${app.problem.rag.indexing.backfill-delay:60s}")
    public void run() {
        if (!properties.enabled() || !properties.indexing().enabled()) return;
        var result = service.enqueueBatch(cursor, properties.indexing().batchSize());
        cursor = result.exhausted() ? 0 : result.nextQuestionId();
    }
}
