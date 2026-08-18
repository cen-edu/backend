package com.cenedu.backend.domain.member.dto.response;

import java.util.List;

/** CSV 일괄 등록으로 생성된 학생 목록과 처리 건수. */
public record StudentBulkCreateResponse(
        int totalCount,
        int createdCount,
        List<StudentCreateResponse> students
) {

    /** 생성된 전체 학생 목록으로 일괄 등록 결과를 만든다. */
    public static StudentBulkCreateResponse from(List<StudentCreateResponse> students) {
        return new StudentBulkCreateResponse(students.size(), students.size(), List.copyOf(students));
    }
}
