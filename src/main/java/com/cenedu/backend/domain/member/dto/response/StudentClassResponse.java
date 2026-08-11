package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberSchoolClass;

/** 학생이 배정된 활성 반의 목록 표시 정보. */
public record StudentClassResponse(
        Long id,
        short academicYear,
        short grade,
        String name
) {

    /** 반 엔티티를 학생 목록의 반 정보로 변환한다. */
    public static StudentClassResponse from(MemberSchoolClass schoolClass) {
        return new StudentClassResponse(
                schoolClass.getId(),
                schoolClass.getAcademicYear(),
                schoolClass.getGrade(),
                schoolClass.getName()
        );
    }
}
