package com.cenedu.backend.domain.member.service;

import java.util.UUID;

import com.cenedu.backend.domain.member.dto.request.StudentCreateRequest;
import com.cenedu.backend.domain.member.dto.response.StudentCreateResponse;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 교사가 소유하는 학생 계정과 학생 프로필을 관리한다. */
@Service
@RequiredArgsConstructor
public class StudentService {

    private static final String STUDENT_LOGIN_ID_PREFIX = "stu_";
    private static final String LOGIN_ID_UNIQUE_CONSTRAINT = "uk_member_account_login_id";
    private static final int MAX_LOGIN_ID_GENERATION_RETRIES = 3;

    private final StudentAccountCreator studentAccountCreator;
    private final PasswordEncoder passwordEncoder;

    /** 인증된 교사를 소유자로 지정하고 로그인 아이디를 초기 비밀번호로 설정한다. */
    public StudentCreateResponse createStudent(long teacherId, StudentCreateRequest request) {
        // 아이디 생성할 때 중복되면 최대 3회까지 재시도
        for (int attempt = 0; attempt <= MAX_LOGIN_ID_GENERATION_RETRIES; attempt++) {
            String loginId = generateStudentLoginId();
            try {
                return studentAccountCreator.createStudent(
                        teacherId,
                        loginId,
                        passwordEncoder.encode(loginId),
                        request.name().trim(),
                        (short) request.registrationYear(),
                        (short) request.grade()
                );
            } catch (DataIntegrityViolationException exception) {
                if (!causedByLoginIdConflict(exception)) {
                    throw exception;
                }
            }
        }

        throw new BusinessException(ErrorCode.MEMBER_STUDENT_LOGIN_ID_GENERATION_FAILED);
    }

    /** 충돌 가능성이 매우 낮은 서버 발급 학생 로그인 아이디를 생성한다. */
    private String generateStudentLoginId() {
        return STUDENT_LOGIN_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /** 로그인 아이디 UNIQUE 제약조건 충돌인지 확인한다. */
    private boolean causedByLoginIdConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return LOGIN_ID_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                        constraintViolation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
