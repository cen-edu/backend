package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

/** 마이페이지에 표시할 학생 계정 정보. */
public record StudentAccountResponse(
        Long id,
        String name,
        short grade,
        String loginId
) {

    /** 학생 프로필과 계정을 마이페이지 응답으로 변환한다. */
    public static StudentAccountResponse from(
            MemberAccount account,
            MemberStudentProfile profile
    ) {
        return new StudentAccountResponse(
                account.getId(),
                account.getName(),
                profile.getGrade(),
                account.getLoginId()
        );
    }
}
