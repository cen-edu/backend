package com.cenedu.backend.domain.problem.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
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

/** Generation Job 안에서 독립적으로 병렬 실행되는 문항 하나의 상태와 재시도를 관리한다. */
@Entity
@Getter
@Table(name = "problem_generation_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_problem_generation_item_job_order",
                        columnNames = {"job_id", "item_order"}),
                @UniqueConstraint(name = "uk_problem_generation_item_request",
                        columnNames = "request_id"),
                @UniqueConstraint(name = "uk_problem_generation_item_session",
                        columnNames = "session_id")
        },
        indexes = @Index(name = "idx_problem_generation_item_job_status",
                columnList = "job_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemGenerationItem extends BaseTimeEntity {

    public static final short MAX_SEMANTIC_RETRY_COUNT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Column(name = "item_order", nullable = false, updatable = false)
    private int itemOrder;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_purpose", nullable = false, length = 60, updatable = false)
    private GenerationPurpose generationPurpose;

    @Column(name = "command_schema_version", nullable = false, updatable = false)
    private int commandSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generation_command", nullable = false, columnDefinition = "jsonb",
            updatable = false)
    private String generationCommand;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GenerationItemStatus status;

    @Column(name = "retry_count", nullable = false)
    private short retryCount;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private ProblemGenerationItem(Long jobId, int itemOrder, UUID requestId, Long sessionId,
                                  GenerationPurpose generationPurpose, int commandSchemaVersion,
                                  String generationCommand) {
        this.jobId = jobId;
        this.itemOrder = itemOrder;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.generationPurpose = generationPurpose;
        this.commandSchemaVersion = commandSchemaVersion;
        this.generationCommand = generationCommand;
        this.status = GenerationItemStatus.QUEUED;
        this.retryCount = 0;
    }

    /** 재시작 후에도 복구할 전체 생성 명령을 가진 대기 Item을 생성한다. */
    public static ProblemGenerationItem create(Long jobId, int itemOrder, UUID requestId,
                                               Long sessionId,
                                               GenerationPurpose generationPurpose,
                                               int commandSchemaVersion,
                                               String generationCommand) {
        return new ProblemGenerationItem(jobId, itemOrder, requestId, sessionId,
                generationPurpose, commandSchemaVersion, generationCommand);
    }

    /** Worker가 실행권을 얻은 Item을 생성 중으로 바꾼다. */
    public void startGeneration() {
        if (status != GenerationItemStatus.QUEUED) {
            throw new IllegalStateException("생성을 시작할 수 없는 Item 상태입니다: " + status);
        }
        status = GenerationItemStatus.GENERATING;
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    /** 구조가 유효한 후보가 생성되면 Item을 검증 중으로 바꾼다. */
    public void startVerification() {
        requireStatus(GenerationItemStatus.GENERATING);
        status = GenerationItemStatus.VERIFYING;
    }

    /** 후보가 검증을 통과해 최종화 가능한 Item으로 종료한다. */
    public void succeed() {
        requireStatus(GenerationItemStatus.VERIFYING);
        status = GenerationItemStatus.SUCCEEDED;
        lastErrorCode = null;
        completedAt = LocalDateTime.now();
    }

    /** 의미 검증 실패 후 새 후보 생성 횟수를 늘리고 재생성 상태로 바꾼다. */
    public void retryGeneration(String errorCode) {
        if (status != GenerationItemStatus.GENERATING
                && status != GenerationItemStatus.VERIFYING) {
            throw new IllegalStateException("재시도할 수 없는 Item 상태입니다: " + status);
        }
        if (retryCount >= MAX_SEMANTIC_RETRY_COUNT) {
            throw new IllegalStateException("문제 후보 재생성 상한을 소진했습니다.");
        }
        retryCount++;
        lastErrorCode = errorCode;
        status = GenerationItemStatus.GENERATING;
    }

    /** 의미 검증 재생성 상한이 남았는지 반환한다. */
    public boolean canRetry() {
        return retryCount < MAX_SEMANTIC_RETRY_COUNT;
    }

    /** 더 이상 재시도하지 않는 Item을 실패로 종료한다. */
    public void fail(String errorCode) {
        if (status == GenerationItemStatus.SUCCEEDED
                || status == GenerationItemStatus.FAILED) {
            throw new IllegalStateException("이미 종료된 Item입니다: " + status);
        }
        status = GenerationItemStatus.FAILED;
        lastErrorCode = errorCode;
        completedAt = LocalDateTime.now();
    }

    private void requireStatus(GenerationItemStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Item 상태가 " + expected + "이(가) 아닙니다.");
        }
    }
}
