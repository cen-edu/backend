package com.cenedu.backend.domain.member.dto.response;

import java.util.List;

import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

import org.springframework.data.domain.Page;

/** 학생 목록과 페이지 탐색에 필요한 메타데이터. */
public record StudentListResponse(
        List<StudentListItemResponse> students,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /** 조회 페이지와 변환된 학생 목록을 API 응답으로 조합한다. */
    public static StudentListResponse from(
            Page<MemberStudentProfile> studentPage,
            List<StudentListItemResponse> students
    ) {
        return new StudentListResponse(
                students,
                studentPage.getNumber(),
                studentPage.getSize(),
                studentPage.getTotalElements(),
                studentPage.getTotalPages(),
                studentPage.isFirst(),
                studentPage.isLast()
        );
    }
}
