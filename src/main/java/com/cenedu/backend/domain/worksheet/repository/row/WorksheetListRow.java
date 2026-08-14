package com.cenedu.backend.domain.worksheet.repository.row;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 목록 필터를 적용한 학습지 본문 한 줄. sourceWorksheetId는 출처 학습지 자기 조인 결과다. */
public record WorksheetListRow(
        Long id,
        String title,
        WorksheetType type,
        WorksheetOrigin origin,
        short grade,
        String semester,
        Short totalScore,
        OffsetDateTime createdAt,
        Long sourceWorksheetId
) {
}
