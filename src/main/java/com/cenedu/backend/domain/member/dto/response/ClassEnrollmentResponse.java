package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberClassEnrollment;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

/** 반에 배정된 학생의 계정과 학년 정보. */
public record ClassEnrollmentResponse(
        Long enrollmentId,
        Long classId,
        Long studentId,
        String studentName,
        short grade
) {

    /** 반 배정과 학생 프로필을 배정 응답으로 변환한다. */
    public static ClassEnrollmentResponse from(MemberClassEnrollment enrollment,
                                               MemberStudentProfile profile) {
        return new ClassEnrollmentResponse(
                enrollment.getId(),
                enrollment.getSchoolClass().getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getName(),
                profile.getGrade()
        );
    }
}
