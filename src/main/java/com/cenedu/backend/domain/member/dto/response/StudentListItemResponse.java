package com.cenedu.backend.domain.member.dto.response;

import java.util.List;

import com.cenedu.backend.domain.member.entity.MemberClassEnrollment;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

/** 학생 목록 한 행에 필요한 계정, 등록, 반 정보. */
public record StudentListItemResponse(
        Long id,
        short registrationYear,
        short grade,
        String name,
        String loginId,
        List<StudentClassResponse> classes
) {

    /** 학생 프로필과 활성 반 배정 목록을 학생 목록 응답으로 변환한다. */
    public static StudentListItemResponse from(
            MemberStudentProfile profile,
            List<MemberClassEnrollment> enrollments
    ) {
        return new StudentListItemResponse(
                profile.getUserId(),
                profile.getRegistrationYear(),
                profile.getGrade(),
                profile.getUser().getName(),
                profile.getUser().getLoginId(),
                enrollments.stream()
                        .map(enrollment -> StudentClassResponse.from(enrollment.getSchoolClass()))
                        .toList()
        );
    }
}
