package com.cenedu.backend.domain.worksheet.repository.row;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/**
 * 맞춤 학습지 하나에 딸린 배정 한 줄. 맞춤 배정은 학생 한 명씩이라
 * ({@code worksheet_assignment}의 {@code class_id} XOR {@code student_id}) 같은 학습지가
 * 학생 수만큼 행으로 나온다. 화면 카드는 이걸 {@code worksheetId}로 묶은 것이다.
 *
 * @param sourceAssignmentId 묶음 키인 원본 배정. 여러 원본을 한 번에 읽을 때 행을 되가르는 축이다
 * @param rootWorksheetId    원본 배정의 학습지. 차수 파생(체인 깊이)의 시작점이다
 * @param parentWorksheetId  직전 차수의 학습지. 원본 학습지를 가리키면 1차다
 */
public record CustomLearningAssignmentRow(
        Long sourceAssignmentId,
        Long rootWorksheetId,
        Long worksheetId,
        Long parentWorksheetId,
        String title,
        WorksheetType type,
        Long assignmentId,
        Long studentId,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt
) {
}
