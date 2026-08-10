package com.cenedu.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.cenedu.backend.global.common.enums.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 액세스 토큰을 발급하고 서명과 클레임을 검증한다. */
@Component
public class JwtProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;

    public JwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") Duration accessTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    /** 사용자 식별자와 역할을 담은 액세스 토큰을 발급한다. */
    public IssuedToken issueAccessToken(Long userId, UserRole role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenExpiration);

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    /** 서명과 만료 시각을 검증하고 토큰의 회원 식별자와 역할을 인증 principal로 변환한다. */
    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Date issuedAt = claims.getIssuedAt();
        Date expiresAt = claims.getExpiration();
        if (issuedAt == null || expiresAt == null || issuedAt.after(new Date())
                || !issuedAt.before(expiresAt)) {
            throw new IllegalArgumentException("JWT 발급·만료 시각이 올바르지 않습니다.");
        }

        long memberId = parseMemberId(claims.getSubject());
        UserRole role = parseRole(claims.get(ROLE_CLAIM, String.class));
        return new AuthenticatedUser(memberId, role);
    }

    private long parseMemberId(String subject) {
        try {
            long memberId = Long.parseLong(subject);
            if (memberId <= 0) {
                throw new IllegalArgumentException("JWT 회원 ID가 올바르지 않습니다.");
            }
            return memberId;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JWT 회원 ID가 올바르지 않습니다.", e);
        }
    }

    private UserRole parseRole(String role) {
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("JWT 역할이 올바르지 않습니다.", e);
        }
    }

    /** 발급된 토큰과 만료 시각. */
    public record IssuedToken(String value, Instant expiresAt) {
    }
}
