package com.cenedu.backend.domain.member.dto.response;

import java.util.List;

import com.cenedu.backend.domain.member.entity.MemberSchoolClass;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

/** 반 상세 모달의 기본 정보와 현재 선택된 학생 목록. */
public record SchoolClassDetailResponse(
        Long id,
        short academicYear,
        short grade,
        String name,
        Long homeroomTeacherId,
        int displayOrder,
        List<ClassStudentCandidateResponse> students
) {

    /** 반 엔티티와 배정 학생 프로필을 상세 응답으로 변환한다. */
    public static SchoolClassDetailResponse from(
            MemberSchoolClass schoolClass,
            List<MemberStudentProfile> studentProfiles
    ) {
        return new SchoolClassDetailResponse(
                schoolClass.getId(),
                schoolClass.getAcademicYear(),
                schoolClass.getGrade(),
                schoolClass.getName(),
                schoolClass.getHomeroomTeacher().getId(),
                schoolClass.getDisplayOrder(),
                studentProfiles.stream()
                        .map(ClassStudentCandidateResponse::from)
                        .toList()
        );
    }
}
