package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.domain.worksheet.entity.enums.SupportMode;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.enums.QuestionType;

/** DB 값 축을 API 상수 축으로 되돌리는 변환. 명세 2.3 매핑 표의 역방향이다. */
final class WorksheetResponseFormatter {

    private WorksheetResponseFormatter() {
    }

    static String toApiType(WorksheetType type) {
        return switch (type) {
            case GENERAL_LEARNING -> "practice";
            case COMPREHENSIVE_ASSESSMENT -> "assessment";
        };
    }

    static String toApiOrigin(WorksheetOrigin origin) {
        return switch (origin) {
            case STANDARD -> "manual";
            case CUSTOM -> "custom";
        };
    }

    static String toApiSemester(String semester) {
        return switch (semester) {
            case "1" -> "first";
            case "2" -> "second";
            case "COMMON" -> "common";
            default -> throw new IllegalStateException("알 수 없는 semester 값: " + semester);
        };
    }

    static String toApiQuestionType(QuestionType questionType) {
        return switch (questionType) {
            case MULTIPLE_CHOICE -> "choice";
            case SHORT_INPUT -> "short";
            case STEP_FILL -> "step";
            case ESSAY -> "essay";
        };
    }

    static String toApiDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> "low";
            case 2 -> "mid";
            case 3 -> "high";
            default -> throw new IllegalStateException("알 수 없는 difficulty 값: " + difficulty);
        };
    }

    static String toApiSupportMode(SupportMode supportMode) {
        if (supportMode == null) {
            return null;
        }
        return switch (supportMode) {
            case CONCEPT_GUIDE -> "concept";
            case CHATBOT -> "chat";
        };
    }

    static String toApiCustomStage(CustomStage customStage) {
        if (customStage == null) {
            return null;
        }
        return switch (customStage) {
            case REVIEW -> "retrace";
            case SIMILAR -> "basic";
            case ADVANCED -> "independent";
        };
    }
}
