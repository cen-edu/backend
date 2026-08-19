package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 맞춤 학습 한 차수. 학생 한 명의 맞춤 배정 하나에 대응한다.
 *
 * <p>필드 집합은 학습 현황의 맞춤 학생 행과 같게 맞춘다 — 한쪽만 진행률·채점 상태를 주면 같은
 * 학생이 두 화면에서 다르게 보인다.
 *
 * <p><b>문항별 정오는 여기 없다.</b> 점수표 API({@code GET /api/teacher/grading/{assignmentId}})가
 * 준다. 목록에 담으면 학생 수 × 차수 × 문항 수만큼 셀을 원본마다 계산하게 된다. 이 행의
 * {@code assignmentId}를 그대로 그 API 경로에 넣으면 된다.
 */
public record GradingCustomSessionResponse(

        @Schema(description = "차수. 저장 컬럼이 없어 parent_worksheet_id 체인의 깊이로 파생한다. "
                + "1부터 시작하고 계보가 끊긴 데이터는 0이다. 배정일과 무관하다")
        int sessionNumber,

        Long worksheetId,

        @Schema(description = "직전 차수의 학습지. 1차는 원본 학습지를 가리킨다", nullable = true)
        Long parentWorksheetId,

        @Schema(description = "맞춤 배정 ID. 점수표·자동채점·확정 API 의 경로 변수다")
        Long assignmentId,

        @Schema(description = "학생 채점 상세 링크용. .../grading/{assignmentId}/students/{이 값}")
        Long assignmentStudentId,

        String title,

        @Schema(allowableValues = {"practice", "assessment"})
        String type,

        @Schema(description = "채점 축. 원본 행과 같은 규칙으로 판정한다. 맞춤은 학생이 한 명이라 "
                + "미제출과 제출·미채점이 둘 다 grading 이며, 구분은 studentStatus 가 한다",
                allowableValues = {"grading", "graded", "confirmed"})
        String status,

        @Schema(description = "진행 축",
                allowableValues = {"not-started", "in-progress", "submitted", "not-submitted"})
        String studentStatus,

        @Schema(description = "푼 칸 수. 진행률 분자이며 totalUnits 와 축이 같다")
        short doneUnits,

        @Schema(description = "진행률 분모. 종합평가는 문항 수, 일반 학습은 채점 칸 수 합")
        int totalUnits,

        @Schema(description = "채점 상태. 일반 학습은 항상 null",
                allowableValues = {"pending", "done"}, nullable = true)
        String grading,

        @Schema(description = "확정 후 정정됨")
        boolean modified,

        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,

        @Schema(nullable = true)
        OffsetDateTime submittedAt,

        @Schema(nullable = true)
        OffsetDateTime gradedAt,

        @Schema(description = "총점. 일반 학습은 항상 null", nullable = true)
        BigDecimal score
) {
}
