package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.global.common.enums.UserRole;

/** 로그인 검증에만 사용하며 API 응답으로 반환하지 않는 계정 자격 증명. */
public record MemberAccountCredentials(
        Long id,
        UserRole role,
        String loginId,
        String passwordHash,
        String name
) {

    /** 회원 엔티티를 로그인 검증용 자격 증명으로 변환한다. */
    public static MemberAccountCredentials from(MemberAccount account) {
        return new MemberAccountCredentials(
                account.getId(),
                account.getRole(),
                account.getLoginId(),
                account.getPasswordHash(),
                account.getName()
        );
    }
}
