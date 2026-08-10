package com.cenedu.backend.domain.analysis.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 화면용 집계 전용 리포지토리.
 *
 * <p>집계는 네이티브 쿼리로 둔다. {@code count(*) FILTER (WHERE ...)} 를 JPQL 로 옮기면 같은
 * 결과를 내려고 조회를 여러 번 나누게 되고, 그러면 화면 하나에 필요한 왕복이 늘어난다.
 *
 * <p>백분율은 SQL 이 아니라 Java 에서 반올림한다. 프로토타입과 같은 값이 나와야 화면 숫자가
 * 이관 전후로 흔들리지 않는다.
 */
public interface AnalysisAggregateRepository extends JpaRepository<AnalysisAttempt, Long> {

    interface AssessmentListRow {
        String getAssessmentId();
        String getAssessmentTitle();
        LocalDate getAssessmentDate();
        String getAssessmentType();
        boolean getIsSimulation();
        int getStudentCount();
        int getProblemCount();
        int getAttemptCount();
        int getCorrectCount();
        int getLowCount();
        int getMediumCount();
        int getHighCount();
    }

    interface AssessmentInfoRow {
        String getAssessmentTitle();
        LocalDate getAssessmentDate();
        String getAssessmentType();
        boolean getIsSimulation();
    }

    interface StudentAggregateRow {
        String getStudentId();
        String getStudentName();
        int getTotalCount();
        int getCorrectCount();
        int getHintCount();
    }

    interface ProblemAggregateRow {
        int getProblemNumber();
        String getProblemId();
        String getProblemTitle();
        String getEvaluationArea();
        String getTopic();
        String getSourceDataset();
        String getDifficultyBand();
        BigDecimal getReferenceSuccessRate();
        int getTotalCount();
        int getCorrectCount();
    }

    interface AreaAggregateRow {
        String getEvaluationArea();
        int getProblemCount();
        int getTotalCount();
        int getCorrectCount();
    }

    interface StudentAreaAggregateRow {
        String getEvaluationArea();
        int getStudentTotal();
        int getStudentCorrect();
        int getClassTotal();
        int getClassCorrect();
    }

    interface DifficultyAggregateRow {
        String getDifficultyBand();
        int getProblemCount();
        int getTotalCount();
        int getCorrectCount();
    }

    interface StudentAttemptRow {
        int getProblemNumber();
        String getProblemId();
        String getProblemTitle();
        String getProblemText();
        String getEvaluationArea();
        boolean getIsCorrect();
        boolean getHintUsed();
        BigDecimal getReferenceSuccessRate();
        String getDifficultyBand();
        String getSourceDataset();
        String getSourceDifficulty();
        String getDifficultyBasis();
        int getClassTotal();
        int getClassCorrect();
    }

    @Query(value = """
            SELECT s.assessment_id            AS assessmentId,
                   min(s.assessment_title)    AS assessmentTitle,
                   min(s.assessment_date)     AS assessmentDate,
                   min(s.assessment_type)     AS assessmentType,
                   bool_and(s.is_simulation)  AS isSimulation,
                   count(DISTINCT s.student_id)      AS studentCount,
                   count(DISTINCT a.problem_number)  AS problemCount,
                   count(DISTINCT a.problem_number) FILTER (WHERE a.difficulty_band = 'low')  AS lowCount,
                   count(DISTINCT a.problem_number) FILTER (WHERE a.difficulty_band = 'mid')  AS mediumCount,
                   count(DISTINCT a.problem_number) FILTER (WHERE a.difficulty_band = 'high') AS highCount,
                   count(a.event_id)          AS attemptCount,
                   count(a.event_id) FILTER (WHERE a.is_correct) AS correctCount
              FROM analysis_assessment s
              LEFT JOIN analysis_attempt a
                ON a.assessment_id = s.assessment_id AND a.student_id = s.student_id
             GROUP BY s.assessment_id
             ORDER BY min(s.assessment_date) DESC, min(s.assessment_title)
            """, nativeQuery = true)
    List<AssessmentListRow> findAssessmentList();

    /**
     * 회차 머리말. 같은 회차를 푼 학생들이 서로 다른 제목을 들고 있으면 두 줄이 나온다.
     * 그 경우를 서비스가 잡아 실패시킨다.
     */
    @Query(value = """
            SELECT assessment_title AS assessmentTitle,
                   assessment_date  AS assessmentDate,
                   assessment_type  AS assessmentType,
                   is_simulation    AS isSimulation
              FROM analysis_assessment
             WHERE assessment_id = :assessmentId
             GROUP BY assessment_title, assessment_date, assessment_type, is_simulation
            """, nativeQuery = true)
    List<AssessmentInfoRow> findAssessmentInfo(@Param("assessmentId") String assessmentId);

    @Query(value = """
            SELECT s.student_id   AS studentId,
                   s.student_name AS studentName,
                   count(*)       AS totalCount,
                   count(*) FILTER (WHERE a.is_correct) AS correctCount,
                   count(*) FILTER (WHERE a.hint_used)  AS hintCount
              FROM analysis_assessment s
              JOIN analysis_attempt a
                ON a.assessment_id = s.assessment_id AND a.student_id = s.student_id
             WHERE s.assessment_id = :assessmentId
             GROUP BY s.student_id, s.student_name
             ORDER BY count(*) FILTER (WHERE a.is_correct) DESC,
                      count(*) FILTER (WHERE a.hint_used),
                      s.student_name
            """, nativeQuery = true)
    List<StudentAggregateRow> findStudentAggregates(@Param("assessmentId") String assessmentId);

