package com.cenedu.backend.domain.auth.dto.response;

import java.time.Instant;

import com.cenedu.backend.global.common.enums.UserRole;

/** 로그인 성공 후 발급된 액세스 토큰과 사용자 정보. */
public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        Long userId,
        String email,
        String name,
        UserRole role
) {

    /** Bearer 액세스 토큰 응답을 생성한다. */
    public static LoginResponse of(
            String accessToken,
            Instant expiresAt,
            Long userId,
            String email,
            String name,
            UserRole role
    ) {
        return new LoginResponse(accessToken, "Bearer", expiresAt, userId, email, name, role);
    }
}
