package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * DB 값 축을 API 상수 축으로 되돌리는 변환(명세 2.3 · 11절).
 *
 * <p>worksheet 도메인에 같은 이름의 변환기가 있지만 package-private 이고, {@code origin} 매핑이
 * 서로 다르다 — 문제 보관함 API 는 {@code manual}, 채점 API 명세는 {@code standard} 다.
 */
public final class GradingResponseFormatter {

    private static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.ONE;

    private GradingResponseFormatter() {
    }

    public static String toApiType(WorksheetType type) {
        return switch (type) {
            case GENERAL_LEARNING -> "practice";
            case COMPREHENSIVE_ASSESSMENT -> "assessment";
        };
    }

    /** 명세 11절은 {@code standard}다. 문제 보관함 API의 {@code manual}과 값이 다르다. */
    public static String toApiOrigin(WorksheetOrigin origin) {
        return switch (origin) {
            case STANDARD -> "standard";
            case CUSTOM -> "custom";
        };
    }

    public static String toApiQuestionFormat(QuestionType questionType) {
        return switch (questionType) {
            case MULTIPLE_CHOICE -> "choice";
            case SHORT_INPUT -> "short";
            case STEP_FILL -> "step";
            case ESSAY -> "essay";
        };
    }

    public static String toApiDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> "low";
            case 2 -> "mid";
            case 3 -> "high";
            default -> throw new IllegalStateException("알 수 없는 difficulty 값: " + difficulty);
        };
    }

    /** 채점 주체(명세 2.3). 교사가 손댄 적이 없으면 자동채점값이다. */
    public static String toApiGradedBy(Long overriddenBy, GradingStatus gradingStatus) {
        if (gradingStatus != GradingStatus.GRADED) {
            return null;
        }
        return overriddenBy == null ? "auto" : "teacher";
    }

    /**
     * 배점. 일반·맞춤 학습은 {@code max_score}가 {@code NULL}이며 만점을 {@code 1.00}으로 본다
     * (명세 2.3).
     */
    public static BigDecimal resolveMaxScore(BigDecimal maxScore) {
        return maxScore != null ? maxScore : DEFAULT_MAX_SCORE;
    }

    /**
     * 칸 하나의 판정(명세 2.6). 교사에게는 {@code FAILED}를 접지 않지만 판정 코드 자체는
     * 아직 점수가 확정되지 않았다는 뜻의 {@code pending}이다 — 구분은 {@code gradingStatus}가 한다.
     */
    public static String classifyUnit(GradingStatus gradingStatus, BigDecimal finalScore,
                               boolean hasAnswer, BigDecimal maxScore) {
        if (gradingStatus != GradingStatus.GRADED) {
            return "pending";
        }
        if (!hasAnswer) {
            return "empty";
        }
        BigDecimal score = finalScore != null ? finalScore : BigDecimal.ZERO;
        if (score.compareTo(maxScore) >= 0) {
            return "correct";
        }
        if (score.compareTo(BigDecimal.ZERO) == 0) {
            return "wrong";
        }
        return "partial";
    }

    /** 문항 판정 = 칸 판정들의 접기(명세 2.6). */
    public static String aggregateItemResult(List<String> unitResults) {
        if (unitResults.isEmpty()) {
            return "pending";
        }
        if (unitResults.contains("pending")) {
            return "pending";
        }
        if (unitResults.stream().allMatch("empty"::equals)) {
            return "empty";
        }
        if (unitResults.stream().allMatch("correct"::equals)) {
            return "correct";
        }
        if (unitResults.stream().allMatch("wrong"::equals)) {
            return "wrong";
        }
        return "partial";
    }
}
