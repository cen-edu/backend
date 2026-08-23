package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticPatch;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ProblemStructuralRegenerationServiceTest {
    @Test
    void generation_port가_없으면_구조재생성을_실행하지_않는다() {
        ObjectProvider<ProblemGenerationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        var service = new ProblemStructuralRegenerationService(provider,
                mock(ProblemCandidateProcessingService.class), mock(ProblemAuthoringJsonCodec.class),
                mock(ProblemAuthoringVersionRepository.class));
        assertThatThrownBy(() -> service.regenerate(7L, null, null, null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 원본에_도형이_있으면_같은_kind로_PRESERVE_ORIGIN을_요청한다() {
        ProblemGenerationPort port = mock(ProblemGenerationPort.class);
        ObjectProvider<ProblemGenerationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        var processingService = mock(ProblemCandidateProcessingService.class);
        var jsonCodec = mock(ProblemAuthoringJsonCodec.class);
        var versionRepository = mock(ProblemAuthoringVersionRepository.class);
        var service = new ProblemStructuralRegenerationService(provider, processingService, jsonCodec, versionRepository);

        var baseVersion = mock(ProblemAuthoringVersion.class);
        when(baseVersion.getId()).thenReturn(475L);
        when(baseVersion.getSnapshot()).thenReturn("snapshot");
        when(baseVersion.getSourceQuestionId()).thenReturn(5603L);
        var baseSnapshot = ProblemSnapshotFixtures.shortInput();
        when(jsonCodec.read("snapshot", QuestionSnapshotV1.class)).thenReturn(baseSnapshot);

        var requestId = UUID.randomUUID();
        var semanticPatch = new ProblemSemanticPatch(1, requestId, 475L, SemanticEditMode.STRUCTURAL_REGENERATION,
                List.of(), "그래프의 기울기를 2에서 3으로 변경하려면 문제 구조와 그래프 관련 값을 함께 재생성해야 합니다.");
        var plan = new ProblemEditExecutionPlan(requestId, 426L, 475L,
                EditAction.REPLACE, ReplacementSourcePolicy.NONE, null, List.of(), semanticPatch,
                List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), null);
        var baseModel = semanticModelWithCoordinateGraph();

        var expectedCandidate = new ProblemCandidateDraft(plan.requestId(), baseSnapshot, List.of(),
                mock(ProblemSemanticModelV1.class),
                new com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance(
                        com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType.AI_GENERATE, null, List.of()));
        when(port.generate(any())).thenReturn(expectedCandidate);
        when(processingService.process(any())).thenReturn(new CandidateProcessingResult(
                999L, 2, UUID.randomUUID(), VerificationOverallStatus.PASSED, null, true));

        service.regenerate(7L, baseVersion, plan, baseModel);

        ArgumentCaptor<ProblemGenerationCommand> captor = ArgumentCaptor.forClass(ProblemGenerationCommand.class);
        org.mockito.Mockito.verify(port).generate(captor.capture());
        var visualRequirement = captor.getValue().specification().visualRequirement();
        assertThat(visualRequirement.mode()).isEqualTo(VisualGenerationMode.PRESERVE_ORIGIN);
        assertThat(visualRequirement.requiredKind()).isEqualTo(VisualReferenceKind.COORDINATE_GRAPH);

        // STRUCTURAL_REGENERATION은 operations·instructions가 항상 비어 있어야 해서, 교사의 실제
        // 요청은 semanticPatch.assistantMessage에만 남아 있다. 이게 재생성 LLM에 전달되지 않으면
        // origin과 거의 동일한 후보가 나온다(값 변경이 반영되지 않음).
        assertThat(captor.getValue().editInstruction())
                .isEqualTo("그래프의 기울기를 2에서 3으로 변경하려면 문제 구조와 그래프 관련 값을 함께 재생성해야 합니다.");

        // generation port는 항상 AI_GENERATE로 후보를 만들지만, 이 서비스는 AI_MODIFY Version으로
        // 등록하므로 processingService에 넘기기 전에 출처를 AI_MODIFY로 다시 붙여야 한다
        // (그렇지 않으면 ProblemCandidateProcessingService.validateSourceType이 거부한다).
        ArgumentCaptor<com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingRequest> requestCaptor =
                ArgumentCaptor.forClass(com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingRequest.class);
        org.mockito.Mockito.verify(processingService).process(requestCaptor.capture());
        assertThat(requestCaptor.getValue().candidate().provenance().sourceType())
                .isEqualTo(com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType.AI_MODIFY);
    }

    private ProblemSemanticModelV1 semanticModelWithCoordinateGraph() {
        var intent = new SemanticProblemIntent(QuestionType.MULTIPLE_CHOICE, "mid", null,
                "그래프에서 관계식을 찾는다.", "RELATION_1", 2, true);
        var presentation = new SemanticPresentationPlan("다음 그래프가 나타내는 관계식으로 알맞은 것을 고르시오.",
                List.of(), List.of(), "", null, List.of());
        var diagram = new CoordinateGraphDiagramSpecV1(1, "F1", DiagramKind.COORDINATE_GRAPH,
                new DiagramViewport(600, 420, 40),
                new DiagramStyle("#222222", "#FFFFFF", "#1769AA", 2, "sans-serif", 14),
                "X_MIN", "X_MAX", "Y_MIN", "Y_MAX", "X_TICK", "Y_TICK", List.of(), List.of(), List.of(),
                List.of(new CoordinateFunctionSpec("G1", CoordinateFunctionKind.DIRECT_PROPORTION, "K_GRAPH", "")));
        return new ProblemSemanticModelV1(1,
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 21L, "변화와 관계", "그래프와 비례관계", "정비례와 반비례"),
                intent, List.of(), List.of(), List.of(), presentation, List.of(diagram), List.of());
    }
}
