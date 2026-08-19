package com.cenedu.backend.domain.analysis.report.pdf;

import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.global.common.enums.EvaluationArea;

/**
 * PDF 에 인쇄할 한국어 표기.
 *
 * <p>화면은 프론트가 라벨을 붙이지만 PDF 는 서버가 끝까지 만든다. enum 이름을 그대로 찍으면
 * 교사가 {@code PARTIAL_CORRECT} 를 읽게 된다.
 *
 * <p>프론트 {@code labels.js} 와 값이 어긋나면 같은 결과를 두 이름으로 부르게 되므로,
 * 문구를 고칠 때는 그쪽과 맞춘다(AGENTS.md 3절 3번).
 */
final class ReportLabels {

    private ReportLabels() {
    }

    static String of(EvaluationArea area) {
        if (area == null) {
            return null;
        }
        return switch (area) {
            case UNDERSTANDING -> "이해";
            case CALCULATION -> "계산";
            case REASONING -> "추론";
            case PROBLEM_SOLVING -> "문제해결";
        };
    }

    static String of(DifficultyBand band) {
        if (band == null) {
            return "-";
        }
        return switch (band) {
            case LOW -> "하";
            case MID -> "중";
            case HIGH -> "상";
        };
    }

    static String of(AssessmentQuestionTypeGroup group) {
        if (group == null) {
            return "-";
        }
        return switch (group) {
            case MULTIPLE_CHOICE -> "객관식";
            case SHORT_ANSWER -> "주관식";
            case ESSAY -> "서술형";
        };
    }

    static String of(StudentItemResultType resultType) {
        if (resultType == null) {
            return "-";
        }
        return switch (resultType) {
            case CORRECT -> "정답";
            case PARTIAL_CORRECT -> "부분정답";
            case INCORRECT -> "오답";
            case NOT_GRADED -> "미채점";
        };
    }
}
