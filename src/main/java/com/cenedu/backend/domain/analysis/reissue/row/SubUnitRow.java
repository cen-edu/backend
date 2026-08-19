package com.cenedu.backend.domain.analysis.reissue.row;

/** 원본 배정이 다룬 소단원. 응답의 행 순서를 정한다. */
public record SubUnitRow(
        long subUnitId,
        String subUnitName
) {
}
