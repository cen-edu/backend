package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.global.common.BusinessException;
import org.junit.jupiter.api.Test;

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
}
