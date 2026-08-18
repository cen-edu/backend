package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemAuthoringStateServiceTest {

    @Mock
    private ProblemAuthoringSessionRepository sessionRepository;

    @Mock
    private ProblemAuthoringVersionRepository versionRepository;

    private ProblemAuthoringStateService service;

    @BeforeEach
    void setUp() {
        service = new ProblemAuthoringStateService(sessionRepository, versionRepository);
    }

    @Test
    @DisplayName("PASSED Version만 Session의 current로 승격한다")
    void promotesOnlyPassedVersion() {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(7L);
        session.attachPendingVersion(11L);
        ProblemAuthoringVersion version = version(1);
        version.startVerification(UUID.randomUUID());
        version.passVerification("{}");
        when(sessionRepository.findByIdAndOwnerTeacherId(3L, 7L))
                .thenReturn(Optional.of(session));
        when(versionRepository.findByIdAndSessionId(11L, 3L))
                .thenReturn(Optional.of(version));

        service.promotePassedVersion(7L, 3L, 11L);

        assertThat(session.getCurrentVersionId()).isEqualTo(11L);
        assertThat(session.getPendingVersionId()).isNull();
    }

    @Test
    @DisplayName("검증 전 Version은 current로 승격하지 않는다")
    void rejectsUnverifiedVersion() {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(7L);
        session.attachPendingVersion(11L);
        ProblemAuthoringVersion version = version(1);
        when(sessionRepository.findByIdAndOwnerTeacherId(3L, 7L))
                .thenReturn(Optional.of(session));
        when(versionRepository.findByIdAndSessionId(11L, 3L))
                .thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.promotePassedVersion(7L, 3L, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        assertThat(session.getCurrentVersionId()).isNull();
        assertThat(session.getPendingVersionId()).isEqualTo(11L);
    }

    private ProblemAuthoringVersion version(int versionNo) {
        return ProblemAuthoringVersion.create(
                3L, versionNo, null, UUID.randomUUID(),
                AuthoringOperationType.AI_GENERATE, null,
                1, "{}", "{}", "생성 후보");
    }
}
