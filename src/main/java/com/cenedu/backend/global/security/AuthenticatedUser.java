package com.cenedu.backend.global.security;

import java.security.Principal;
import java.util.List;

import com.cenedu.backend.global.common.enums.UserRole;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** JWT 인증을 마친 사용자의 변경되지 않는 식별 정보. */
public record AuthenticatedUser(long memberId, UserRole role) implements Principal {

    /** Spring Security가 URL 역할을 검사할 때 사용할 권한을 반환한다. */
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getName() {
        return Long.toString(memberId);
    }
}
