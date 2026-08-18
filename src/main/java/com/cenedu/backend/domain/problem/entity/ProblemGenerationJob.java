package com.cenedu.backend.domain.problem.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
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

/** 한 번의 시스템 문제 생성 요청과 그 요청 아래 여러 Item의 집계 상태를 관리한다. */
@Entity
@Getter
@Table(name = "problem_generation_job",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_generation_job_owner_request",
                columnNames = {"owner_teacher_id", "client_request_id"}),
        indexes = @Index(name = "idx_problem_generation_job_owner_status",
                columnList = "owner_teacher_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemGenerationJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_teacher_id", nullable = false)
    private Long ownerTeacherId;

    @Column(name = "client_request_id", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private GenerationJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GenerationJobStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private ProblemGenerationJob(Long ownerTeacherId, UUID clientRequestId,
                                 GenerationJobType jobType) {
        this.ownerTeacherId = ownerTeacherId;
        this.clientRequestId = clientRequestId;
        this.jobType = jobType;
        this.status = GenerationJobStatus.QUEUED;
    }

    /** 중복 키와 사업 유형을 가진 대기 상태 Job을 생성한다. */
    public static ProblemGenerationJob create(Long ownerTeacherId, UUID clientRequestId,
                                              GenerationJobType jobType) {
        return new ProblemGenerationJob(ownerTeacherId, clientRequestId, jobType);
    }

    /** 첫 Item 실행 직전에 Job을 실행 중으로 바꾼다. */
    public void start() {
        requireStatus(GenerationJobStatus.QUEUED);
        status = GenerationJobStatus.RUNNING;
        startedAt = LocalDateTime.now();
    }

    /** 모든 Item 결과 집계 후 Job을 성공·부분 실패·전체 실패 중 하나로 종료한다. */
    public void complete(GenerationJobStatus terminalStatus) {
        if (terminalStatus != GenerationJobStatus.COMPLETED
                && terminalStatus != GenerationJobStatus.PARTIALLY_FAILED
                && terminalStatus != GenerationJobStatus.FAILED) {
            throw new IllegalArgumentException("종료 상태가 아닙니다: " + terminalStatus);
        }
        requireStatus(GenerationJobStatus.RUNNING);
        status = terminalStatus;
        completedAt = LocalDateTime.now();
    }

    private void requireStatus(GenerationJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("생성 Job 상태가 " + expected + "이(가) 아닙니다.");
        }
    }
}
