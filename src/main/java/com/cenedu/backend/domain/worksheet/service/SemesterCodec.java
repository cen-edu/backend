package com.cenedu.backend.domain.worksheet.service;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

/**
 * 학기 값의 API ↔ DB 역변환. {@code WorksheetResponseFormatter.toApiSemester}가 단방향이라
 * 요청 파라미터를 DB 값으로 되돌리는 쪽은 여기에 둔다.
 *
 * <p>변환기는 {@code dto.response} 패키지에 package-private으로 있어 서비스가 부를 수 없다.
 * 이 축은 조회 조건이라 서비스 단계에서 필요하므로 같은 패키지에 둔다 — 규칙을 두 곳에 복사하면
 * 학습지 목록과 학습 현황의 학기 필터가 갈린다.
 */
final class SemesterCodec {

    private SemesterCodec() {
    }

    static String toDbSemester(String semester) {
        return switch (semester) {
            case "first" -> "1";
            case "second" -> "2";
            case "common" -> "COMMON";
            default -> throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "semester 값이 올바르지 않습니다.");
        };
    }
}
