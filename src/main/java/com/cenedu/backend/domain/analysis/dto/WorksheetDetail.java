package com.cenedu.backend.domain.analysis.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

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
            @Schema(description = "stable / review / priority / insufficient. insufficient 는 낸 "
                    + "문항을 다 풀지 않은 학생이며 학급 평균과 취약 판정에서 제외해야 한다",
                    example = "priority")
            String status,
            String nextAction,
            List<Object> customSessions,
            List<Response> responses
    ) {
    }

    public record Response(
            int no,
            int score,
            @Schema(description = "항상 1. 백엔드는 배점을 두지 않고 정오답만 기록한다")
            int maxScore,
            boolean hintUsed,
            @Schema(description = "항상 0. 백엔드가 문항별 소요 시간을 기록하지 않는다")
            int seconds,
            @Schema(description = "채점 주체. null 이면 채점 대기이며 지표에서 제외한다")
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
    public record ResponseStep(
            int order,
            boolean correct,
            @Schema(description = "학생이 이 구간에 쓴 답. 비어 있을 수 있다")
            String input,
            @Schema(description = "이 구간까지 도달했는지. 개념별 성취의 분모는 이 값으로 센다. "
                    + "input 이 비었는지로 대신 판단하면 앞에서 막힌 학생의 달성률이 부풀려진다")
            boolean attempted) {
    }
}
