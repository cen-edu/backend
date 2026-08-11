package com.cenedu.backend.domain.worksheet.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 배포 행위. 반 단위(일반·종합)와 학생 단위(맞춤) 중 하나만 값을 가진다. */
@Entity
@Getter
@Table(name = "worksheet_assignment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_worksheet_assignment_worksheet_class",
                        columnNames = {"worksheet_id", "class_id"}),
                @UniqueConstraint(name = "uk_worksheet_assignment_worksheet_student",
                        columnNames = {"worksheet_id", "student_id"})},
        indexes = {
                @Index(name = "idx_worksheet_assignment_class", columnList = "class_id"),
                @Index(name = "idx_worksheet_assignment_student", columnList = "student_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worksheet_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_worksheet_assignment_worksheet"))
    private Worksheet worksheet;

    /** member 도메인의 반 ID. 학생 단위 배포면 null 이다. */
    @Column(name = "class_id")
    private Long classId;

    /** member 도메인의 학생 계정 ID. 반 단위 배포면 null 이다. */
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    private WorksheetAssignment(Worksheet worksheet, Long classId, Long studentId,
                                OffsetDateTime assignedAt, OffsetDateTime dueAt) {
        this.worksheet = worksheet;
        this.classId = classId;
        this.studentId = studentId;
        this.assignedAt = assignedAt;
        this.dueAt = dueAt;
    }

    /** 학습지 배포를 생성한다. 반/학생 택일은 서비스가 검증한다. */
    public static WorksheetAssignment create(Worksheet worksheet, Long classId, Long studentId,
                                             OffsetDateTime assignedAt, OffsetDateTime dueAt) {
        return new WorksheetAssignment(worksheet, classId, studentId, assignedAt, dueAt);
    }
}
