package com.cenedu.backend.domain.worksheet.repository.row;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/**
 * 맞춤 학습지 하나에 딸린 배정 한 줄. 맞춤 배정은 학생 한 명씩이라
 * ({@code worksheet_assignment}의 {@code class_id} XOR {@code student_id}) 같은 학습지가
 * 학생 수만큼 행으로 나온다. 화면 카드는 이걸 {@code worksheetId}로 묶은 것이다.
 */
public record CustomLearningAssignmentRow(
        Long worksheetId,
        String title,
        WorksheetType type,
        Long assignmentId,
        Long studentId,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt
) {
}
