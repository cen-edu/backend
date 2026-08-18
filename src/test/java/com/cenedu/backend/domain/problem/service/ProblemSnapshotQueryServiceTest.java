package com.cenedu.backend.domain.problem.service;

import static com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures.shortInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cenedu.backend.domain.problem.dto.response.AuthoringProblemReferenceResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ProblemSnapshotQueryServiceTest {

    private ProblemAuthoringSession session;
    private ProblemAuthoringVersion version;
    private ProblemSnapshotQueryService service;

    @BeforeEach
    void setUp() {
        ProblemAuthoringSessionRepository sessionRepository =
                mock(ProblemAuthoringSessionRepository.class);
        ProblemAuthoringVersionRepository versionRepository =
                mock(ProblemAuthoringVersionRepository.class);
        ProblemAuthoringJsonCodec codec = new ProblemAuthoringJsonCodec(new ObjectMapper());
        session = ProblemAuthoringSession.createIdle(7L);
        ReflectionTestUtils.setField(session, "id", 3L);
        version = ProblemAuthoringVersion.create(
                3L, 1, null, UUID.randomUUID(),
                AuthoringOperationType.AI_GENERATE, null,
                1, codec.write(shortInput()), codec.write(
                        com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest
                                .planned(List.of())), "생성");
        ReflectionTestUtils.setField(version, "id", 10L);
        version.startVerification(UUID.randomUUID());
        version.passVerification("{}");
        session.attachPendingVersion(10L);
        session.promotePendingVersion(10L, AuthoringVerificationStatus.PASSED);
        when(sessionRepository.findByIdAndOwnerTeacherId(3L, 7L))
                .thenReturn(Optional.of(session));
        when(versionRepository.findByIdAndSessionId(10L, 3L))
                .thenReturn(Optional.of(version));
        service = new ProblemSnapshotQueryService(
                sessionRepository, versionRepository, codec);
    }

    @Test
    @DisplayName("현재 PASSED Version을 Entity가 아닌 S1 응답으로 반환한다")
    void returnsPassedCurrentSnapshot() {
        var response = service.getCurrent(7L, 3L);
        var status = service.getStatus(7L, 3L);

        assertThat(response.versionId()).isEqualTo(10L);
        assertThat(response.snapshot()).isEqualTo(shortInput());
        assertThat(status.readyForFinalization()).isTrue();
    }

    @Test
    @DisplayName("기존 questionId와 작성 sessionId를 동시에 사용할 수 없다")
    void referenceUsesExactlyOneIdentifier() {
        assertThat(AuthoringProblemReferenceResponse.existingQuestion(9L).sessionId())
                .isNull();
        assertThat(AuthoringProblemReferenceResponse.authoringSession(3L).questionId())
                .isNull();
        assertThatThrownBy(() -> new AuthoringProblemReferenceResponse(
                com.cenedu.backend.domain.problem.dto.response.ProblemReferenceType
                        .EXISTING_QUESTION,
                9L, 3L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("현재 Version이 검증 통과가 아니면 S1을 공개하지 않는다")
    void rejectsUnverifiedCurrentVersion() {
        ReflectionTestUtils.setField(
                version, "verificationStatus", AuthoringVerificationStatus.ERROR);

        assertThatThrownBy(() -> service.getCurrent(7L, 3L))
                .isInstanceOf(BusinessException.class);
    }
}
