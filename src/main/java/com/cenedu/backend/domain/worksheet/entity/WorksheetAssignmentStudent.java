package com.cenedu.backend.domain.worksheet.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.global.common.enums.AssignmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학생 한 명의 배정. 제출·채점·공개 상태의 정본이다. */
@Entity
@Getter
@Table(name = "worksheet_assignment_student",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_worksheet_assignment_student",
                columnNames = {"assignment_id", "student_id"}),
        indexes = @Index(name = "idx_worksheet_assignment_student_student",
                columnList = "student_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetAssignmentStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_worksheet_assignment_student_assignment"))
    private WorksheetAssignment assignment;

    /** member 도메인의 학생 프로필 ID(=계정 ID). */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private AssignmentStatus status;

    @Column(name = "progress_count", nullable = false)
    private short progressCount;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;

    /** 교사가 반 단위 확정을 누른 시각. null 이면 학생은 점수·정답·해설을 볼 수 없다. */
    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    /** 종합평가 전용 비정규화 값. */
    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    private WorksheetAssignmentStudent(WorksheetAssignment assignment, Long studentId) {
        this.assignment = assignment;
        this.studentId = studentId;
        this.status = AssignmentStatus.NOT_STARTED;
        this.progressCount = 0;
    }

    /** 배포와 동시에 학생별 배정을 생성한다. 시작 전 상태로 만들어진다. */
    public static WorksheetAssignmentStudent create(WorksheetAssignment assignment,
                                                    Long studentId) {
        return new WorksheetAssignmentStudent(assignment, studentId);
    }

    /**
     * 답안 저장 후 진행률을 재계산한 값으로 갈아 끼운다. {@code status}는 건드리지 않는다 —
     * {@code IN_PROGRESS}는 DB에 없는 값이라(CHECK 제약 4값 고정) 조회 시점에 파생한다.
     */
    public void updateProgressCount(short progressCount) {
        this.progressCount = progressCount;
    }

    /** 제출을 확정한다. 채점은 여기서 하지 않는다 — 교사가 트리거한다. */
    public void submit(OffsetDateTime submittedAt) {
        this.status = AssignmentStatus.SUBMITTED;
        this.submittedAt = submittedAt;
    }

    /**
     * 전 칸 채점이 끝났음을 기록한다. 종합평가가 아니면 {@code totalScore}가 {@code null}이다.
     */
    public void markGraded(OffsetDateTime gradedAt, BigDecimal totalScore) {
        this.status = AssignmentStatus.GRADED;
        this.gradedAt = gradedAt;
        this.totalScore = totalScore;
    }

    /** 교사가 점수를 고친 뒤 총점만 다시 계산한다. 채점 상태와 시각은 그대로 둔다. */
    public void updateTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    /** 교사가 반 단위로 확정한다. 이때부터 학생이 점수·정답·해설을 본다. */
    public void release(OffsetDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    /**
     * 확정 시점까지 내지 않은 학생을 미제출로 기록한다. {@code released_at}은 채우지 않는다 —
     * 공개할 결과가 없다.
     */
    public void markNotSubmitted() {
        this.status = AssignmentStatus.NOT_SUBMITTED;
    }
}
