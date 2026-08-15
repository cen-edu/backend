package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * 학생 API 전용 값 변환. 명세 2.4절 매핑표를 따른다.
 *
 * <p>{@link WorksheetResponseFormatter}와 일부 축(문항 형식·난이도·지원 방식·맞춤 단계)은 매핑이
 * 같아 그대로 재사용한다. {@code origin}은 교사용("manual")과 학생용("standard") 값이 달라
 * 재사용하지 않는다. {@code semester}는 변환하지 않고 원값을 그대로 내려보낸다.
 */
final class StudentResponseFormatter {

    private StudentResponseFormatter() {
    }

    /**
     * 진행 상태 파생(명세 2.4). {@code NOT_STARTED}인데 마감이 지났으면 진행률과 무관하게
     * {@code not-submitted}로 접는다 — DB에는 {@code NOT_SUBMITTED}로 확정되기 전이라 매 조회마다
     * 시각을 비교해 파생해야 한다.
     */
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

    static String toApiCategory(WorksheetType type) {
        return switch (type) {
            case GENERAL_LEARNING -> "homework";
            case COMPREHENSIVE_ASSESSMENT -> "assessment";
        };
    }

    /** 교사용 {@code toApiOrigin}과 값이 다르다(학생: standard/custom, 교사: manual/custom). */
    static String toApiOrigin(WorksheetOrigin origin) {
        return switch (origin) {
            case STANDARD -> "standard";
            case CUSTOM -> "custom";
        };
    }

    /**
     * 문항 이미지 URL 조립. 경로가 아직 확정 전이다(명세 10-①). 규칙을 여기 한 곳에만 둬서
     * 경로가 바뀌면 이 메서드만 고치면 되게 한다.
     */
    static String assembleImageUrl(long questionId, String assetKey) {
        return "/api/images/problems/%d/assets/%s".formatted(questionId, assetKey);
    }

    /**
     * 답안 칸의 입력 방식. {@code submission_answer}가 아니라 {@code problem_question.question_type}
     * 으로 결정한다(6-3절 저장 규칙과 같은 축) — 아직 답이 없는 칸도 렌더링해야 하기 때문이다.
     */
    static String toInputMode(QuestionType questionType) {
        return switch (questionType) {
            case MULTIPLE_CHOICE -> "CHOICE";
            case ESSAY -> "IMAGE";
            case SHORT_INPUT, STEP_FILL -> "HANDWRITING";
        };
    }
}
