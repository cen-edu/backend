package com.cenedu.backend.domain.problem.service;

import static com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures.shortInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.edit.ConfirmedProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditChangeNature;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.PendingProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ProblemEditConversationServiceTest {

    private ProblemAuthoringSession session;
    private ProblemEditConversationService service;

    @BeforeEach
    void setUp() {
        ProblemAuthoringSessionRepository sessionRepository =
                mock(ProblemAuthoringSessionRepository.class);
        ProblemAuthoringVersionRepository versionRepository =
                mock(ProblemAuthoringVersionRepository.class);
        ProblemAuthoringJsonCodec codec = new ProblemAuthoringJsonCodec(new ObjectMapper());

        session = ProblemAuthoringSession.createIdle(7L);
        ReflectionTestUtils.setField(session, "id", 3L);
        session.attachPendingVersion(10L);
        session.promotePendingVersion(
                10L,
                com.cenedu.backend.domain.problem.entity.enums
                        .AuthoringVerificationStatus.PASSED);
        ProblemAuthoringVersion version = ProblemAuthoringVersion.create(
                3L, 1, null, UUID.randomUUID(),
                AuthoringOperationType.AI_GENERATE, null,
                1, codec.write(shortInput()), codec.write(
                        com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest
                                .planned(List.of())), "생성");
        ReflectionTestUtils.setField(version, "id", 10L);
        version.startVerification(UUID.randomUUID());
        version.passVerification("{}");

        when(sessionRepository.findOwnedByIdForUpdate(3L, 7L))
                .thenReturn(Optional.of(session));
        when(versionRepository.findByIdAndSessionId(10L, 3L))
                .thenReturn(Optional.of(version));
        service = new ProblemEditConversationService(
                sessionRepository, versionRepository, codec, new ProblemEditPolicy());
    }

    @Test
    @DisplayName("교사가 대기 명령과 동일한 수정을 확인해야 실행 계획이 활성화된다")
    void activatesOnlyConfirmedCommand() {
        UUID requestId = UUID.randomUUID();
        List<ProblemEditInstruction> instructions = List.of(
                new ProblemEditInstruction(
                        EditTargetType.EXPLANATION, null,
                        EditChangeNature.PRESENTATIONAL, "해설을 간결하게"));
        PendingProblemEditCommand pending = new PendingProblemEditCommand(
                requestId, 3L, 10L, instructions,
                null, null, ReplacementSourcePolicy.NONE);
        ConfirmedProblemEditCommand confirmed = new ConfirmedProblemEditCommand(
                requestId, UUID.randomUUID(), 3L, 10L, instructions,
                null, null, ReplacementSourcePolicy.NONE);

        service.start(7L, 3L);
        service.requestConfirmation(7L, pending);
        var plan = service.confirm(7L, confirmed);

        assertThat(plan.action()).isEqualTo(EditAction.MODIFY);
        assertThat(session.getOperationStatus()).isEqualTo(AuthoringOperationStatus.MODIFYING);
        assertThat(session.getActiveRequestId()).isEqualTo(requestId);
        assertThatThrownBy(() -> service.confirm(7L, confirmed))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROBLEM_EDIT_COMMAND_STALE));
    }
}
