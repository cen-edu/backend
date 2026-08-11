package com.cenedu.backend.domain.member.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.member.dto.request.StudentListRequest;
import com.cenedu.backend.domain.member.dto.request.StudentSort;
import com.cenedu.backend.domain.member.dto.response.StudentListItemResponse;
import com.cenedu.backend.domain.member.dto.response.StudentListResponse;
import com.cenedu.backend.domain.member.entity.MemberClassEnrollment;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;
import com.cenedu.backend.domain.member.repository.MemberClassEnrollmentRepository;
import com.cenedu.backend.domain.member.repository.MemberStudentProfileRepository;
import com.cenedu.backend.domain.member.repository.MemberStudentProfileSpecifications;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학생 목록 조회와 응답 조합만 담당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentListQueryService {

    private final MemberStudentProfileRepository studentProfileRepository;
    private final MemberClassEnrollmentRepository enrollmentRepository;

    /** 교사 소유 학생과 각 학생의 활성 반 정보를 페이지 단위로 반환한다. */
    public StudentListResponse getStudents(long teacherId, StudentListRequest request) {
        Page<MemberStudentProfile> studentPage = studentProfileRepository.findAll(
                MemberStudentProfileSpecifications.ownedStudents(
                        teacherId,
                        toShort(request.registrationYear()),
                        toShort(request.grade()),
                        request.classId(),
                        normalizeKeyword(request.keyword())),
                PageRequest.of(
                        request.resolvedPage(),
                        request.resolvedSize(),
                        toSort(request.resolvedSort()))
        );

        Map<Long, List<MemberClassEnrollment>> enrollmentsByStudentId =
                getEnrollmentsByStudentId(teacherId, studentPage.getContent());

        List<StudentListItemResponse> students = studentPage.getContent().stream()
                .map(profile -> StudentListItemResponse.from(
                        profile,
                        enrollmentsByStudentId.getOrDefault(profile.getUserId(), List.of())))
                .toList();

        return StudentListResponse.from(studentPage, students);
    }

    /** 조회된 학생들의 활성 반 배정을 학생 ID 기준으로 묶어 반환한다. */
    private Map<Long, List<MemberClassEnrollment>> getEnrollmentsByStudentId(
            long teacherId,
            List<MemberStudentProfile> profiles
    ) {
        List<Long> studentIds = profiles.stream()
                .map(MemberStudentProfile::getUserId)
                .toList();
        if (studentIds.isEmpty()) {
            return Map.of();
        }

        return enrollmentRepository.findAllActiveByTeacherIdAndStudentIdIn(teacherId, studentIds)
                .stream()
                .collect(Collectors.groupingBy(enrollment -> enrollment.getStudent().getId()));
    }

    /** 선택 정수 필터를 엔티티 필드 타입으로 변환한다. */
    private Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    /** 공백 검색어를 검색 조건 없음으로 정규화한다. */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    /** 요청 정렬값을 안정적인 학생 계정 ID 보조 정렬이 포함된 정렬 조건으로 변환한다. */
    private Sort toSort(StudentSort sort) {
        return switch (sort) {
            case OLDEST -> Sort.by(Sort.Order.asc("user.createdAt"), Sort.Order.asc("user.id"));
            case NAME_ASC -> Sort.by(Sort.Order.asc("user.name"), Sort.Order.asc("user.id"));
            case LATEST -> Sort.by(Sort.Order.desc("user.createdAt"), Sort.Order.desc("user.id"));
        };
    }
}
