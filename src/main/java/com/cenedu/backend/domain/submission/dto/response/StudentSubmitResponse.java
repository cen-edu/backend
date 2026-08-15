package com.cenedu.backend.domain.submission.dto.response;

import java.time.OffsetDateTime;

/**
 * 제출 확정 결과. {@code answeredUnits < totalUnits}면 프론트가 제출 전 확인 모달을 띄운다
 * (명세 7절) — 값은 서버가 준다.
 */
public record StudentSubmitResponse(String status, OffsetDateTime submittedAt, int answeredUnits, int totalUnits) {

    public static StudentSubmitResponse from(OffsetDateTime submittedAt, int answeredUnits, int totalUnits) {
        return new StudentSubmitResponse("submitted", submittedAt, answeredUnits, totalUnits);
    }
}
