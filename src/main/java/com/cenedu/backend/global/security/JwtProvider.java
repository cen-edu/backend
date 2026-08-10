package com.cenedu.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.cenedu.backend.global.common.enums.UserRole;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 인증된 사용자 정보로 서명된 JWT 액세스 토큰을 발급한다. */
@Component
public class JwtProvider {

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
                .claim("role", role.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    /** 발급된 토큰과 만료 시각. */
    public record IssuedToken(String value, Instant expiresAt) {
    }
}
