package com.cenedu.backend.domain.auth.dto.response;

import com.cenedu.backend.domain.member.dto.response.MemberAccountResponse;
import com.cenedu.backend.global.common.enums.UserRole;

/** 가입이 완료된 교사 계정 정보. */
public record SignupResponse(
        Long userId,
        String email,
        String name,
        UserRole role
) {

    /** 회원 도메인의 공개 결과를 가입 응답으로 변환한다. */
    public static SignupResponse from(MemberAccountResponse account) {
        return new SignupResponse(account.id(), account.loginId(), account.name(), account.role());
    }
}
