package com.cenedu.backend.domain.member.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.member.dto.request.ClassStudentCandidateListRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassCreateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassListRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassOrderUpdateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassUpdateRequest;
import com.cenedu.backend.domain.member.dto.response.ClassStudentCandidateResponse;
import com.cenedu.backend.domain.member.dto.response.SchoolClassDetailResponse;
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
        List<Long> studentIds = request.resolvedStudentIds();
        validateUniqueStudentIds(studentIds);
        List<MemberStudentProfile> studentProfiles = getOwnedStudentProfiles(
                teacherId,
                (short) request.grade(),
                studentIds
        );
        int displayOrder = nextDisplayOrder(teacherId);

        MemberSchoolClass schoolClass = MemberSchoolClass.create(
                (short) request.academicYear(),
                (short) request.grade(),
                request.name().trim(),
                teacher,
                displayOrder
        );
        MemberSchoolClass savedClass = schoolClassRepository.save(schoolClass);

        if (!studentProfiles.isEmpty()) {
            List<MemberClassEnrollment> enrollments = studentProfiles.stream()
                    .map(profile -> MemberClassEnrollment.create(savedClass, profile.getUser()))
                    .toList();
            enrollmentRepository.saveAll(enrollments);
        }

        return SchoolClassResponse.from(savedClass, studentProfiles.size());
    }

    /** 인증된 교사가 소유한 활성 반 목록과 각 반의 배정 인원을 반환한다. */
    public List<SchoolClassResponse> getClasses(
            long teacherId,
            SchoolClassListRequest request
    ) {
        getRequiredTeacher(teacherId);
        return schoolClassRepository
                .findAllForClassList(
                        teacherId,
                        toShort(request.academicYear()),
                        toShort(request.grade()),
                        normalizeKeyword(request.keyword())
                )
                .stream()
                .map(schoolClass -> SchoolClassResponse.from(
                        schoolClass,
                        enrollmentRepository.countBySchoolClassIdAndStudentDeletedAtIsNull(
                                schoolClass.getId())))
                .toList();
    }

    /** 교사 소유 학생 중 반 학년과 이름 검색 조건에 맞는 학생을 모두 반환한다. */
    public List<ClassStudentCandidateResponse> getClassStudentCandidates(
            long teacherId,
            ClassStudentCandidateListRequest request
    ) {
        getRequiredTeacher(teacherId);
        String keyword = normalizeKeyword(request.keyword());
        List<MemberStudentProfile> profiles;

        if (keyword == null) {
            profiles = studentProfileRepository
                    .findAllClassCandidates(
                            teacherId,
                            request.grade().shortValue()
                    );
        } else {
            profiles = studentProfileRepository
                    .findAllClassCandidatesByKeyword(
                            teacherId,
                            request.grade().shortValue(),
                            keyword
                    );
        }

        return profiles.stream()
                .map(ClassStudentCandidateResponse::from)
                .toList();
    }

    /** 교사 소유 반의 기본 정보와 현재 배정된 활성 학생을 반환한다. */
    public SchoolClassDetailResponse getClassDetail(long teacherId, long classId) {
        getRequiredTeacher(teacherId);
        MemberSchoolClass schoolClass = getOwnedClass(teacherId, classId);
        List<MemberStudentProfile> studentProfiles = enrollmentRepository
                .findAllBySchoolClassIdAndStudentDeletedAtIsNullOrderByStudentNameAscStudentIdAsc(
                        classId)
                .stream()
                .map(enrollment -> getRequiredStudentProfile(enrollment.getStudent().getId()))
                .toList();

        return SchoolClassDetailResponse.from(schoolClass, studentProfiles);
    }

    /** 반 정보와 최종 선택 학생 목록을 한 트랜잭션에서 함께 수정한다. */
    @Transactional
    public SchoolClassDetailResponse updateClass(
            long teacherId,
            long classId,
            SchoolClassUpdateRequest request
    ) {
        getRequiredTeacher(teacherId);
        MemberSchoolClass schoolClass = getOwnedClass(teacherId, classId);
        List<Long> studentIds = List.copyOf(request.studentIds());
        validateUniqueStudentIds(studentIds);
        List<MemberStudentProfile> studentProfiles = getOwnedStudentProfiles(
                teacherId,
                (short) request.grade(),
                studentIds
        );

        synchronizeClassEnrollments(schoolClass, studentProfiles);
        schoolClass.updateDetails(
                (short) request.academicYear(),
                (short) request.grade(),
                request.name().trim()
        );

        return SchoolClassDetailResponse.from(schoolClass, studentProfiles);
    }

    /** 전달된 최종 ID 순서대로 교사 소유 활성 반의 표시 순서를 다시 매긴다. */
    @Transactional
    public void updateClassOrder(long teacherId, SchoolClassOrderUpdateRequest request) {
        getRequiredTeacher(teacherId);
        List<MemberSchoolClass> schoolClasses = schoolClassRepository
                .findAllByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(teacherId);
        List<Long> requestedClassIds = request.classIds();

        if (requestedClassIds.size() != schoolClasses.size()
                || new HashSet<>(requestedClassIds).size() != requestedClassIds.size()) {
            throw new BusinessException(ErrorCode.MEMBER_SCHOOL_CLASS_ORDER_INVALID);
        }

        Map<Long, MemberSchoolClass> schoolClassesById = new HashMap<>();
        schoolClasses.forEach(schoolClass ->
                schoolClassesById.put(schoolClass.getId(), schoolClass));

        for (int displayOrder = 0; displayOrder < requestedClassIds.size(); displayOrder++) {
            MemberSchoolClass schoolClass = schoolClassesById.get(
                    requestedClassIds.get(displayOrder));
            if (schoolClass == null) {
                throw new BusinessException(ErrorCode.MEMBER_SCHOOL_CLASS_ORDER_INVALID);
            }
            schoolClass.changeDisplayOrder(displayOrder);
        }
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

    /** 학생 ID에 대응하는 프로필을 조회한다. */
    private MemberStudentProfile getRequiredStudentProfile(long studentId) {
        return studentProfileRepository.findByUserId(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_FOUND));
    }

    /** 생성 요청의 학생 ID가 서로 중복되지 않는지 검증한다. */
    private void validateUniqueStudentIds(List<Long> studentIds) {
        if (new HashSet<>(studentIds).size() != studentIds.size()) {
            throw new BusinessException(ErrorCode.MEMBER_CLASS_STUDENT_IDS_DUPLICATED);
        }
    }

    /** 요청 학생 전체가 교사 소유의 활성 학생이고 반 학년과 일치하는지 검증한다. */
    private List<MemberStudentProfile> getOwnedStudentProfiles(
            long teacherId,
            short classGrade,
            List<Long> studentIds
    ) {
        if (studentIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MemberStudentProfile> profilesByStudentId = studentProfileRepository
                .findAllByUserIdIn(studentIds)
                .stream()
                .collect(Collectors.toMap(
                        MemberStudentProfile::getUserId,
                        profile -> profile
                ));

        return studentIds.stream()
                .map(studentId -> validateClassStudent(
                        teacherId,
                        classGrade,
                        profilesByStudentId.get(studentId)
                ))
                .toList();
    }

    /** 한 학생의 존재 여부와 역할, 소유 교사, 학년을 검증한다. */
    private MemberStudentProfile validateClassStudent(
            long teacherId,
            short classGrade,
            MemberStudentProfile profile
    ) {
        if (profile == null || profile.getUser().deleted()) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_FOUND);
        }
        if (profile.getUser().getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_REQUIRED);
        }
        if (!profile.getOwnerTeacher().getId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_NOT_OWNED);
        }
        if (profile.getGrade() != classGrade) {
            throw new BusinessException(ErrorCode.MEMBER_CLASS_GRADE_MISMATCH);
        }
        return profile;
    }

    /** 반 배정을 요청의 최종 학생 목록과 일치하도록 추가하고 해제한다. */
    private void synchronizeClassEnrollments(
            MemberSchoolClass schoolClass,
            List<MemberStudentProfile> requestedProfiles
    ) {
        List<MemberClassEnrollment> currentEnrollments = enrollmentRepository
                .findAllBySchoolClassId(schoolClass.getId());
        Set<Long> requestedStudentIds = requestedProfiles.stream()
                .map(MemberStudentProfile::getUserId)
                .collect(Collectors.toSet());
        Set<Long> currentStudentIds = currentEnrollments.stream()
                .map(enrollment -> enrollment.getStudent().getId())
                .collect(Collectors.toSet());

        List<MemberClassEnrollment> enrollmentsToRemove = currentEnrollments.stream()
                .filter(enrollment -> !requestedStudentIds.contains(
                        enrollment.getStudent().getId()))
                .toList();
        List<MemberClassEnrollment> enrollmentsToAdd = requestedProfiles.stream()
                .filter(profile -> !currentStudentIds.contains(profile.getUserId()))
                .map(profile -> MemberClassEnrollment.create(schoolClass, profile.getUser()))
                .toList();

        enrollmentRepository.deleteAll(enrollmentsToRemove);
        enrollmentRepository.saveAll(enrollmentsToAdd);
    }

    /** 공백 검색어를 검색 조건 없음으로 정규화한다. */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    /** 선택 정수 필터를 엔티티 필드 타입으로 변환한다. */
    private Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    /** 교사가 가진 마지막 반 다음의 표시 순서를 계산한다. */
    private int nextDisplayOrder(long teacherId) {
        return schoolClassRepository
                .findTopByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderDesc(teacherId)
                .map(schoolClass -> schoolClass.getDisplayOrder() + 1)
                .orElse(0);
    }
}
