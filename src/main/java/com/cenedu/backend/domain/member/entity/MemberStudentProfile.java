package com.cenedu.backend.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학생 계정과 일대일로 대응하는 학생 정보. */
@Entity
@Getter
@Table(name = "member_student_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberStudentProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_student_profile_user"))
    private MemberAccount user;

    @Column(name = "registration_year", nullable = false)
    private short registrationYear;

    @Column(name = "grade", nullable = false)
    private short grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_student_profile_owner_teacher"))
    private MemberAccount ownerTeacher;

    private MemberStudentProfile(MemberAccount user, short registrationYear, short grade,
                                 MemberAccount ownerTeacher) {
        this.user = user;
        this.registrationYear = registrationYear;
        this.grade = grade;
        this.ownerTeacher = ownerTeacher;
    }

    /** 학생 계정의 등록 정보와 소유 교사를 지정해 프로필을 생성한다. */
    public static MemberStudentProfile create(MemberAccount student, short registrationYear,
                                              short grade, MemberAccount ownerTeacher) {
        return new MemberStudentProfile(student, registrationYear, grade, ownerTeacher);
    }

    public void changeGrade(short grade) {
        this.grade = grade;
    }
}
