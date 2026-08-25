package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditChangeNature;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.edit.RequestedProblemSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceRetrievalPort;
import com.cenedu.backend.domain.problem.authoring.retrieval.RetrievedProblemReference;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class ProblemModificationReferenceRetrievalTest {

    @Test
    void 교체_생성은_교사_요청과_현재_문항으로_EXAMPLE을_검색한다() {
        Fixture fixture = fixture(true);
        var retrieved = new RetrievedProblemReference(99L, snapshot(99L), 0.9, 1,
                "hash", "cluster", Set.of());
        when(fixture.port().retrieve(any())).thenReturn(List.of(retrieved));

        var references = fixture.coordinator().replacementReferences(
                plan(EditAction.REPLACE), snapshot(77L), fixture.version(), scope());

        ArgumentCaptor<ProblemReferenceQuery> query = ArgumentCaptor.forClass(ProblemReferenceQuery.class);
        verify(fixture.port()).retrieve(query.capture());
        assertThat(query.getValue().queryHint()).isEqualTo("주관식으로 바꾸고 난이도를 높여줘");
        assertThat(query.getValue().originQuestionId()).isEqualTo(77L);
        assertThat(query.getValue().questionType()).isEqualTo(QuestionType.ESSAY);
        assertThat(query.getValue().difficulty()).isEqualTo("high");
        assertThat(references).singleElement().satisfies(reference -> {
            assertThat(reference.sourceQuestionId()).isEqualTo(99L);
            assertThat(reference.role().name()).isEqualTo("EXAMPLE");
        });
    }

    @Test
    void 일반_수정이나_RAG_비활성은_검색하지_않는다() {
        Fixture enabled = fixture(true);
        Fixture disabled = fixture(false);

        assertThat(enabled.coordinator().replacementReferences(
                plan(EditAction.MODIFY), snapshot(77L), enabled.version(), scope())).isEmpty();
        assertThat(disabled.coordinator().replacementReferences(
                plan(EditAction.REPLACE), snapshot(77L), disabled.version(), scope())).isEmpty();

        verify(enabled.port(), never()).retrieve(any());
        verify(disabled.port(), never()).retrieve(any());
    }

    private Fixture fixture(boolean ragEnabled) {
        ProblemAuthoringVersionRepository versions = mock(ProblemAuthoringVersionRepository.class);
        when(versions.findAllBySessionIdOrderByVersionNo(1L)).thenReturn(List.of());
        var coordinator = new ProblemModificationExecutionCoordinator(
                mock(ProblemModificationWorker.class), mock(ProblemAuthoringStateService.class),
                mock(ProblemQuestionSelector.class), mock(ProblemBankSnapshotQueryService.class),
                mock(ProblemAuthoringSessionRepository.class), versions,
                mock(ProblemAuthoringJsonCodec.class), mock(PlatformTransactionManager.class));
        ProblemReferenceRetrievalPort port = mock(ProblemReferenceRetrievalPort.class);
        ProblemRagProperties properties = mock(ProblemRagProperties.class);
        when(properties.enabled()).thenReturn(ragEnabled);
        when(properties.candidateLimit()).thenReturn(40);
        coordinator.setReferenceRetrievalPort(port);
        coordinator.setRagProperties(properties);
        ProblemAuthoringVersion version = mock(ProblemAuthoringVersion.class);
        when(version.getSourceQuestionId()).thenReturn(77L);
        return new Fixture(coordinator, port, version);
    }

    private ProblemEditExecutionPlan plan(EditAction action) {
        var instruction = new ProblemEditInstruction(EditTargetType.WHOLE_QUESTION, null,
                EditChangeNature.STRUCTURAL, "주관식으로 바꾸고 난이도를 높여줘");
        return new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L, action,
                action == EditAction.REPLACE ? ReplacementSourcePolicy.GENERATE_ONLY
                        : ReplacementSourcePolicy.NONE,
                null, List.of(instruction), null,
                List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), new RequestedProblemSpecification(QuestionType.ESSAY, "high"));
    }

    private CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                null, 10L, "수와 연산", "정수와 유리수", "유리수의 계산");
    }

    private QuestionSnapshotV1 snapshot(Long sourceId) {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE, QuestionPresentation.TEXT_ONLY,
                        "mid", 10L, null, null, sourceId),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "현재 문항", null, null)),
                List.of(), List.of(), List.of(), List.of(), "해설", null, List.of());
    }

    private record Fixture(ProblemModificationExecutionCoordinator coordinator,
                           ProblemReferenceRetrievalPort port,
                           ProblemAuthoringVersion version) {}
}
