package com.cenedu.backend.infra.vector;

import com.cenedu.backend.ai.embedding.EmbeddingCallException;
import com.cenedu.backend.ai.embedding.EmbeddingClient;
import com.cenedu.backend.ai.embedding.EmbeddingResult;
import com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocument;
import com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocumentFactory;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.service.ProblemSearchCorpusEligibilityService;
import com.cenedu.backend.domain.problem.service.SearchCorpusEligibility;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ProblemSearchIndexWorker {
    private final ProblemSearchIndexJdbcRepository repository;
    private final ProblemSearchDocumentFactory documentFactory;
    private final EmbeddingClient embeddingClient;
    private final ProblemSearchCorpusEligibilityService eligibilityService;
    private final ProblemRagProperties properties;

    public ProblemSearchIndexWorker(ProblemSearchIndexJdbcRepository repository,
            ProblemSearchDocumentFactory documentFactory, EmbeddingClient embeddingClient,
            ProblemSearchCorpusEligibilityService eligibilityService, ProblemRagProperties properties) {
        this.repository = repository; this.documentFactory = documentFactory; this.embeddingClient = embeddingClient;
        this.eligibilityService = eligibilityService; this.properties = properties;
    }

    /** 현재 처리 가능한 작업을 설정된 batch 크기까지만 처리하고 처리 수를 반환한다. */
    public int runPending() {
        if (!properties.enabled() || !properties.indexing().enabled()) return 0;
        var tasks = repository.claimDue(Instant.now(), properties.indexing().batchSize());
        tasks.forEach(this::runOne);
        return tasks.size();
    }

    /** 이미 원자적으로 선점한 한 작업을 READY, SKIPPED, RETRY_WAIT 또는 FAILED로 끝낸다. */
    public void runOne(ProblemSearchIndexJdbcRepository.ClaimedSearchIndexTask task) {
        try {
            SearchCorpusEligibility eligibility = eligibilityService.evaluate(task.command().snapshot(), java.util.Map.of());
            if (eligibility == SearchCorpusEligibility.WAITING_FOR_ASSETS) {
                repository.markRetry(task.taskId(), task.attemptCount(), Instant.now().plus(properties.indexing().retryDelay()), "ASSETS_NOT_READY"); return;
            }
            if (eligibility == SearchCorpusEligibility.REJECTED) { repository.markFailed(task.taskId(), task.attemptCount(), "CORPUS_REJECTED"); return; }
            ProblemSearchDocument document = documentFactory.create(task.command());
            var ready = repository.findReadyMetadata(task.questionId());
            if (ready.isPresent() && ready.get().documentHash().equals(document.documentHash())) {
                repository.markSkipped(task.taskId()); return;
            }
            EmbeddingResult embedding = embeddingClient.embed(document.documentText());
            String vector = VectorCodec.encode(embedding.vector());
            repository.upsertReady(task, document, embedding, vector);
            repository.markReady(task.taskId());
        } catch (EmbeddingCallException exception) {
            if (exception.retryable() && task.attemptCount() < properties.indexing().maxAttempts()) {
                repository.markRetry(task.taskId(), task.attemptCount(), Instant.now().plus(properties.indexing().retryDelay()), "EMBEDDING_RETRYABLE");
            } else repository.markFailed(task.taskId(), task.attemptCount(), "EMBEDDING_FAILED");
        } catch (IllegalArgumentException exception) {
            repository.markFailed(task.taskId(), task.attemptCount(), "EMBEDDING_DIMENSION_INVALID");
        } catch (RuntimeException exception) {
            repository.markFailed(task.taskId(), task.attemptCount(), "INDEXING_FAILED");
        }
    }
}
