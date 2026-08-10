package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;
import com.cenedu.backend.global.common.enums.UserRole;

/** 생성된 학생 계정과 프로필 정보. */
public record StudentCreateResponse(
        Long id,
        UserRole role,
        String loginId,
        String name,
        short registrationYear,
        short grade,
        Long ownerTeacherId
) {

    /** 학생 계정과 프로필을 생성 결과 응답으로 변환한다. */
    public static StudentCreateResponse from(MemberAccount student, MemberStudentProfile profile) {
        return new StudentCreateResponse(
                student.getId(),
                student.getRole(),
                student.getLoginId(),
                student.getName(),
                profile.getRegistrationYear(),
                profile.getGrade(),
                profile.getOwnerTeacher().getId()
        );
    }
}
