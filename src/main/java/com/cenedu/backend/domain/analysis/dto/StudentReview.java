package com.cenedu.backend.domain.analysis.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 학생이 회차를 마친 뒤 보는 정답 확인.
 *
 * <p>기록되지 않은 문항({@code submissionFailed})은 빼고 담는다. 그대로 두면 빈 답에 오답
 * 표시가 붙어 학생이 자기가 틀린 것으로 읽는다.
 */
public record StudentReview(
        String assessmentId,
        String assessmentTitle,
        LocalDate assessmentDate,
        String studentId,
        String studentName,
        int totalCount,
        int correctCount,
        List<ReviewItem> problems
) {

    public record ReviewItem(
            int problemNumber,
            String problemTitle,
            String problemText,
            List<String> choices,
            String responseType,
            String studentAnswer,
            String correctAnswer,
            boolean correct,
            boolean hintUsed,
            List<ReviewStep> steps
    ) {
    }

    /** 학생이 그 구간에 쓴 답과 맞는 답. 자기 답이므로 학생에게 돌려줘도 된다. */
    public record ReviewStep(String stepName, String studentAnswer, String correctAnswer) {
    }
}
