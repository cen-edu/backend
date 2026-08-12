package com.cenedu.backend.domain.member.service;

import java.util.Optional;

import com.cenedu.backend.domain.member.dto.response.MemberAccountCredentials;
import com.cenedu.backend.domain.member.dto.response.MemberAccountResponse;
import com.cenedu.backend.domain.member.dto.response.TeacherAccountResponse;
import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.repository.MemberAccountRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 계정의 공개 경계. 다른 도메인은 저장소 대신 이 서비스를 사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAccountService {

    private final MemberAccountRepository memberAccountRepository;

    /** 로그인 아이디가 이미 등록되어 있는지 확인한다. */
    public boolean existsByLoginId(String loginId) {
        return memberAccountRepository.existsByLoginId(loginId);
    }

    /** 로그인 아이디로 인증에 필요한 활성 계정 정보를 조회한다. */
    public Optional<MemberAccountCredentials> findActiveAccountByLoginId(String loginId) {
        return memberAccountRepository.findByLoginIdAndDeletedAtIsNull(loginId)
                .map(MemberAccountCredentials::from);
    }

    /** 활성 교사 계정을 조회하고 학생 아이디 생성에 사용할 로그인 아이디를 반환한다. */
    public String getRequiredTeacherLoginId(long teacherId) {
        return getRequiredTeacher(teacherId).getLoginId();
    }

    /** 활성 교사의 마이페이지 계정 정보를 반환한다. */
    public TeacherAccountResponse getTeacherAccount(long teacherId) {
        return TeacherAccountResponse.from(getRequiredTeacher(teacherId));
    }

    /** 활성 교사의 비밀번호 검증용 계정 정보를 반환한다. */
    public MemberAccountCredentials getRequiredTeacherCredentials(long teacherId) {
        return MemberAccountCredentials.from(getRequiredTeacher(teacherId));
    }

    /** 활성 학생의 비밀번호 검증용 계정 정보를 반환한다. */
    public MemberAccountCredentials getRequiredStudentCredentials(long studentId) {
        return MemberAccountCredentials.from(getRequiredStudent(studentId));
    }

    /** 교사 계정을 생성하고 외부 공개용 계정 정보를 반환한다. */
    @Transactional
    public MemberAccountResponse createTeacher(String loginId, String passwordHash, String name) {
        if (memberAccountRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.MEMBER_LOGIN_ID_ALREADY_EXISTS);
        }

        MemberAccount teacher = MemberAccount.createTeacher(loginId, passwordHash, name);

        try {
            return MemberAccountResponse.from(memberAccountRepository.saveAndFlush(teacher));
        } catch (DataIntegrityViolationException exception) {
            // 사전 중복 검사 이후 같은 아이디가 동시에 생성되는 경쟁 조건도 동일하게 처리한다.
            throw new BusinessException(ErrorCode.MEMBER_LOGIN_ID_ALREADY_EXISTS);
        }
    }

    /** 교사 회원탈퇴 - 활성 교사 계정을 소프트 삭제한다. */
    @Transactional
    public void withdrawTeacher(long teacherId) {
        getRequiredTeacher(teacherId).delete();
    }

    /** 활성 교사의 비밀번호 해시를 변경한다. */
    @Transactional
    public void changeTeacherPassword(long teacherId, String passwordHash) {
        getRequiredTeacher(teacherId).changePassword(passwordHash);
    }

    /** 활성 학생의 비밀번호 해시를 변경한다. */
    @Transactional
    public void changeStudentPassword(long studentId, String passwordHash) {
        getRequiredStudent(studentId).changePassword(passwordHash);
    }

    /** 활성 교사 계정을 조회하고 역할을 검증한다. */
    private MemberAccount getRequiredTeacher(long teacherId) {
        MemberAccount teacher = memberAccountRepository.findByIdAndDeletedAtIsNull(teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_TEACHER_NOT_FOUND));
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.MEMBER_TEACHER_REQUIRED);
        }
        return teacher;
    }

    /** 활성 학생 계정을 조회하고 역할을 검증한다. */
    private MemberAccount getRequiredStudent(long studentId) {
        MemberAccount student = memberAccountRepository.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_FOUND));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_REQUIRED);
        }
        return student;
    }
}
