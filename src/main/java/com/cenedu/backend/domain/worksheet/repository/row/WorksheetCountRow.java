package com.cenedu.backend.domain.worksheet.repository.row;

/** worksheet_id별 집계 개수 한 줄. problemCount·assignmentCount 조회에 공용으로 쓴다. */
public record WorksheetCountRow(Long worksheetId, long count) {
}
