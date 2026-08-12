package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberAccount;

/** 마이페이지에 표시할 교사 계정 정보. */
public record TeacherAccountResponse(
        Long id,
        String name,
        String email
) {

    /** 교사 계정을 마이페이지 응답으로 변환한다. */
    public static TeacherAccountResponse from(MemberAccount account) {
        return new TeacherAccountResponse(
                account.getId(),
                account.getName(),
                account.getLoginId()
        );
    }
}
