package com.cenedu.backend.domain.member.service;

import java.security.SecureRandom;

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

    private static final String STUDENT_LOGIN_ID_SEPARATOR = "_S";
    private static final int STUDENT_LOGIN_ID_NUMBER_BOUND = 100_000_000;
    private static final String LOGIN_ID_UNIQUE_CONSTRAINT = "uk_member_account_login_id";
    private static final int MAX_LOGIN_ID_GENERATION_RETRIES = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StudentAccountCreator studentAccountCreator;
    private final MemberAccountService memberAccountService;
    private final PasswordEncoder passwordEncoder;

    /** 인증된 교사를 소유자로 지정하고 로그인 아이디를 초기 비밀번호로 설정한다. */
    public StudentCreateResponse createStudent(long teacherId, StudentCreateRequest request) {
        String teacherLoginId = memberAccountService.getRequiredTeacherLoginId(teacherId);
        String teacherLoginIdPrefix = teacherLoginId.substring(0, teacherLoginId.indexOf('@'));

        // 아이디 생성할 때 중복되면 최대 3회까지 재시도
        for (int attempt = 0; attempt <= MAX_LOGIN_ID_GENERATION_RETRIES; attempt++) {
            String loginId = generateStudentLoginId(teacherLoginIdPrefix);
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

    /** 교사 로그인 아이디의 로컬 파트와 임의의 숫자 8자리로 학생 로그인 아이디를 생성한다. */
    private String generateStudentLoginId(String teacherLoginIdPrefix) {
        int randomNumber = SECURE_RANDOM.nextInt(STUDENT_LOGIN_ID_NUMBER_BOUND);
        return teacherLoginIdPrefix + STUDENT_LOGIN_ID_SEPARATOR + "%08d".formatted(randomNumber);
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
