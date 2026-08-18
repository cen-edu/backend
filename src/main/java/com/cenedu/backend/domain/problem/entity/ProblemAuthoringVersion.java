package com.cenedu.backend.domain.problem.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** S1 구조 검증을 통과한 문제 후보의 불변 스냅샷과 후속 자산·검증 결과를 보관한다. */
@Entity
@Getter
@Table(name = "problem_authoring_version",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_problem_authoring_version_session_no",
                        columnNames = {"session_id", "version_no"}),
                @UniqueConstraint(name = "uk_problem_authoring_version_session_request",
                        columnNames = {"session_id", "source_request_id"}),
                @UniqueConstraint(name = "uk_problem_authoring_version_verification_request",
                        columnNames = "verification_request_id"),
                @UniqueConstraint(name = "uk_problem_authoring_version_id_session",
                        columnNames = {"id", "session_id"})
        },
        indexes = @Index(name = "idx_problem_authoring_version_session_created",
                columnList = "session_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAuthoringVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "parent_version_id", updatable = false)
    private Long parentVersionId;

    @Column(name = "source_request_id", nullable = false, updatable = false)
    private UUID sourceRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30, updatable = false)
    private AuthoringOperationType operationType;

    @Column(name = "source_question_id", updatable = false)
    private Long sourceQuestionId;

    @Column(name = "snapshot_schema_version", nullable = false, updatable = false)
    private int snapshotSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String snapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asset_manifest", nullable = false, columnDefinition = "jsonb")
    private String assetManifest;

    @Column(name = "change_summary", columnDefinition = "TEXT", updatable = false)
    private String changeSummary;

    @Column(name = "verification_request_id")
    private UUID verificationRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private AuthoringVerificationStatus verificationStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_report", columnDefinition = "jsonb")
    private String verificationReport;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    private ProblemAuthoringVersion(Long sessionId, int versionNo, Long parentVersionId,
                                    UUID sourceRequestId, AuthoringOperationType operationType,
                                    Long sourceQuestionId, int snapshotSchemaVersion,
                                    String snapshot, String assetManifest, String changeSummary) {
        if (operationType == AuthoringOperationType.BANK_REUSE && sourceQuestionId == null) {
            throw new IllegalArgumentException("BANK_REUSE Version은 원본 questionId가 필요합니다.");
        }
        this.sessionId = sessionId;
        this.versionNo = versionNo;
        this.parentVersionId = parentVersionId;
        this.sourceRequestId = sourceRequestId;
        this.operationType = operationType;
        this.sourceQuestionId = sourceQuestionId;
        this.snapshotSchemaVersion = snapshotSchemaVersion;
        this.snapshot = snapshot;
        this.assetManifest = assetManifest;
        this.changeSummary = changeSummary;
        this.verificationStatus = AuthoringVerificationStatus.NOT_STARTED;
    }

    /** 구조 검증을 통과한 스냅샷을 Session의 다음 불변 후보 Version으로 생성한다. */
    public static ProblemAuthoringVersion create(Long sessionId, int versionNo,
                                                 Long parentVersionId, UUID sourceRequestId,
                                                 AuthoringOperationType operationType,
                                                 Long sourceQuestionId,
                                                 int snapshotSchemaVersion, String snapshot,
                                                 String assetManifest, String changeSummary) {
        return new ProblemAuthoringVersion(sessionId, versionNo, parentVersionId,
                sourceRequestId, operationType, sourceQuestionId, snapshotSchemaVersion,
                snapshot, assetManifest, changeSummary);
    }

    /** 임시 자산 생성 상태와 결과만 manifest에 반영하고 S1 스냅샷은 바꾸지 않는다. */
    public void updateAssetManifest(String assetManifest) {
        requireNotTerminal();
        this.assetManifest = assetManifest;
    }

    /** 멱등성 ID를 고정하고 Version 검증을 시작한다. */
    public void startVerification(UUID verificationRequestId) {
        if (verificationStatus != AuthoringVerificationStatus.NOT_STARTED
                && verificationStatus != AuthoringVerificationStatus.ERROR) {
            throw new IllegalStateException("검증을 시작할 수 없는 Version 상태입니다: "
                    + verificationStatus);
        }
        if (this.verificationRequestId != null
                && !this.verificationRequestId.equals(verificationRequestId)) {
            throw new IllegalStateException("같은 Version의 검증 요청 ID를 변경할 수 없습니다.");
        }
        this.verificationRequestId = verificationRequestId;
        this.verificationStatus = AuthoringVerificationStatus.VERIFYING;
        this.verificationReport = null;
        this.verifiedAt = null;
    }

    /** 내용·자산 검증이 모두 통과한 Version을 현재 버전 승격 가능하게 한다. */
    public void passVerification(String verificationReport) {
        finishVerification(AuthoringVerificationStatus.PASSED, verificationReport);
    }

    /** 문제 품질·정합성에 실패한 Version을 이력으로 보존한다. */
    public void failVerification(String verificationReport) {
        finishVerification(AuthoringVerificationStatus.FAILED, verificationReport);
    }

    /** Provider·파싱 등 기술 오류로 판정하지 못한 Version을 재검증 가능한 상태로 남긴다. */
    public void errorVerification(String verificationReport) {
        finishVerification(AuthoringVerificationStatus.ERROR, verificationReport);
    }

    private void finishVerification(AuthoringVerificationStatus terminalStatus,
                                    String verificationReport) {
        if (verificationStatus != AuthoringVerificationStatus.VERIFYING) {
            throw new IllegalStateException("검증 중인 Version만 검증을 완료할 수 있습니다.");
        }
        verificationStatus = terminalStatus;
        this.verificationReport = verificationReport;
        verifiedAt = LocalDateTime.now();
    }

    private void requireNotTerminal() {
        if (verificationStatus == AuthoringVerificationStatus.PASSED
                || verificationStatus == AuthoringVerificationStatus.FAILED) {
            throw new IllegalStateException("검증이 종료된 Version의 자산 manifest를 바꿀 수 없습니다.");
        }
    }
}
