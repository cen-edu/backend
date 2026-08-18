package com.cenedu.backend.domain.problem.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 문제 하나의 현재·후보 Version 포인터와 수정 HITL·실행 상태를 관리한다. */
@Entity
@Getter
@Table(name = "problem_authoring_session",
        indexes = {
                @Index(name = "idx_problem_authoring_session_owner_lifecycle",
                        columnList = "owner_teacher_id, lifecycle_status"),
                @Index(name = "idx_problem_authoring_session_finalized_question",
                        columnList = "finalized_question_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAuthoringSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_teacher_id", nullable = false, updatable = false)
    private Long ownerTeacherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private AuthoringLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", nullable = false, length = 20)
    private AuthoringOperationStatus operationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_status", nullable = false, length = 30)
    private AuthoringInteractionStatus interactionStatus;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "pending_version_id")
    private Long pendingVersionId;

    @Column(name = "active_request_id")
    private UUID activeRequestId;

    @Column(name = "active_base_version_id")
    private Long activeBaseVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_instructions", columnDefinition = "jsonb")
    private String pendingInstructions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_command", columnDefinition = "jsonb")
    private String activeCommand;

    @Column(name = "edit_schema_version")
    private Integer editSchemaVersion;

    @Column(name = "finalized_question_id")
    private Long finalizedQuestionId;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    private ProblemAuthoringSession(Long ownerTeacherId, AuthoringOperationStatus operationStatus) {
        this.ownerTeacherId = ownerTeacherId;
        this.lifecycleStatus = AuthoringLifecycleStatus.DRAFT;
        this.operationStatus = operationStatus;
        this.interactionStatus = AuthoringInteractionStatus.IDLE;
    }

    /** AI 최초 생성을 즉시 시작할 DRAFT Session을 만든다. */
    public static ProblemAuthoringSession createGenerating(Long ownerTeacherId) {
        return new ProblemAuthoringSession(ownerTeacherId, AuthoringOperationStatus.GENERATING);
    }

    /** 문제은행에서 만든 최초 Version을 검증 완료 상태의 현재 Version으로 연결한다. */
    public void initializeCurrentVersion(Long versionId) {
        if (lifecycleStatus != AuthoringLifecycleStatus.DRAFT || currentVersionId != null) {
            throw new IllegalStateException("최초 Version을 연결할 수 없는 Session입니다.");
        }
        currentVersionId = versionId;
        operationStatus = AuthoringOperationStatus.IDLE;
    }

    /** 기존 문제 수정 또는 복원 대화를 받을 수 있는 IDLE DRAFT Session을 만든다. */
    public static ProblemAuthoringSession createIdle(Long ownerTeacherId) {
        return new ProblemAuthoringSession(ownerTeacherId, AuthoringOperationStatus.IDLE);
    }

    /** 새 수정 사항을 받기 위해 HITL 수집 상태를 연다. */
    public void startCollecting() {
        requireDraftIdle();
        interactionStatus = AuthoringInteractionStatus.COLLECTING;
    }

    /** 누적한 구조화 수정 상태를 저장하고 교사 확인을 기다린다. */
    public void awaitConfirmation(String pendingInstructions, int editSchemaVersion) {
        if (interactionStatus != AuthoringInteractionStatus.COLLECTING) {
            throw new IllegalStateException("수정 사항 수집 중이 아닙니다.");
        }
        this.pendingInstructions = pendingInstructions;
        this.editSchemaVersion = editSchemaVersion;
        interactionStatus = AuthoringInteractionStatus.AWAITING_CONFIRMATION;
    }

    /** 교사가 확인한 명령을 고정하고 비동기 수정 실행 상태로 바꾼다. */
    public void activateEdit(UUID requestId, Long baseVersionId, String activeCommand,
                             int editSchemaVersion) {
        if (interactionStatus != AuthoringInteractionStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("최종 확인을 기다리는 수정 요청이 없습니다.");
        }
        this.activeRequestId = requestId;
        this.activeBaseVersionId = baseVersionId;
        this.activeCommand = activeCommand;
        this.editSchemaVersion = editSchemaVersion;
        this.pendingInstructions = null;
        this.interactionStatus = AuthoringInteractionStatus.IDLE;
        this.operationStatus = AuthoringOperationStatus.MODIFYING;
        this.lastErrorCode = null;
    }

    /** 교사가 확인한 이전 PASSED Version을 AI 호출 없이 바로 복원한다. */
    public void completeConfirmedRestore(Long versionId,
                                         AuthoringVerificationStatus verificationStatus) {
        if (interactionStatus != AuthoringInteractionStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("최종 확인을 기다리는 복원 요청이 없습니다.");
        }
        requirePassed(verificationStatus);
        currentVersionId = versionId;
        pendingInstructions = null;
        editSchemaVersion = null;
        interactionStatus = AuthoringInteractionStatus.IDLE;
        lastErrorCode = null;
    }

    /** 수집 중이거나 확인 대기 중인 HITL 요청을 취소한다. */
    public void cancelInteraction() {
        requireDraft();
        if (interactionStatus == AuthoringInteractionStatus.IDLE) {
            throw new IllegalStateException("취소할 수정 대화가 없습니다.");
        }
        interactionStatus = AuthoringInteractionStatus.IDLE;
        pendingInstructions = null;
        editSchemaVersion = null;
    }

    /** 구조 검증을 통과한 후보 Version을 pending으로 지정하고 검증 상태로 바꾼다. */
    public void attachPendingVersion(Long versionId) {
        requireDraft();
        if (pendingVersionId != null) {
            throw new IllegalStateException("이미 검증 중인 후보 Version이 있습니다.");
        }
        pendingVersionId = versionId;
        operationStatus = AuthoringOperationStatus.VERIFYING;
    }

    /** PASSED 후보만 현재 Version으로 승격시킨다. */
    public void promotePendingVersion(Long versionId,
                                      AuthoringVerificationStatus verificationStatus) {
        requirePendingVersion(versionId);
        requirePassed(verificationStatus);
        currentVersionId = versionId;
        pendingVersionId = null;
        operationStatus = AuthoringOperationStatus.IDLE;
        clearActiveExecution();
        lastErrorCode = null;
    }

    /** 실패한 후보를 current에 반영하지 않고 후보 포인터만 제거한다. */
    public void failPendingVersion(Long versionId, String errorCode) {
        requirePendingVersion(versionId);
        pendingVersionId = null;
        operationStatus = AuthoringOperationStatus.FAILED;
        // 수정 후보가 실패해도 재시작 후 재시도할 수 있게
        // activeRequest·baseVersion·command는 성공 승격 전까지 유지한다.
        lastErrorCode = errorCode;
    }

    /** 의미 검증 실패 후 기존 current를 유지한 채 새 생성 또는 수정 후보 준비로 돌아간다. */
    public void prepareRetry(boolean modification, String errorCode) {
        if (operationStatus != AuthoringOperationStatus.VERIFYING
                && operationStatus != AuthoringOperationStatus.FAILED
                && operationStatus != AuthoringOperationStatus.GENERATING
                && operationStatus != AuthoringOperationStatus.MODIFYING) {
            throw new IllegalStateException("실행 중이거나 실패한 Session만 재시도할 수 있습니다.");
        }
        pendingVersionId = null;
        operationStatus = modification
                ? AuthoringOperationStatus.MODIFYING
                : AuthoringOperationStatus.GENERATING;
        lastErrorCode = errorCode;
    }

    /** Version을 만들지 못한 생성·수정 오류를 Session 실패로 마감한다. */
    public void failOperation(String errorCode) {
        requireDraft();
        if (pendingVersionId != null) {
            throw new IllegalStateException("pending Version은 검증 결과로 실패 처리해야 합니다.");
        }
        operationStatus = AuthoringOperationStatus.FAILED;
        clearActiveExecution();
        lastErrorCode = errorCode;
    }

    /** 교사가 지칭한 PASSED Version을 현재 Version으로 변경한다. */
    public void restorePassedVersion(Long versionId,
                                     AuthoringVerificationStatus verificationStatus) {
        requireDraftIdle();
        requirePassed(verificationStatus);
        currentVersionId = versionId;
        lastErrorCode = null;
    }

    /** 최종 문제은행 저장 후 Session을 종료하고 결과 questionId를 고정한다. */
    public void finalizeAs(Long questionId,
                           AuthoringVerificationStatus currentVerificationStatus) {
        requireDraftIdle();
        if (currentVersionId == null || pendingVersionId != null) {
            throw new IllegalStateException("최종화할 PASSED 현재 Version이 준비되지 않았습니다.");
        }
        requirePassed(currentVerificationStatus);
        lifecycleStatus = AuthoringLifecycleStatus.FINALIZED;
        finalizedQuestionId = questionId;
        finalizedAt = LocalDateTime.now();
    }

    private void requirePendingVersion(Long versionId) {
        if (operationStatus != AuthoringOperationStatus.VERIFYING
                || pendingVersionId == null
                || !pendingVersionId.equals(versionId)) {
            throw new IllegalStateException("현재 Session의 pending Version이 아닙니다.");
        }
    }

    private void requireDraftIdle() {
        requireDraft();
        if (operationStatus != AuthoringOperationStatus.IDLE) {
            throw new IllegalStateException("실행 중인 Session은 처리할 수 없습니다.");
        }
        if (interactionStatus != AuthoringInteractionStatus.IDLE) {
            throw new IllegalStateException("수정 대화가 진행 중인 Session은 처리할 수 없습니다.");
        }
    }

    private void requireDraft() {
        if (lifecycleStatus != AuthoringLifecycleStatus.DRAFT) {
            throw new IllegalStateException("이미 최종화된 Session입니다.");
        }
    }

    private void requirePassed(AuthoringVerificationStatus verificationStatus) {
        if (verificationStatus != AuthoringVerificationStatus.PASSED) {
            throw new IllegalStateException("PASSED Version만 사용할 수 있습니다.");
        }
    }

    private void clearActiveExecution() {
        activeRequestId = null;
        activeBaseVersionId = null;
        activeCommand = null;
    }
}
