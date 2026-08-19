package com.cenedu.backend.domain.analysis.report.pdf;

import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.CustomResolutionStatus;
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

    /** 지도 우선순위. 교사가 읽을 문서라 REVIEW 같은 코드를 그대로 두면 안 된다. */
    static String of(AnalysisStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case INSUFFICIENT_DATA -> "자료 부족";
            case INTENSIVE -> "집중 지도";
            case REVIEW -> "복습 권장";
            case STABLE -> "안정";
        };
    }

    /** 맞춤 학습으로 취약점이 풀렸는지. */
    static String of(CustomResolutionStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case RESOLVED -> "해결";
            case IN_PROGRESS -> "진행 중";
            case UNRESOLVED -> "미해결";
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
