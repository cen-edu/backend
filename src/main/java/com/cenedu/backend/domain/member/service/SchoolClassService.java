package com.cenedu.backend.domain.member.service;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.member.dto.request.ClassStudentCandidateListRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassCreateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassDeleteRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassListRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassOrderUpdateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassUpdateRequest;
import com.cenedu.backend.domain.member.dto.response.AcademicContextResponse;
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

    private static final List<AcademicContextResponse.SemesterOption> SEMESTER_OPTIONS = List.of(
            new AcademicContextResponse.SemesterOption(1, "1학기"),
            new AcademicContextResponse.SemesterOption(2, "2학기")
    );

    private final MemberAccountRepository memberAccountRepository;
    private final MemberStudentProfileRepository studentProfileRepository;
    private final MemberSchoolClassRepository schoolClassRepository;
    private final MemberClassEnrollmentRepository enrollmentRepository;

    /** 교사가 담당하는 활성 반을 학년도·학년·반 계층과 독립 학기 옵션으로 반환한다. */
    public AcademicContextResponse getAcademicContexts(long teacherId) {
        getRequiredTeacher(teacherId);
        List<MemberSchoolClass> schoolClasses = schoolClassRepository
                .findAllByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(teacherId);

        Map<Short, Map<Short, List<MemberSchoolClass>>> classesByAcademicYearAndGrade =
                new TreeMap<>(Comparator.reverseOrder());
        for (MemberSchoolClass schoolClass : schoolClasses) {
            classesByAcademicYearAndGrade
                    .computeIfAbsent(
                            schoolClass.getAcademicYear(),
                            ignored -> new TreeMap<>())
                    .computeIfAbsent(schoolClass.getGrade(), ignored -> new ArrayList<>())
                    .add(schoolClass);
        }

        List<AcademicContextResponse.AcademicYearOption> academicYears =
                classesByAcademicYearAndGrade.entrySet().stream()
                        .map(yearEntry -> new AcademicContextResponse.AcademicYearOption(
                                yearEntry.getKey(),
                                yearEntry.getValue().entrySet().stream()
                                        .map(gradeEntry -> new AcademicContextResponse.GradeOption(
                                                gradeEntry.getKey(),
                                                gradeEntry.getValue().stream()
                                                        .sorted(Comparator
                                                                .comparingInt(MemberSchoolClass::getDisplayOrder)
                                                                .thenComparing(MemberSchoolClass::getId))
                                                        .map(schoolClass ->
                                                                new AcademicContextResponse.ClassOption(
                                                                        schoolClass.getId(),
                                                                        schoolClass.getName(),
                                                                        schoolClass.getDisplayOrder()))
                                                        .toList()))
                                        .toList()))
                        .toList();

        Integer defaultAcademicYear = resolveDefaultAcademicYear(
                classesByAcademicYearAndGrade);
        return new AcademicContextResponse(
                academicYears,
                SEMESTER_OPTIONS,
                new AcademicContextResponse.Defaults(defaultAcademicYear, null, null, null)
        );
    }

    /** 인증된 교사를 담임으로 지정하고 교사의 마지막 순서에 반을 생성한다. */
    @Transactional
    public SchoolClassResponse createClass(long teacherId, SchoolClassCreateRequest request) {
        MemberAccount teacher = getRequiredTeacher(teacherId);
        List<Long> studentIds = request.resolvedStudentIds();
        validateUniqueStudentIds(studentIds);
        List<MemberStudentProfile> studentProfiles = getOwnedStudentProfiles(
                teacherId,
                (short) request.grade(),
                (short) request.academicYear(),
                null,
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
        Short academicYear = toShort(request.academicYear());
        Short grade = toShort(request.grade());
        String keyword = normalizeKeyword(request.keyword());
        List<MemberSchoolClass> schoolClasses;

        if (keyword == null) {
            schoolClasses = schoolClassRepository.findAllForClassList(
                    teacherId,
                    academicYear,
                    grade
            );
        } else {
            schoolClasses = schoolClassRepository.findAllForClassListByKeyword(
                    teacherId,
                    academicYear,
                    grade,
                    keyword
            );
        }

        return schoolClasses.stream()
                .map(schoolClass -> SchoolClassResponse.from(
                        schoolClass,
                        enrollmentRepository.countBySchoolClassIdAndStudentDeletedAtIsNull(
                                schoolClass.getId())))
                .toList();
    }

    /**
     * 반 ID 목록으로 이름을 반환한다. 존재하지 않거나 이 교사의 반이 아닌 ID는 결과에서 빠진다.
     *
     * <p>다른 도메인이 "이 배포가 어느 반이더라"만 알면 될 때 쓴다. {@link #getClasses}는 교사 검증
     * 1회 + 반 목록 1회 + <b>반마다 인원 COUNT</b>로 {@code 2+N} 쿼리가 드는데, 그 인원 수는
     * 이름만 필요한 호출부가 그대로 버린다. 여기서는 <b>쿼리 하나</b>로 끝낸다.
     *
     * <p>{@code getRequiredTeacher}를 부르지 않는다 — 소유가 아닌 반은 결과에서 빠지므로 남의 반
     * 이름이 새지 않고, 교사 존재 검증 때문에 쿼리를 하나 더 쓸 이유가 없다.
     * {@code StudentListQueryService#getStudentNamesByIds}와 같은 규약이다.
     */
    public Map<Long, String> getClassNamesByIds(long teacherId, Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return schoolClassRepository
                .findAllByIdInAndHomeroomTeacherIdAndDeletedAtIsNull(classIds, teacherId)
                .stream()
                .collect(Collectors.toMap(MemberSchoolClass::getId, MemberSchoolClass::getName));
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

        Map<Long, MemberSchoolClass> enrolledClassesByStudentId = findActiveEnrollments(
                teacherId,
                profiles.stream().map(MemberStudentProfile::getUserId).toList()
        ).stream().collect(toSchoolClassByStudentId());

        return profiles.stream()
                .map(profile -> ClassStudentCandidateResponse.from(
                        profile,
                        enrolledClassesByStudentId.get(profile.getUserId())))
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
                (short) request.academicYear(),
                classId,
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

    /** 선택한 교사 소유 활성 반을 소프트 삭제하고 남은 반의 표시 순서를 정리한다. */
    @Transactional
    public void deleteClasses(long teacherId, SchoolClassDeleteRequest request) {
        getRequiredTeacher(teacherId);
        List<Long> classIds = request.classIds();
        if (new HashSet<>(classIds).size() != classIds.size()) {
            throw new BusinessException(ErrorCode.MEMBER_SCHOOL_CLASS_IDS_DUPLICATED);
        }

        List<MemberSchoolClass> schoolClasses = classIds.stream()
                .map(classId -> getOwnedClass(teacherId, classId))
                .toList();
        schoolClasses.forEach(MemberSchoolClass::delete);
        enrollmentRepository.deleteAllBySchoolClassIdIn(classIds);

        List<MemberSchoolClass> remainingClasses = schoolClassRepository
                .findAllByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(teacherId);
        for (int displayOrder = 0; displayOrder < remainingClasses.size(); displayOrder++) {
            remainingClasses.get(displayOrder).changeDisplayOrder(displayOrder);
        }
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

    /** 현재 학년도에 담당 반이 없으면 가장 최근 담당 학년도를 기본값으로 반환한다. */
    private Integer resolveDefaultAcademicYear(
            Map<Short, Map<Short, List<MemberSchoolClass>>> classesByAcademicYearAndGrade
    ) {
        if (classesByAcademicYearAndGrade.isEmpty()) {
            return null;
        }
        short currentYear = (short) Year.now().getValue();
        if (classesByAcademicYearAndGrade.containsKey(currentYear)) {
            return (int) currentYear;
        }
        return (int) classesByAcademicYearAndGrade.keySet().iterator().next();
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

    /**
     * 요청 학생 전체가 교사 소유의 활성 학생이고, 반 학년과 일치하며, 같은 학년도의 다른 활성
     * 반에 소속돼 있지 않은지 검증한다.
     *
     * <p>{@code excludeClassId}는 편집 중인 반이다. 그 반에 이미 들어 있는 학생은 충돌이 아니다.
     * 생성 시에는 {@code null}을 넘긴다.
     */
    private List<MemberStudentProfile> getOwnedStudentProfiles(
            long teacherId,
            short classGrade,
            short academicYear,
            Long excludeClassId,
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
        Map<Long, MemberSchoolClass> conflictingClassesByStudentId =
                findActiveEnrollments(teacherId, studentIds).stream()
                        .filter(enrollment -> {
                            MemberSchoolClass enrolledClass = enrollment.getSchoolClass();
                            return enrolledClass.getAcademicYear() == academicYear
                                    && !enrolledClass.getId().equals(excludeClassId);
                        })
                        .collect(toSchoolClassByStudentId());

        return studentIds.stream()
                .map(studentId -> validateClassStudent(
                        teacherId,
                        classGrade,
                        profilesByStudentId.get(studentId),
                        conflictingClassesByStudentId.get(studentId)
                ))
                .toList();
    }

    /**
     * 학생들이 소속된 교사 소유 활성 반의 배정을 <b>한 번의 조회</b>로 가져온다.
     *
     * <p>학생 1명당 조회하면 N+1이 되므로 호출부는 학생 ID 전체를 한 번에 넘긴다.
     */
    private List<MemberClassEnrollment> findActiveEnrollments(
            long teacherId,
            List<Long> studentIds
    ) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return enrollmentRepository.findAllActiveByTeacherIdAndStudentIdIn(teacherId, studentIds);
    }

    /**
     * 배정을 학생 ID별 반으로 접는다.
     *
     * <p>학생이 여러 학년도의 반에 걸쳐 있으면 쿼리 정렬(학년도 내림차순)의 첫 반, 즉 가장 최근
     * 학년도 반을 남긴다. 접기 전에 걸러야 하는 조건이 있으면 호출부가 먼저 걸러서 넘긴다.
     */
    private Collector<MemberClassEnrollment, ?, Map<Long, MemberSchoolClass>>
    toSchoolClassByStudentId() {
        return Collectors.toMap(
                enrollment -> enrollment.getStudent().getId(),
                MemberClassEnrollment::getSchoolClass,
                (first, ignored) -> first
        );
    }

    /** 한 학생의 존재 여부와 역할, 소유 교사, 학년, 다른 반 소속 여부를 검증한다. */
    private MemberStudentProfile validateClassStudent(
            long teacherId,
            short classGrade,
            MemberStudentProfile profile,
            MemberSchoolClass conflictingClass
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
        if (conflictingClass != null) {
            throw new BusinessException(ErrorCode.MEMBER_CLASS_STUDENT_ALREADY_ENROLLED);
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
