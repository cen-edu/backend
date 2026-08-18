package com.cenedu.backend.domain.member.service;

import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.domain.member.dto.response.StudentCreateResponse;
import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;
import com.cenedu.backend.domain.member.repository.MemberAccountRepository;
import com.cenedu.backend.domain.member.repository.MemberStudentProfileRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 학생 로그인 아이디 생성 시도 하나를 독립된 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
public class StudentAccountCreator {

    private final MemberAccountRepository memberAccountRepository;
    private final MemberStudentProfileRepository studentProfileRepository;

    /** 교사와 학생 정보를 검증하고 계정과 프로필을 한 트랜잭션으로 저장한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentCreateResponse createStudent(long teacherId, String loginId, String passwordHash,
                                               String name, short registrationYear, short grade) {
        MemberAccount teacher = memberAccountRepository.findByIdAndDeletedAtIsNull(teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_TEACHER_NOT_FOUND));
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.MEMBER_TEACHER_REQUIRED);
        }

        MemberAccount student = MemberAccount.createStudent(loginId, passwordHash, name);
        memberAccountRepository.saveAndFlush(student);

        MemberStudentProfile profile = MemberStudentProfile.create(
                student,
                registrationYear,
                grade,
                teacher
        );
        studentProfileRepository.save(profile);

        return StudentCreateResponse.from(student, profile);
    }

    /** 검증된 학생 계정 초안 전체를 하나의 트랜잭션으로 저장한다. */
    @Transactional
    public List<StudentCreateResponse> createStudents(
            long teacherId,
            List<StudentAccountDraft> drafts
    ) {
        MemberAccount teacher = memberAccountRepository.findByIdAndDeletedAtIsNull(teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_TEACHER_NOT_FOUND));
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.MEMBER_TEACHER_REQUIRED);
        }

        List<StudentCreateResponse> responses = new ArrayList<>(drafts.size());
        for (StudentAccountDraft draft : drafts) {
            MemberAccount student = MemberAccount.createStudent(
                    draft.loginId(), draft.passwordHash(), draft.name());
            memberAccountRepository.save(student);

            MemberStudentProfile profile = MemberStudentProfile.create(
                    student, draft.registrationYear(), draft.grade(), teacher);
            studentProfileRepository.save(profile);
            responses.add(StudentCreateResponse.from(student, profile));
        }
        memberAccountRepository.flush();
        studentProfileRepository.flush();
        return List.copyOf(responses);
    }

    /** 저장 직전 학생 계정과 프로필 생성에 필요한 값. */
    public record StudentAccountDraft(
            String loginId,
            String passwordHash,
            String name,
            short registrationYear,
            short grade
    ) {
    }
}
