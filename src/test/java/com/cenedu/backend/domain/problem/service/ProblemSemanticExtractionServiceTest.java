package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticExtractionPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.*;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.SemanticModelStatus;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.*;
import com.cenedu.backend.global.common.enums.QuestionType;

class ProblemSemanticExtractionServiceTest {
    @Test
    void unsupported는_provider를_다시_호출하지_않는다() {
        var questions = mock(ProblemQuestionRepository.class);
        var versions = mock(ProblemAuthoringVersionRepository.class);
        var port = mock(ProblemSemanticExtractionPort.class);
        var materializer = mock(ProblemSemanticMaterializer.class);
        var question = mock(ProblemQuestion.class);
        when(question.getSemanticModelStatus()).thenReturn(SemanticModelStatus.UNSUPPORTED);
        when(questions.findById(41L)).thenReturn(Optional.of(question));
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        var service = new ProblemSemanticExtractionService(questions, versions, port, materializer, tx,
                new ObjectMapper());

        var result = service.ensureQuestionSemantic(41L, scope(), ProblemSnapshotFixtures.shortInput());

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.UNSUPPORTED);
        verifyNoInteractions(port);
    }

    @Test
    void version_extraction은_owner가_다르면_거부한다() {
        var questions = mock(ProblemQuestionRepository.class);
        var versions = mock(ProblemAuthoringVersionRepository.class);
        var sessions = mock(com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository.class);
        var port = mock(ProblemSemanticExtractionPort.class);
        var tx = mock(PlatformTransactionManager.class);
        when(sessions.findByIdAndOwnerTeacherId(31L, 7L)).thenReturn(Optional.empty());
        var service = new ProblemSemanticExtractionService(questions, versions, sessions, port,
                mock(ProblemSemanticMaterializer.class), tx, new ObjectMapper());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ensureVersionSemantic(
                7L, 31L, 41L, scope())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(port, versions);
    }

    @Test
    void ready_model은_provider를_호출하지_않고_재사용한다() throws Exception {
        var questions = mock(ProblemQuestionRepository.class);
        var question = mock(ProblemQuestion.class);
        var model = model();
        when(question.getSemanticModelStatus()).thenReturn(SemanticModelStatus.READY);
        when(question.getSemanticModel()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(model));
        when(questions.findById(41L)).thenReturn(Optional.of(question));
        var port = mock(ProblemSemanticExtractionPort.class);
        var materializer = mock(ProblemSemanticMaterializer.class);
        when(materializer.materialize(any())).thenReturn(new MaterializedProblem(
                ProblemSnapshotFixtures.shortInput(), List.of(),
                new SemanticMaterializationReport(1, List.of(), Map.of(), Set.of(), Set.of())));
        var tx = mock(PlatformTransactionManager.class);
        var service = new ProblemSemanticExtractionService(questions,
                mock(ProblemAuthoringVersionRepository.class), port, materializer, tx, new ObjectMapper());

        var result = service.ensureQuestionSemantic(41L, scope(), ProblemSnapshotFixtures.shortInput());

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.EXTRACTED);
        verifyNoInteractions(port);
    }

    @Test
    void version와_question에_동시에_canonical_model을_저장한다() throws Exception {
        var questions = mock(ProblemQuestionRepository.class);
        var versions = mock(ProblemAuthoringVersionRepository.class);
        var sessions = mock(com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository.class);
        var question = mock(ProblemQuestion.class);
        var version = mock(com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion.class);
        var model = model();
        when(sessions.findByIdAndOwnerTeacherId(31L, 7L)).thenReturn(Optional.of(mock(com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession.class)));
        when(versions.findByIdAndSessionId(41L, 31L)).thenReturn(Optional.of(version));
        when(version.getSourceQuestionId()).thenReturn(51L);
        when(version.getSnapshot()).thenReturn(new ObjectMapper().writeValueAsString(ProblemSnapshotFixtures.shortInput()));
        when(questions.findById(51L)).thenReturn(Optional.of(question));
        when(question.getSemanticModelStatus()).thenReturn(SemanticModelStatus.ABSENT);
        when(questions.findByIdForUpdate(51L)).thenReturn(Optional.of(question));
        var materializer = mock(ProblemSemanticMaterializer.class);
        when(materializer.materialize(any())).thenReturn(new MaterializedProblem(
                ProblemSnapshotFixtures.shortInput(), List.of(),
                new SemanticMaterializationReport(1, List.of(), Map.of(), Set.of(), Set.of())));
        var port = mock(ProblemSemanticExtractionPort.class);
        when(port.extract(any())).thenReturn(new SemanticExtractionResult(
                SemanticExtractionStatus.EXTRACTED, model, List.of()));
        var tx = mock(PlatformTransactionManager.class);
        var service = new ProblemSemanticExtractionService(questions, versions, sessions, port,
                materializer, tx, new ObjectMapper());

        service.ensureVersionSemantic(7L, 31L, 41L, scope());

        verify(question).attachSemanticModel(any());
        verify(version).attachSemanticModel(any());
    }

    private ProblemSemanticModelV1 model() {
        var p = new SemanticParameter("A", SemanticValueType.INTEGER, "1", null, false, null);
        var c = new SemanticComputation("C", SemanticOperation.IDENTITY, List.of("A"), null, null, "1");
        var i = new SemanticProblemIntent(QuestionType.SHORT_INPUT, "mid", null, "identity", "C", 1, false);
        var presentation = new SemanticPresentationPlan("${A}", List.of(), List.of(), "${C}", null, List.of());
        return new ProblemSemanticModelV1(1, scope(), i, List.of(p), List.of(c), List.of(), presentation, List.of(), List.of());
    }

    private CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                "수와 연산", "사칙연산", "덧셈");
    }
}
