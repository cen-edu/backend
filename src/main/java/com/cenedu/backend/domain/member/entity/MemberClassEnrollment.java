package com.cenedu.backend.domain.member.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 반과 학생 계정의 다대다 소속 관계. */
@Entity
@Getter
@Table(name = "member_class_enrollment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_class_enrollment_class_student",
                columnNames = {"class_id", "student_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberClassEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_class_enrollment_class"))
    private MemberSchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_class_enrollment_student"))
    private MemberAccount student;

    private MemberClassEnrollment(MemberSchoolClass schoolClass, MemberAccount student) {
        this.schoolClass = schoolClass;
        this.student = student;
    }

    /** 검증이 완료된 반과 학생을 연결하는 배정을 생성한다. */
    public static MemberClassEnrollment create(MemberSchoolClass schoolClass,
                                               MemberAccount student) {
        return new MemberClassEnrollment(schoolClass, student);
    }
}
