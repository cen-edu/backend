package com.cenedu.backend.domain.analysis.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.cenedu.backend.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생 한 명의 문제지 한 회분.
 *
 * <p>업무 키는 {@code (assessmentId, studentId)} 다. id 는 JPA 용 대리 키일 뿐이라 외부에 노출하지
 * 않는다. member 도메인이 생기면 studentId 를 Long FK 로 바꾼다.
 */
@Entity
@Getter
@Table(name = "analysis_assessment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_assessment_business_key",
                columnNames = {"assessment_id", "student_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisAssessment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false, length = 64)
    private String assessmentId;

    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;

    @Column(name = "assessment_title", nullable = false)
    private String assessmentTitle;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "assessment_type", nullable = false, length = 40)
    private String assessmentType;

    /** 모의 데이터 여부. 집계에서 실제 데이터와 섞지 않는다. */
    @Column(name = "is_simulation", nullable = false)
    private boolean simulation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssessmentStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    private AnalysisAssessment(String assessmentId, String studentId, String assessmentTitle,
                               LocalDate assessmentDate, String studentName,
                               String assessmentType, boolean simulation) {
        this.assessmentId = assessmentId;
        this.studentId = studentId;
        this.assessmentTitle = assessmentTitle;
        this.assessmentDate = assessmentDate;
        this.studentName = studentName;
        this.assessmentType = assessmentType;
        this.simulation = simulation;
        this.status = AssessmentStatus.IN_PROGRESS;
    }

    public boolean completed() {
        return status.completed();
    }

    /**
     * 회차를 완료 처리한다.
     *
     * <p>완료 시각은 처음 완료된 때를 유지한다. 같은 회차를 두 번 완료해도 시각이 밀리지 않아야
     * 보고서와 대시보드가 같은 기준 시각을 본다.
     */
    public void complete() {
        this.status = AssessmentStatus.COMPLETED;
        if (this.completedAt == null) {
            this.completedAt = Instant.now();
        }
    }

    /**
     * 같은 회차로 들어온 머리말이 먼저 저장된 것과 일치하는지 본다.
     *
     * <p>어긋나면 서로 다른 회차가 같은 키를 쓰고 있다는 뜻이라 저장을 막아야 한다.
     */
    public boolean matchesHeader(String title, LocalDate date, String studentName,
                                 String assessmentType, boolean simulation) {
        return this.assessmentTitle.equals(title)
                && this.assessmentDate.equals(date)
                && this.studentName.equals(studentName)
                && this.assessmentType.equals(assessmentType)
                && this.simulation == simulation;
    }
}
