package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 맞춤 학습 카드 하나. <b>맞춤 학습지 단위</b>로 묶는다.
 *
 * <p>맞춤 배정은 학생 한 명씩이라({@code class_id} XOR {@code student_id}) 배정으로 묶으면
 * 카드마다 학생이 한 명뿐이다. 학습지 하나를 학생 넷에게 배정한 것이 화면의 "대상 4명" 카드이므로
 * 학습지로 묶어야 한다. 배정 ID는 {@code students[].assignmentId}에 있다.
 */
public record CustomLearningWorksheetResponse(

        Long worksheetId,
        String title,

        @Schema(description = "문항 형식. grading·score·totalUnits 규칙이 이 값으로 갈린다",
                allowableValues = {"practice", "assessment"})
        String type,

        @Schema(description = "차수. 저장 컬럼이 없어 parent_worksheet_id 체인의 깊이로 파생한다. "
                + "1부터 시작하고, 계보가 끊긴 데이터는 0이다. 배정일과 무관하므로 늦게 받은 학생의 "
                + "첫 맞춤도 1차다")
        int sessionNumber,

        @Schema(description = "가장 이른 배정일. 차수 파생에는 쓰지 않는다")
        OffsetDateTime assignedAt,

        @Schema(description = "가장 늦은 마감일. 한 명이라도 마감 전이면 카드는 열려 있는 것으로 본다. "
                + "학생 행의 상태 판정에는 이 값이 아니라 그 학생 배정의 마감을 쓴다")
        OffsetDateTime dueAt,

        int totalUnits,
        int studentCount,
        List<CustomLearningStudentResponse> students
) {

    public static CustomLearningWorksheetResponse of(
            Long worksheetId, String title, WorksheetType type, int sessionNumber,
            OffsetDateTime assignedAt, OffsetDateTime dueAt, int totalUnits,
            List<CustomLearningStudentResponse> students
    ) {
        return new CustomLearningWorksheetResponse(
                worksheetId,
                title,
                WorksheetResponseFormatter.toApiType(type),
                sessionNumber,
                assignedAt,
                dueAt,
                totalUnits,
                students.size(),
                List.copyOf(students));
    }
}
