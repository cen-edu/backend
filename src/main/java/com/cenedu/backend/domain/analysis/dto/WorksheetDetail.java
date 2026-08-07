package com.cenedu.backend.domain.analysis.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 취약점 분석 화면이 통째로 받는 worksheet 한 벌.
 *
 * <p>필드 이름은 화면이 읽는 그대로다. 화면과 helper 가 이 구조만 알기 때문에 이름을 바꾸면
 * 오류 없이 빈칸이 된다.
 */
public record WorksheetDetail(
        String id,
        String gradeId,
        String classId,
        String term,
        String type,
        String origin,
        String title,
        String className,
        LocalDate date,
        List<Concept> concepts,
        List<Question> questions,
        List<Student> students
) {

    public record Concept(String id, String label) {
    }

    public record Question(
            String id,
            int no,
            String unitId,
            String difficulty,
            String area,
            String prompt,
            String correctAnswer,
            int maxScore,
            String format,
            String grading,
            List<QuestionStep> steps
    ) {
    }

    public record QuestionStep(String id, int order, String conceptId, String label) {
    }

    public record Student(
            String id,
            String name,
            String status,
            String nextAction,
            List<Object> customSessions,
            List<Response> responses
    ) {
    }

    public record Response(
            int no,
            int score,
            int maxScore,
            boolean hintUsed,
            int seconds,
            String gradedBy,
            String studentAnswer,
            List<ResponseStep> steps
    ) {
    }

    /**
     * @param attempted 학생이 이 구간까지 도달했는지. {@code input} 이 비었는지로 대신 판단하면
     *                  안 된다. 빈칸을 안 푼 것으로 보고 모수에서 빼면 분모가 "얼마나 멀리
     *                  갔는가"가 되어, 앞에서 막힌 학생일수록 달성률이 높게 나온다.
     */
    public record ResponseStep(int order, boolean correct, String input, boolean attempted) {
    }
}
