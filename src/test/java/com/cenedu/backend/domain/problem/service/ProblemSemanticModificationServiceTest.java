package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.global.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.*;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingRequest;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import java.util.*;

class ProblemSemanticModificationServiceTest {
    @Test
    void null_base_or_patch는_실행하지_않는다() {
        var service = new ProblemSemanticModificationService(mock(ProblemAuthoringJsonCodec.class),
                mock(ProblemSemanticMaterializer.class), mock(ProblemCandidateProcessingService.class));
        assertThatThrownBy(() -> service.apply(7L, 31L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void semantic_model이_없는_version은_지원불가로_종료한다() {
        var service = new ProblemSemanticModificationService(mock(ProblemAuthoringJsonCodec.class),
                mock(ProblemSemanticMaterializer.class), mock(ProblemCandidateProcessingService.class));
        var version = mock(com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion.class);
        whenId(version, 20L);
        var patch = new com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticPatch(
                1, java.util.UUID.randomUUID(), 20L,
                com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode.PARAMETRIC_PATCH,
                java.util.List.of(), "확인");
        assertThatThrownBy(() -> service.apply(7L, 31L, version, patch))
                .isInstanceOf(BusinessException.class);
    }

    private void whenId(com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion version, Long id) {
        org.mockito.Mockito.when(version.getId()).thenReturn(id);
        org.mockito.Mockito.when(version.getSemanticModel()).thenReturn(null);
    }

    @Test
    void parameter_patch는_정규화_model과_영향영역을_후속검증에_전달한다() throws Exception {
        var jsonCodec = mock(ProblemAuthoringJsonCodec.class);
        var materializer = mock(ProblemSemanticMaterializer.class);
        var processing = mock(ProblemCandidateProcessingService.class);
        var service = new ProblemSemanticModificationService(jsonCodec, materializer, processing);
        var base = semanticModel("3");
        var changed = semanticModel("5");
        var version = mock(com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion.class);
        when(version.getId()).thenReturn(20L);
        when(version.getSemanticModel()).thenReturn(new tools.jackson.databind.ObjectMapper().writeValueAsString(base));
        when(version.getSnapshot()).thenReturn("snapshot");
        when(jsonCodec.read(eq("snapshot"), eq(com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1.class)))
                .thenReturn(ProblemSnapshotFixtures.shortInput());
        var materialized = new MaterializedProblem(ProblemSnapshotFixtures.shortInput(), List.of(),
                new SemanticMaterializationReport(1, List.of(), Map.of("A", "5"), Set.of("A"), Set.of()));
        when(materializer.materialize(any())).thenReturn(materialized);
        when(processing.process(any())).thenReturn(new CandidateProcessingResult(21L, 2, UUID.randomUUID(),
                VerificationOverallStatus.PASSED, null, true));
        var patch = new ProblemSemanticPatch(1, UUID.randomUUID(), 20L, SemanticEditMode.PARAMETRIC_PATCH,
                List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE,
                        "/parameters/A/value", "3", "5")), "변경을 확인해 주세요.");

        var result = service.apply(7L, 31L, version, patch);

        ArgumentCaptor<CandidateProcessingRequest> captor = ArgumentCaptor.forClass(CandidateProcessingRequest.class);
        org.mockito.Mockito.verify(processing).process(captor.capture());
        assertThat(captor.getValue().candidate().semanticModel().parameters().getFirst().value()).isEqualTo("5");
        assertThat(result.previewVersionId()).isEqualTo(21L);
        assertThat(result.diff().impactedAreas()).contains(SemanticImpactArea.ANSWERS, SemanticImpactArea.STEM);
        assertThat(result.promoted()).isTrue();
    }

    private ProblemSemanticModelV1 semanticModel(String value) {
        var parameter = new SemanticParameter("A", SemanticValueType.INTEGER, value, null, true, null);
        var computation = new SemanticComputation("C", SemanticOperation.IDENTITY, List.of("A"), null, null, value);
        var intent = new SemanticProblemIntent(com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT,
                "mid", null, "identity", "C", 1, false);
        var presentation = new SemanticPresentationPlan("${A}cm", List.of(), List.of(), "${C}", null, List.of());
        return new ProblemSemanticModelV1(1, new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "a", "b", "c"),
                intent, List.of(parameter), List.of(computation), List.of(), presentation, List.of(), List.of());
    }
}
