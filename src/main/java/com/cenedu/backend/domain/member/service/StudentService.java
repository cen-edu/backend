package com.cenedu.backend.domain.member.service;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.member.dto.request.StudentCreateRequest;
import com.cenedu.backend.domain.member.dto.request.StudentListRequest;
import com.cenedu.backend.domain.member.dto.response.StudentBulkCreateResponse;
import com.cenedu.backend.domain.member.dto.response.StudentCreateResponse;
import com.cenedu.backend.domain.member.dto.response.StudentListResponse;
import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;
import com.cenedu.backend.domain.member.repository.MemberAccountRepository;
import com.cenedu.backend.domain.member.repository.MemberStudentProfileRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final StudentListQueryService studentListQueryService;
    private final MemberAccountRepository memberAccountRepository;
    private final MemberStudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentBulkCsvParser studentBulkCsvParser;

    /** 학생 생성 - 인증된 교사를 소유자로 지정하고 로그인 아이디를 초기 비밀번호로 설정한다. */
    public StudentCreateResponse createStudent(long teacherId, StudentCreateRequest request) {
        String teacherLoginId = memberAccountService.getRequiredTeacherLoginId(teacherId);
        String teacherLoginIdPrefix = teacherLoginId.substring(0, teacherLoginId.indexOf('@'));

        return createStudentWithGeneratedLoginId(teacherId, teacherLoginIdPrefix, request);
    }

    /** CSV 전체를 검증한 뒤 현재 연도로 학생 계정을 일괄 생성한다. */
    public StudentBulkCreateResponse createStudentsBulk(long teacherId, MultipartFile file) {
        List<StudentCreateRequest> requests = studentBulkCsvParser.parse(file);
        String teacherLoginId = memberAccountService.getRequiredTeacherLoginId(teacherId);
        String teacherLoginIdPrefix = teacherLoginId.substring(0, teacherLoginId.indexOf('@'));
        Set<String> reservedLoginIds = new HashSet<>();

        List<StudentAccountCreator.StudentAccountDraft> drafts = requests.stream()
                .map(request -> createStudentAccountDraft(
                        teacherLoginIdPrefix, request, reservedLoginIds))
                .toList();

        return StudentBulkCreateResponse.from(
                studentAccountCreator.createStudents(teacherId, drafts));
    }

    /** 학생 삭제 - 요청 교사가 소유한 학생 계정을 소프트 삭제한다. */
    @Transactional
    public void deleteStudent(long teacherId, long studentId) {
        getOwnedStudent(teacherId, studentId).delete();
    }

    /** 학생 비밀번호 초기화 - 학생 로그인 아이디를 초기 비밀번호로 다시 설정한다. */
    @Transactional
    public void resetStudentPassword(long teacherId, long studentId) {
        MemberAccount student = getOwnedStudent(teacherId, studentId);
        student.changePassword(passwordEncoder.encode(student.getLoginId()));
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

    /** 중복되지 않는 로그인 아이디를 생성해 학생 계정 생성을 시도한다. */
    private StudentCreateResponse createStudentWithGeneratedLoginId(
        long teacherId,
        String teacherLoginIdPrefix,
        StudentCreateRequest request
    ) {
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

    /** DB와 현재 파일에서 사용하지 않은 로그인 아이디로 학생 계정 초안을 만든다. */
    private StudentAccountCreator.StudentAccountDraft createStudentAccountDraft(
            String teacherLoginIdPrefix,
            StudentCreateRequest request,
            Set<String> reservedLoginIds
    ) {
        String loginId = generateAvailableLoginId(teacherLoginIdPrefix, reservedLoginIds);
        return new StudentAccountCreator.StudentAccountDraft(
                loginId,
                passwordEncoder.encode(loginId),
                request.name().trim(),
                (short) request.registrationYear(),
                (short) request.grade());
    }

    /** DB와 현재 일괄 등록 목록에 없는 학생 로그인 아이디를 생성한다. */
    private String generateAvailableLoginId(
            String teacherLoginIdPrefix,
            Set<String> reservedLoginIds
    ) {
        for (int attempt = 0; attempt <= MAX_LOGIN_ID_GENERATION_RETRIES; attempt++) {
            String loginId = generateStudentLoginId(teacherLoginIdPrefix);
            if (!reservedLoginIds.contains(loginId)
                    && !memberAccountRepository.existsByLoginId(loginId)) {
                reservedLoginIds.add(loginId);
                return loginId;
            }
        }
        throw new BusinessException(ErrorCode.MEMBER_STUDENT_LOGIN_ID_GENERATION_FAILED);
    }

    /** 교사 로그인 아이디의 로컬 파트와 임의의 숫자 8자리로 학생 로그인 아이디를 생성한다. */
    private String generateStudentLoginId(String teacherLoginIdPrefix) {
        int randomNumber = SECURE_RANDOM.nextInt(STUDENT_LOGIN_ID_NUMBER_BOUND);
        return teacherLoginIdPrefix + STUDENT_LOGIN_ID_SEPARATOR + "%08d".formatted(randomNumber);
    }

    /** 활성 학생 계정을 조회하고 요청 교사의 소유인지 검증한다. */
    private MemberAccount getOwnedStudent(long teacherId, long studentId) {
        memberAccountService.getRequiredTeacherLoginId(teacherId);

        MemberAccount student = memberAccountRepository.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_FOUND));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_REQUIRED);
        }

        MemberStudentProfile profile = studentProfileRepository.findByUserId(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_FOUND));
        if (!profile.getOwnerTeacher().getId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_OWNED);
        }
        return student;
    }
}
