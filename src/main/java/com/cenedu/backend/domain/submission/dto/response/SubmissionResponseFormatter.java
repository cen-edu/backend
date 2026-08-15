package com.cenedu.backend.domain.submission.dto.response;

import java.time.OffsetDateTime;

import com.cenedu.backend.global.common.enums.AssignmentStatus;

/**
 * 저장·제출 응답 전용 값 변환. {@code domain/worksheet}의 {@code StudentResponseFormatter}와
 * 진행 상태 파생 규칙은 같지만(명세 2.4), 패키지가 달라 재사용할 수 없어(package-private) 그대로
 * 옮겨 둔다.
 */
final class SubmissionResponseFormatter {

    private SubmissionResponseFormatter() {
    }

    static String toApiStatus(AssignmentStatus status, short progressCount, OffsetDateTime dueAt) {
        return switch (status) {
            case SUBMITTED, GRADED -> "submitted";
            case NOT_SUBMITTED -> "not-submitted";
            case NOT_STARTED -> {
                if (dueAt.isBefore(OffsetDateTime.now())) {
                    yield "not-submitted";
                }
                yield progressCount > 0 ? "in-progress" : "not-started";
            }
        };
    }
}
