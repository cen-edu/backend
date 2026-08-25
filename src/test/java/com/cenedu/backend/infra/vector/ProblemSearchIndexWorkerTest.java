package com.cenedu.backend.infra.vector;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import com.cenedu.backend.ai.embedding.EmbeddingClient;
import com.cenedu.backend.domain.problem.authoring.search.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.service.*;
import org.junit.jupiter.api.Test;

class ProblemSearchIndexWorkerTest {
    @Test
    void 더_높은_READY_schema가_있으면_embedding_없이_skip한다() {
        var repository = mock(ProblemSearchIndexJdbcRepository.class);
        var factory = mock(ProblemSearchDocumentFactory.class);
        var embedding = mock(EmbeddingClient.class);
        var eligibility = mock(ProblemSearchCorpusEligibilityService.class);
        var properties = mock(ProblemRagProperties.class);
        var indexing = mock(ProblemRagProperties.Indexing.class);
        when(properties.indexing()).thenReturn(indexing);
        var command = mock(SearchIndexingCommand.class);
        var snapshot = mock(QuestionSnapshotV1.class);
        var task = mock(ProblemSearchIndexJdbcRepository.ClaimedSearchIndexTask.class);
        when(task.command()).thenReturn(command);
        when(task.taskId()).thenReturn(10L);
        when(task.questionId()).thenReturn(20L);
        when(task.attemptCount()).thenReturn(1);
        when(command.snapshot()).thenReturn(snapshot);
        when(command.assetStorageKeys()).thenReturn(java.util.Map.of());
        when(command.indexSchemaVersion()).thenReturn((short) 1);
        when(eligibility.evaluate(any(), any())).thenReturn(SearchCorpusEligibility.READY);
        when(repository.findReadyMetadata(20L)).thenReturn(Optional.of(new ProblemSearchIndexJdbcRepository.ReadySearchIndexMetadata("hash", (short) 2)));

        var worker = new ProblemSearchIndexWorker(repository, factory, embedding, eligibility, properties);
        assertThatCode(() -> worker.runOne(task)).doesNotThrowAnyException();

        verify(repository).markSkipped(10L);
        verifyNoInteractions(embedding);
        verifyNoInteractions(factory);
    }
}
