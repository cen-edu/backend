package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberSchoolClass;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

/** 반 만들기 모달에서 선택할 수 있는 학생 정보. */
public record ClassStudentCandidateResponse(
        Long id,
        String name,
        short grade,
        String enrolledClassName,
        Short enrolledAcademicYear
) {

    /** 학생 프로필을 반 만들기용 학생 응답으로 변환한다. */
    public static ClassStudentCandidateResponse from(MemberStudentProfile profile) {
        return from(profile, null);
    }

    /**
     * 학생 프로필과 현재 소속 반을 반 만들기용 학생 응답으로 변환한다.
     *
     * <p>소속 반이 {@code null}이면 소속 필드도 {@code null}이다. 프론트는 만들고 있는 반의
     * 학년도와 {@code enrolledAcademicYear}를 비교해 같으면 선택을 막는다.
     */
    public static ClassStudentCandidateResponse from(
            MemberStudentProfile profile,
            MemberSchoolClass enrolledClass
    ) {
        return new ClassStudentCandidateResponse(
                profile.getUserId(),
                profile.getUser().getName(),
                profile.getGrade(),
                enrolledClass == null ? null : enrolledClass.getName(),
                enrolledClass == null ? null : enrolledClass.getAcademicYear()
        );
    }
}
