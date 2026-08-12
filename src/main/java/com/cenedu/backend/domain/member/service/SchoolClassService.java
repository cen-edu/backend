package com.cenedu.backend.domain.member.service;

import java.util.List;

import com.cenedu.backend.domain.member.dto.request.ClassEnrollmentCreateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassCreateRequest;
import com.cenedu.backend.domain.member.dto.response.ClassEnrollmentResponse;
import com.cenedu.backend.domain.member.dto.response.SchoolClassResponse;
import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.entity.MemberClassEnrollment;
import com.cenedu.backend.domain.member.entity.MemberSchoolClass;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;
import com.cenedu.backend.domain.member.repository.MemberAccountRepository;
import com.cenedu.backend.domain.member.repository.MemberClassEnrollmentRepository;
import com.cenedu.backend.domain.member.repository.MemberSchoolClassRepository;
import com.cenedu.backend.domain.member.repository.MemberStudentProfileRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 교사가 소유하는 반과 학생 배정을 관리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolClassService {

    private final MemberAccountRepository memberAccountRepository;
    private final MemberStudentProfileRepository studentProfileRepository;
    private final MemberSchoolClassRepository schoolClassRepository;
    private final MemberClassEnrollmentRepository enrollmentRepository;

    /** 인증된 교사를 담임으로 지정하고 교사의 마지막 순서에 반을 생성한다. */
    @Transactional
    public SchoolClassResponse createClass(long teacherId, SchoolClassCreateRequest request) {
        MemberAccount teacher = getRequiredTeacher(teacherId);
        int displayOrder = nextDisplayOrder(teacherId);

        MemberSchoolClass schoolClass = MemberSchoolClass.create(
                (short) request.academicYear(),
                (short) request.grade(),
                request.name().trim(),
                teacher,
                displayOrder
        );

        return SchoolClassResponse.from(schoolClassRepository.save(schoolClass), 0);
    }

    /** 인증된 교사가 소유한 활성 반 목록과 각 반의 배정 인원을 반환한다. */
    public List<SchoolClassResponse> getClasses(long teacherId) {
        getRequiredTeacher(teacherId);
        return schoolClassRepository
                .findAllByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(teacherId)
                .stream()
                .map(schoolClass -> SchoolClassResponse.from(
                        schoolClass,
                        enrollmentRepository.countBySchoolClassIdAndStudentDeletedAtIsNull(
                                schoolClass.getId())))
                .toList();
    }

    /** 교사 소유 반에 담당 학생을 배정하고 생성된 배정 정보를 반환한다. */
    @Transactional
    public ClassEnrollmentResponse enrollStudent(long teacherId, long classId,
                                                  ClassEnrollmentCreateRequest request) {
        getRequiredTeacher(teacherId);
        MemberSchoolClass schoolClass = getOwnedClass(teacherId, classId);
        MemberStudentProfile profile = getOwnedStudentProfile(teacherId, request.studentId());
        MemberAccount student = profile.getUser();

        if (schoolClass.getGrade() != profile.getGrade()) {
            throw new BusinessException(ErrorCode.MEMBER_CLASS_GRADE_MISMATCH);
        }
        if (enrollmentRepository.existsBySchoolClassIdAndStudentId(classId, student.getId())) {
            throw new BusinessException(ErrorCode.MEMBER_CLASS_ALREADY_ENROLLED);
        }

        try {
            MemberClassEnrollment enrollment = enrollmentRepository.saveAndFlush(
                    MemberClassEnrollment.create(schoolClass, student));
            return ClassEnrollmentResponse.from(enrollment, profile);
        } catch (DataIntegrityViolationException exception) {
            // 사전 중복 검사 이후 같은 학생이 동시에 배정되는 경쟁 조건도 동일하게 처리한다.
            throw new BusinessException(ErrorCode.MEMBER_CLASS_ALREADY_ENROLLED);
        }
    }

    /** 교사 소유 반에 배정된 학생 목록을 이름순으로 반환한다. */
    public List<ClassEnrollmentResponse> getEnrolledStudents(long teacherId, long classId) {
        getRequiredTeacher(teacherId);
        getOwnedClass(teacherId, classId);
        return enrollmentRepository
                .findAllBySchoolClassIdAndStudentDeletedAtIsNullOrderByStudentNameAscStudentIdAsc(
                        classId)
                .stream()
                .map(enrollment -> ClassEnrollmentResponse.from(
                        enrollment,
                        getRequiredStudentProfile(enrollment.getStudent().getId())))
                .toList();
    }

    /** 교사 소유 반에서 지정한 학생의 배정을 해제한다. */
    @Transactional
    public void removeStudent(long teacherId, long classId, long studentId) {
        getRequiredTeacher(teacherId);
        getOwnedClass(teacherId, classId);
        MemberClassEnrollment enrollment = enrollmentRepository
                .findBySchoolClassIdAndStudentId(classId, studentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_CLASS_ENROLLMENT_NOT_FOUND));
        enrollmentRepository.delete(enrollment);
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

    /** 활성 반을 조회하고 요청 교사의 소유인지 검증한다. */
    private MemberSchoolClass getOwnedClass(long teacherId, long classId) {
        MemberSchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_SCHOOL_CLASS_NOT_FOUND));
        if (!schoolClass.getHomeroomTeacher().getId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.MEMBER_SCHOOL_CLASS_NOT_OWNED);
        }
        return schoolClass;
    }

    /** 활성 학생 계정과 프로필을 조회하고 요청 교사의 담당 학생인지 검증한다. */
    private MemberStudentProfile getOwnedStudentProfile(long teacherId, long studentId) {
        MemberAccount student = memberAccountRepository.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_FOUND));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_REQUIRED);
        }
        MemberStudentProfile profile = getRequiredStudentProfile(studentId);
        if (!profile.getOwnerTeacher().getId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_OWNED);
        }
        return profile;
    }

    /** 학생 ID에 대응하는 프로필을 조회한다. */
    private MemberStudentProfile getRequiredStudentProfile(long studentId) {
        return studentProfileRepository.findByUserId(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_FOUND));
    }

    /** 교사가 가진 마지막 반 다음의 표시 순서를 계산한다. */
    private int nextDisplayOrder(long teacherId) {
        return schoolClassRepository
                .findTopByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderDesc(teacherId)
                .map(schoolClass -> schoolClass.getDisplayOrder() + 1)
                .orElse(0);
    }
}
