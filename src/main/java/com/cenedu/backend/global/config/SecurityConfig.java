package com.cenedu.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 설정 — 5단계(JWT 인증)에서 전면 교체된다.
 *
 * <p>spring-boot-starter-security 를 추가하면 기본 설정이 모든 요청을 로그인 폼으로 막는다.
 * JWT 인증이 들어오기 전까지 앱이 정상 동작하도록 전체 허용으로 열어둔다.
 * 이 상태로 배포하지 않는다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
