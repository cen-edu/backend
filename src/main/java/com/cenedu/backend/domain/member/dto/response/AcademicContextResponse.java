package com.cenedu.backend.domain.member.dto.response;

import java.util.List;

/** 교사 화면에서 공통으로 사용하는 학년도·학년·반 계층과 독립 학기 옵션. */
public record AcademicContextResponse(
        List<AcademicYearOption> academicYears,
        List<SemesterOption> semesters,
        Defaults defaults
) {

    public AcademicContextResponse {
        academicYears = List.copyOf(academicYears);
        semesters = List.copyOf(semesters);
    }

    public record AcademicYearOption(
            int year,
            List<GradeOption> grades
    ) {

        public AcademicYearOption {
            grades = List.copyOf(grades);
        }
    }

    public record GradeOption(
            int grade,
            List<ClassOption> classes
    ) {

        public GradeOption {
            classes = List.copyOf(classes);
        }
    }

    public record ClassOption(
            Long id,
            String name,
            int displayOrder
    ) {
    }

    public record SemesterOption(
            int value,
            String label
    ) {
    }

    /** 학년·반·학기는 전체 선택을 유지하고 학년도만 화면의 숨은 조회 기준으로 제공한다. */
    public record Defaults(
            Integer academicYear,
            Integer grade,
            Long classId,
            Integer semester
    ) {
    }
}
