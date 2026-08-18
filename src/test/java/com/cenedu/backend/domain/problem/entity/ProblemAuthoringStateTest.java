package com.cenedu.backend.domain.problem.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemAuthoringStateTest {

    @Test
    @DisplayName("검증 실패 후보는 기존 current Version을 덮어쓰지 않는다")
    void failedCandidateDoesNotReplaceCurrentVersion() {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(7L);
        session.attachPendingVersion(1L);
        session.promotePendingVersion(1L, AuthoringVerificationStatus.PASSED);
        session.attachPendingVersion(2L);

        session.failPendingVersion(2L, "ANSWER_MISMATCH");

        assertThat(session.getCurrentVersionId()).isEqualTo(1L);
        assertThat(session.getPendingVersionId()).isNull();
    }

    @Test
    @DisplayName("수정 요청을 수집 중이면 복원과 최종화를 막는다")
    void blocksRestoreAndFinalizationDuringHitlCollection() {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(7L);
        session.attachPendingVersion(1L);
        session.promotePendingVersion(1L, AuthoringVerificationStatus.PASSED);
        session.startCollecting();

        assertThat(session.getInteractionStatus())
                .isEqualTo(AuthoringInteractionStatus.COLLECTING);
        assertThatThrownBy(() -> session.restorePassedVersion(
                1L, AuthoringVerificationStatus.PASSED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> session.finalizeAs(
                10L, AuthoringVerificationStatus.PASSED))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getLifecycleStatus()).isEqualTo(AuthoringLifecycleStatus.DRAFT);
    }

    @Test
    @DisplayName("생성 Item은 의미 검증 실패를 최대 2회까지만 재생성한다")
    void limitsSemanticGenerationRetry() {
        ProblemGenerationItem item = ProblemGenerationItem.create(
                1L, 1, UUID.randomUUID(), 2L,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, 1, "{}");

        item.startGeneration();
        item.startVerification();
        item.retryGeneration("FIRST_FAILURE");
        item.startVerification();
        item.retryGeneration("SECOND_FAILURE");
        item.startVerification();

        assertThat(item.getStatus()).isEqualTo(GenerationItemStatus.VERIFYING);
        assertThat(item.getRetryCount()).isEqualTo((short) 2);
        assertThatThrownBy(() -> item.retryGeneration("THIRD_FAILURE"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("BANK_REUSE Version은 원본 questionId 없이 만들 수 없다")
    void bankReuseVersionRequiresSourceQuestion() {
        assertThatThrownBy(() -> ProblemAuthoringVersion.create(
                1L, 1, null, UUID.randomUUID(),
                AuthoringOperationType.BANK_REUSE, null,
                1, "{}", "{}", "원본 문항"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