    @Query(value = """
            SELECT problem_number                  AS problemNumber,
                   problem_id                      AS problemId,
                   min(problem_title)              AS problemTitle,
                   min(evaluation_area)            AS evaluationArea,
                   min(topic)                      AS topic,
                   min(source_dataset)             AS sourceDataset,
                   min(difficulty_band)            AS difficultyBand,
                   min(reference_success_rate)     AS referenceSuccessRate,
                   count(*)                        AS totalCount,
                   count(*) FILTER (WHERE is_correct) AS correctCount
              FROM analysis_attempt
             WHERE assessment_id = :assessmentId
             GROUP BY problem_number, problem_id
             ORDER BY problem_number
            """, nativeQuery = true)
    List<ProblemAggregateRow> findProblemAggregates(@Param("assessmentId") String assessmentId);

    @Query(value = """
            SELECT evaluation_area AS evaluationArea,
                   count(DISTINCT problem_number) AS problemCount,
                   count(*)                       AS totalCount,
                   count(*) FILTER (WHERE is_correct) AS correctCount
              FROM analysis_attempt
             WHERE assessment_id = :assessmentId
             GROUP BY evaluation_area
             ORDER BY CASE evaluation_area WHEN 'concept' THEN 1 WHEN 'calculation' THEN 2
                      WHEN 'reasoning' THEN 3 WHEN 'problemSolving' THEN 4 ELSE 5 END
            """, nativeQuery = true)
    List<AreaAggregateRow> findClassAreaAggregates(@Param("assessmentId") String assessmentId);

    @Query(value = """
            SELECT evaluation_area AS evaluationArea,
                   count(*) FILTER (WHERE student_id = :studentId) AS studentTotal,
                   count(*) FILTER (WHERE student_id = :studentId AND is_correct) AS studentCorrect,
                   count(*) AS classTotal,
                   count(*) FILTER (WHERE is_correct) AS classCorrect
              FROM analysis_attempt
             WHERE assessment_id = :assessmentId
             GROUP BY evaluation_area
             ORDER BY CASE evaluation_area WHEN 'concept' THEN 1 WHEN 'calculation' THEN 2
                      WHEN 'reasoning' THEN 3 WHEN 'problemSolving' THEN 4 ELSE 5 END
            """, nativeQuery = true)
    List<StudentAreaAggregateRow> findStudentAreaAggregates(
            @Param("assessmentId") String assessmentId, @Param("studentId") String studentId);

    /**
     * 난이도 집계. {@code studentId} 가 null 이면 학급 전체다.
     *
     * <p>edu-sen 은 문자열을 이어 붙여 WHERE 절을 만들었는데, 여기서는 널 비교 한 줄로 둔다.
     * 조건을 문자열로 조립하면 쿼리가 두 벌이 되고 한쪽만 고치는 일이 생긴다.
     */
    @Query(value = """
            SELECT difficulty_band AS difficultyBand,
                   count(DISTINCT problem_number) AS problemCount,
                   count(*)                       AS totalCount,
                   count(*) FILTER (WHERE is_correct) AS correctCount
              FROM analysis_attempt
             WHERE assessment_id = :assessmentId
               AND (CAST(:studentId AS text) IS NULL OR student_id = :studentId)
             GROUP BY difficulty_band
             ORDER BY CASE difficulty_band WHEN 'low' THEN 1 WHEN 'mid' THEN 2
                      WHEN 'high' THEN 3 ELSE 4 END
            """, nativeQuery = true)
    List<DifficultyAggregateRow> findDifficultyAggregates(
            @Param("assessmentId") String assessmentId, @Param("studentId") String studentId);

    @Query(value = """
            SELECT a.problem_number         AS problemNumber,
                   a.problem_id             AS problemId,
                   a.problem_title          AS problemTitle,
                   a.problem_text           AS problemText,
                   a.evaluation_area        AS evaluationArea,
                   a.is_correct             AS isCorrect,
                   a.hint_used              AS hintUsed,
                   a.reference_success_rate AS referenceSuccessRate,
                   a.difficulty_band        AS difficultyBand,
                   a.source_dataset         AS sourceDataset,
                   a.source_difficulty      AS sourceDifficulty,
                   a.difficulty_basis       AS difficultyBasis,
                   p.class_total            AS classTotal,
                   p.class_correct          AS classCorrect
              FROM analysis_attempt a
              JOIN (
                   SELECT problem_number, problem_id, count(*) AS class_total,
                          count(*) FILTER (WHERE is_correct) AS class_correct
                     FROM analysis_attempt
                    WHERE assessment_id = :assessmentId
                    GROUP BY problem_number, problem_id
              ) p ON p.problem_number = a.problem_number AND p.problem_id = a.problem_id
             WHERE a.assessment_id = :assessmentId AND a.student_id = :studentId
             ORDER BY a.problem_number
            """, nativeQuery = true)
    List<StudentAttemptRow> findStudentAttempts(
            @Param("assessmentId") String assessmentId, @Param("studentId") String studentId);
}
