package com.cenedu.backend.domain.problem.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;

class ProblemAuthoringSessionEditRecoveryTest {

    @Test
    void 실행_중_중단된_수정은_회수_후_다시_수집할_수_있다() {
        ProblemAuthoringSession session = editableSession();
        activateEdit(session);
        assertThat(session.getOperationStatus()).isEqualTo(AuthoringOperationStatus.MODIFYING);

        session.abortActiveExecution("MODIFICATION_FAILED");

        assertThat(session.getOperationStatus()).isEqualTo(AuthoringOperationStatus.FAILED);
        assertThat(session.getPendingVersionId()).isNull();
        assertThat(session.getLastErrorCode()).isEqualTo("MODIFICATION_FAILED");
        assertThatCode(session::startCollecting).doesNotThrowAnyException();
        assertThat(session.getInteractionStatus()).isEqualTo(AuthoringInteractionStatus.COLLECTING);
    }

    @Test
    void 회수되지_못한_MODIFYING도_다음_수정_턴에서_다시_열린다() {
        ProblemAuthoringSession session = editableSession();
        activateEdit(session);

        assertThatCode(session::startCollecting).doesNotThrowAnyException();

        assertThat(session.getInteractionStatus()).isEqualTo(AuthoringInteractionStatus.COLLECTING);
        assertThat(session.getLastErrorCode()).isNull();
    }

    @Test
    void 승격이_끝난_Session은_회수가_상태를_되돌리지_않는다() {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(1L);
        session.initializeCurrentVersion(10L);
        session.attachPendingVersion(11L);
        session.promotePendingVersion(11L, AuthoringVerificationStatus.PASSED);

        session.abortActiveExecution("MODIFICATION_FAILED");

        assertThat(session.getOperationStatus()).isEqualTo(AuthoringOperationStatus.IDLE);
        assertThat(session.getLastErrorCode()).isNull();
    }

    @Test
    void 확인_대기_중_요구를_바꾸면_마지막_명령만_남는다() {
        ProblemAuthoringSession session = editableSession();
        session.startCollecting();
        session.awaitConfirmation("{\"first\":true}", 1);

        assertThatCode(() -> session.awaitConfirmation("{\"second\":true}", 1))
                .doesNotThrowAnyException();

        assertThat(session.getPendingInstructions()).isEqualTo("{\"second\":true}");
        assertThat(session.getInteractionStatus())
                .isEqualTo(AuthoringInteractionStatus.AWAITING_CONFIRMATION);
    }

    private ProblemAuthoringSession editableSession() {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(1L);
        session.initializeCurrentVersion(10L);
        return session;
    }

    private void activateEdit(ProblemAuthoringSession session) {
        session.startCollecting();
        session.awaitConfirmation("{}", 1);
        session.activateEdit(UUID.randomUUID(), 10L, "{}", 1);
    }
}
