package com.cenedu.backend.domain.member.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 교사가 관리하는 학년도 단위의 반. */
@Entity
@Getter
@Table(name = "member_school_class")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private short academicYear;

    @Column(name = "grade", nullable = false)
    private short grade;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "homeroom_teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_school_class_homeroom_teacher"))
    private MemberAccount homeroomTeacher;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private MemberSchoolClass(short academicYear, short grade, String name,
                              MemberAccount homeroomTeacher, int displayOrder) {
        this.academicYear = academicYear;
        this.grade = grade;
        this.name = name;
        this.homeroomTeacher = homeroomTeacher;
        this.displayOrder = displayOrder;
    }

    /** 학년도와 학년, 담임 교사, 표시 순서를 지정해 반을 생성한다. */
    public static MemberSchoolClass create(short academicYear, short grade, String name,
                                           MemberAccount homeroomTeacher, int displayOrder) {
        return new MemberSchoolClass(academicYear, grade, name, homeroomTeacher, displayOrder);
    }

    public void updateDetails(short academicYear, short grade, String name) {
        this.academicYear = academicYear;
        this.grade = grade;
        this.name = name;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    public boolean deleted() {
        return deletedAt != null;
    }
}
