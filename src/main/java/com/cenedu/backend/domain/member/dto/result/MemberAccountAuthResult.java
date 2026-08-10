package com.cenedu.backend.domain.member.dto.result;

import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.global.common.enums.UserRole;

/** auth 도메인이 자격 증명을 검증할 때 사용하는 활성 계정 정보. */
public record MemberAccountAuthResult(
        Long id,
        UserRole role,
        String loginId,
        String passwordHash,
        String name
) {

    /** 회원 엔티티를 로그인 자격 증명 검증용 결과로 변환한다. */
    public static MemberAccountAuthResult from(MemberAccount account) {
        return new MemberAccountAuthResult(
                account.getId(),
                account.getRole(),
                account.getLoginId(),
                account.getPasswordHash(),
                account.getName()
        );
    }
}
