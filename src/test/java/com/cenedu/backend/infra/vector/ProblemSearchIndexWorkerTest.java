package com.cenedu.backend.infra.vector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cenedu.backend.ai.embedding.EmbeddingClient;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocument;
import com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocumentFactory;
import com.cenedu.backend.domain.problem.authoring.search.SearchIndexingCommand;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.service.ProblemSearchCorpusEligibilityService;
import com.cenedu.backend.domain.problem.service.SearchCorpusEligibility;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProblemSearchIndexWorkerTest {

    @Test
    void 본문_해시가_같아도_v2_메타데이터를_갱신하고_임베딩은_재호출하지_않는다() {
        ProblemSearchIndexJdbcRepository repository = mock(ProblemSearchIndexJdbcRepository.class);
        ProblemSearchDocumentFactory documentFactory = mock(ProblemSearchDocumentFactory.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        ProblemSearchCorpusEligibilityService eligibilityService =
                mock(ProblemSearchCorpusEligibilityService.class);
        SearchIndexingCommand command = mock(SearchIndexingCommand.class);
        QuestionSnapshotV1 snapshot = mock(QuestionSnapshotV1.class);
        ProblemSearchDocument document = new ProblemSearchDocument(
                "동일 본문", "same-hash", "duplicate", "family", "strategy", "summary",
                VisualReferenceKind.COORDINATE_GRAPH);
        var task = new ProblemSearchIndexJdbcRepository.ClaimedSearchIndexTask(31L, 77L, command, 1);

        when(command.assetStorageKeys()).thenReturn(Map.of());
        when(command.snapshot()).thenReturn(snapshot);
        when(command.indexSchemaVersion()).thenReturn((short) 2);
        when(snapshot.assets()).thenReturn(List.of());
        when(eligibilityService.evaluate(any(), any())).thenReturn(SearchCorpusEligibility.READY);
        when(repository.findReadyMetadata(77L)).thenReturn(java.util.Optional.of(
                new ProblemSearchIndexJdbcRepository.ReadySearchIndexMetadata("same-hash", (short) 1)));
        when(documentFactory.create(command)).thenReturn(document);

        var worker = new ProblemSearchIndexWorker(repository, documentFactory, embeddingClient,
                eligibilityService, properties());
        worker.runOne(task);

        verify(repository).refreshReadyMetadata(task, document);
        verify(repository).markReady(31L);
        verify(repository, never()).markSkipped(31L);
        verifyNoInteractions(embeddingClient);
    }

    private ProblemRagProperties properties() {
        return new ProblemRagProperties(true, "A_DENSE_MMR_V1", 40, 3, 4, 0.70, 0.55,
                Duration.ofSeconds(2), new ProblemRagProperties.Indexing(true, 20, 3,
                Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMinutes(10),
                Duration.ofHours(1), 50));
    }
}
