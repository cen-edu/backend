package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.global.common.enums.UserRole;

/** 비밀번호를 제외한 회원 계정의 일반 반환 정보. */
public record MemberAccountResponse(
        Long id,
        UserRole role,
        String loginId,
        String name
) {

    /** 회원 엔티티를 비밀번호가 제외된 계정 응답으로 변환한다. */
    public static MemberAccountResponse from(MemberAccount account) {
        return new MemberAccountResponse(
                account.getId(),
                account.getRole(),
                account.getLoginId(),
                account.getName()
        );
    }
}
