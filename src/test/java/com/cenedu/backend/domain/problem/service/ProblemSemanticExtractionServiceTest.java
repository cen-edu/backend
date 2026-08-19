package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Optional;
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

    private CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                "수와 연산", "사칙연산", "덧셈");
    }
}
