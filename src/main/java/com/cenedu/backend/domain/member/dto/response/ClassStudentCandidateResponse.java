package com.cenedu.backend.domain.member.dto.response;

import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

/** 반 만들기 모달에서 선택할 수 있는 학생 정보. */
public record ClassStudentCandidateResponse(
        Long id,
        String name,
        short grade
) {

    /** 학생 프로필을 반 만들기용 학생 응답으로 변환한다. */
    public static ClassStudentCandidateResponse from(MemberStudentProfile profile) {
        return new ClassStudentCandidateResponse(
                profile.getUserId(),
                profile.getUser().getName(),
                profile.getGrade()
        );
    }
}
