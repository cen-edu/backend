package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProblemAuthoringFinalizationServiceTest {
    @Mock private ProblemAuthoringSessionRepository sessionRepository;
    @Mock private ProblemAuthoringVersionRepository versionRepository;
    @Mock private ProblemQuestionRepository questionRepository;
    @Mock private ProblemChoiceRepository choiceRepository;
    @Mock private ProblemStepRepository stepRepository;
    @Mock private ProblemAnswerUnitRepository answerUnitRepository;
    @Mock private ProblemRubricItemRepository rubricRepository;
    @Mock private ProblemAssetRepository assetRepository;
    @Mock private ProblemSnapshotEntityMapper mapper;
    private ProblemAuthoringFinalizationService service;

    @BeforeEach
    void setUp() {
        service = new ProblemAuthoringFinalizationService(sessionRepository, versionRepository,
                questionRepository, choiceRepository, stepRepository, answerUnitRepository,
                rubricRepository, assetRepository, mapper, new ObjectMapper());
    }

    @Test
    void 여러_session_중_하나라도_준비되지_않으면_저장을_시작하지_않는다() {
        ProblemAuthoringSession ready = mock(ProblemAuthoringSession.class);
        when(ready.getOwnerTeacherId()).thenReturn(7L);
        ProblemAuthoringSession failed = mock(ProblemAuthoringSession.class);
        when(failed.getOwnerTeacherId()).thenReturn(7L);
        when(failed.getLifecycleStatus()).thenReturn(com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus.DRAFT);
        when(failed.getCurrentVersionId()).thenReturn(11L);
        lenient().when(failed.getOperationStatus()).thenReturn(com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus.VERIFYING);
        when(sessionRepository.findAllForFinalization(List.of(1L, 2L))).thenReturn(List.of(failed, ready));

        assertThatThrownBy(() -> service.finalizeForWorksheet(7L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        verifyNoInteractions(questionRepository, choiceRepository, stepRepository,
                answerUnitRepository, rubricRepository, assetRepository, mapper);
    }
}
