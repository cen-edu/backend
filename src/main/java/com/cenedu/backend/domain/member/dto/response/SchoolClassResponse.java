package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberSchoolClass;

/** 교사가 관리하는 반 정보와 현재 배정 학생 수. */
public record SchoolClassResponse(
        Long id,
        short academicYear,
        short grade,
        String name,
        Long homeroomTeacherId,
        int displayOrder,
        long studentCount
) {

    /** 반 엔티티와 배정 인원을 반 응답으로 변환한다. */
    public static SchoolClassResponse from(MemberSchoolClass schoolClass, long studentCount) {
        return new SchoolClassResponse(
                schoolClass.getId(),
                schoolClass.getAcademicYear(),
                schoolClass.getGrade(),
                schoolClass.getName(),
                schoolClass.getHomeroomTeacher().getId(),
                schoolClass.getDisplayOrder(),
                studentCount
        );
    }
}
