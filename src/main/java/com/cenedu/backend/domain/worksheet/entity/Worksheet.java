package com.cenedu.backend.domain.worksheet.entity;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 문항 묶음. 문제 보관함에 저장하는 시점에 확정되며 이후 불변이다. */
@Entity
@Getter
@Table(name = "worksheet",
        indexes = {
                @Index(name = "idx_worksheet_owner_teacher", columnList = "owner_teacher_id"),
                @Index(name = "idx_worksheet_source_assignment",
                        columnList = "source_assignment_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worksheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private WorksheetType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 10)
    private WorksheetOrigin origin;

    /** 소유 교사의 member 도메인 계정 ID. */
    @Column(name = "owner_teacher_id", nullable = false)
    private Long ownerTeacherId;

    @Column(name = "grade", nullable = false)
    private short grade;

    /** '1' / '2' / 'COMMON'. 숫자 값이 섞여 있어 enum 으로 두지 않았다. */
    @Column(name = "semester", nullable = false, length = 10)
    private String semester;

    /** 종합평가만 값이 있다. */
    @Column(name = "total_score")
    private Short totalScore;

    /** 맞춤 학습지가 어느 배정 결과를 보고 만들어졌는지. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_assignment_id",
            foreignKey = @ForeignKey(name = "fk_worksheet_source_assignment"))
    private WorksheetAssignment sourceAssignment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private Worksheet(String title, WorksheetType type, WorksheetOrigin origin, Long ownerTeacherId,
                      short grade, String semester, Short totalScore,
                      WorksheetAssignment sourceAssignment) {
        this.title = title;
        this.type = type;
        this.origin = origin;
        this.ownerTeacherId = ownerTeacherId;
        this.grade = grade;
        this.semester = semester;
        this.totalScore = totalScore;
        this.sourceAssignment = sourceAssignment;
        this.createdAt = OffsetDateTime.now();
    }

    /** 학습지를 생성한다. 맞춤 학습지가 아니면 sourceAssignment 가 null 이다. */
    public static Worksheet create(String title, WorksheetType type, WorksheetOrigin origin,
                                   Long ownerTeacherId, short grade, String semester,
                                   Short totalScore, WorksheetAssignment sourceAssignment) {
        return new Worksheet(title, type, origin, ownerTeacherId, grade, semester, totalScore,
                sourceAssignment);
    }
}
