package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import org.junit.jupiter.api.Test;
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
}
