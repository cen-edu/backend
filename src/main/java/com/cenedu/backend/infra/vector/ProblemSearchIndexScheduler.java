package com.cenedu.backend.infra.vector;

import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProblemSearchIndexScheduler {
    private final ProblemSearchIndexWorker worker;
    private final ProblemRagProperties properties;
    public ProblemSearchIndexScheduler(ProblemSearchIndexWorker worker, ProblemRagProperties properties) { this.worker = worker; this.properties = properties; }

    /** 기능 플래그가 켜진 경우에만 검색 인덱싱 작업을 주기적으로 처리한다. */
    @Scheduled(fixedDelayString = "${app.problem.rag.indexing.worker-delay:5s}")
    public void process() { if (properties.enabled() && properties.indexing().enabled()) worker.runPending(); }
}
