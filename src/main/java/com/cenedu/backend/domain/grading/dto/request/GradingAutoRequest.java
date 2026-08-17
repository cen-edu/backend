package com.cenedu.backend.domain.grading.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 자동채점 실행 대상(명세 7절). 화면의 {@code aiSelections}({@code 학생 → 문항번호 목록}) 구조를
 * 그대로 받는다.
 *
 * @param targets 비어 있거나 {@code null}이면 <b>제출한 전원의 전 문항</b>이 대상이다
 */
public record GradingAutoRequest(

        @Schema(description = "채점할 학생과 문항. 생략하면 제출한 전원의 전 문항")
        @Valid
        List<Target> targets
) {

    /**
     * @param worksheetItemIds 비어 있거나 {@code null}이면 그 학생의 <b>전 문항</b>이 대상이다.
     *                         문항을 고르면 그 문항의 전 칸이 대상이 된다 — 화면이 칸 단위 선택을
     *                         제공하지 않는다
     */
    public record Target(

            @NotNull(message = "학생 배정 ID는 필수입니다.")
            Long assignmentStudentId,

            @Schema(description = "채점할 문항. 생략하면 그 학생의 전 문항")
            List<Long> worksheetItemIds
    ) {
    }

    /** {@code null}과 빈 배열을 같은 뜻으로 다룬다 — 프론트가 둘 다 보낸다. */
    public List<Target> resolvedTargets() {
        return targets == null ? List.of() : targets;
    }
}
