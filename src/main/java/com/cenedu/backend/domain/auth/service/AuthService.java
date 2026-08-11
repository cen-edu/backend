package com.cenedu.backend.domain.auth.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cenedu.backend.domain.auth.dto.request.LoginRequest;
import com.cenedu.backend.domain.auth.dto.request.SignupRequest;
import com.cenedu.backend.domain.auth.dto.response.LoginResponse;
import com.cenedu.backend.domain.auth.dto.response.SignupResponse;
import com.cenedu.backend.domain.member.dto.response.MemberAccountCredentials;
import com.cenedu.backend.domain.member.dto.response.MemberAccountResponse;
import com.cenedu.backend.domain.member.service.MemberAccountService;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.security.JwtProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 교사 가입과 로그인 인증을 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final Pattern STUDENT_LOGIN_ID_PATTERN =
            Pattern.compile("^(.+)_s(\\d{8})$", Pattern.CASE_INSENSITIVE);

    private final MemberAccountService memberAccountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** BCrypt로 비밀번호를 암호화하고 회원 서비스에 중복 검사를 위임해 교사 계정을 생성한다. */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        MemberAccountResponse account = memberAccountService.createTeacher(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim()
        );
        return SignupResponse.from(account);
    }

    /** 교사 이메일 또는 학생 로그인 아이디와 비밀번호를 검증하고 JWT를 발급한다. */
    public LoginResponse login(LoginRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        MemberAccountCredentials account = memberAccountService.findActiveAccountByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        JwtProvider.IssuedToken issuedToken = jwtProvider.issueAccessToken(account.id(), account.role());
        return LoginResponse.of(
                issuedToken.value(),
                issuedToken.expiresAt(),
                account.id(),
                account.loginId(),
                account.name(),
                account.role()
        );
    }

    /** 이메일 비교와 저장에 사용할 소문자 정규형을 반환한다. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 교사 이메일과 서버 발급 학생 아이디의 비교용 정규형을 반환한다. */
    private String normalizeLoginId(String loginId) {
        String trimmedLoginId = loginId.trim();
        Matcher studentLoginIdMatcher = STUDENT_LOGIN_ID_PATTERN.matcher(trimmedLoginId);
        if (studentLoginIdMatcher.matches()) {
            return studentLoginIdMatcher.group(1).toLowerCase(Locale.ROOT)
                    + "_S"
                    + studentLoginIdMatcher.group(2);
        }
        return trimmedLoginId.toLowerCase(Locale.ROOT);
    }
}
