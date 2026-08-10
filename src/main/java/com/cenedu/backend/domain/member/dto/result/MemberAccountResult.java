package com.cenedu.backend.domain.member.dto.result;

import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.global.common.enums.UserRole;

/** 영속성 엔티티를 노출하지 않고 다른 도메인에 제공하는 회원 계정 기본 정보. */
public record MemberAccountResult(
        Long id,
        UserRole role,
        String loginId,
        String name
) {

    /** 회원 엔티티를 비밀번호가 제외된 외부 공개 결과로 변환한다. */
    public static MemberAccountResult from(MemberAccount account) {
        return new MemberAccountResult(
                account.getId(),
                account.getRole(),
                account.getLoginId(),
                account.getName()
        );
    }
}
