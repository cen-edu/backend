package com.cenedu.backend.domain.analysis.reissue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.analysis.reissue.row.DiagnosticStageEvidenceRow;
import com.cenedu.backend.domain.analysis.reissue.row.EvaluationAreaEvidenceRow;
import com.cenedu.backend.domain.analysis.reissue.row.IncorrectQuestionRow;
import com.cenedu.backend.domain.analysis.reissue.row.LatestSimilarResultRow;
import com.cenedu.backend.domain.analysis.reissue.row.PlacementTallyRow;
import com.cenedu.backend.domain.analysis.reissue.row.QuestionOwnershipRow;
import com.cenedu.backend.domain.analysis.reissue.row.SubUnitRow;
import com.cenedu.backend.domain.analysis.reissue.row.SubUnitWeaknessRow;
import com.cenedu.backend.global.common.enums.EvaluationArea;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 원본 배정에서 파생된 맞춤 회차 사슬을 따라 재출제 근거를 읽는 조회 Repository.
 *
 * <p>계보는 {@code worksheet.parent_worksheet_id} 로 잇는다. 같은 묶음의
 * {@code source_assignment_id} 는 <b>모든 차수가 원본 배정을 가리키므로</b> 계보로 쓸 수 없다 —
 * 그것으로 재귀하면 2차·3차가 전부 1차와 같은 깊이로 붙어 "직전 회차"를 집어낼 수 없다.
 * 묶음 조건으로만 함께 건다.
 *
 * <p>누적 취약 분포는 사슬 전체를 더한 값이라 끝까지 따라간다. 반면 <b>난이도 조절은 직전 회차
 * 하나만</b> 본다({@link #findLatestSimilarResults}).
 *
 * <p>재귀는 학습지 계보({@code lineage})만 훑고, 학생 배정 매핑은 {@code chain} 에서 한 번만
 * 한다. 같은 학습지가 반 단위와 학생 단위로 각각 배정돼 있을 수 있어 학습지마다 한 행으로
 * 좁히지 않으면 문항이 두 번 집계된다.
 */
@Repository
@RequiredArgsConstructor
public class ReissueProposalRepository {

    /**
     * 참조 대상 배정이 먼저 존재해야 새 학습지를 만들 수 있어 순환은 구조적으로 생기지 않는다.
     * 그래도 데이터가 어긋났을 때 쿼리가 멈추지 않는 일이 없도록 깊이를 제한한다.
     */
    private static final int MAX_CHAIN_DEPTH = 20;

    /** 응용 문항은 평가에 반영하지 않으므로 취약점 집계에서 뺀다. 중복 출제 방지에는 그대로 쓴다. */
    private static final String EXCLUDE_ADVANCED =
            "(custom_stage IS NULL OR custom_stage <> 'ADVANCED')";

    /** 모든 채점 칸이 채점된 문항만 집계에 넣는다. 미채점을 오답으로 세면 안 된다. */
    private static final String FULLY_GRADED = "graded_unit_count = expected_unit_count";

    /** 원본 배정과 그로부터 파생된 맞춤 회차 전체. 뒤따르는 모든 쿼리의 공통 기반이다. */
    private static final String CHAIN_CTE = """
            WITH RECURSIVE lineage AS (
                SELECT root.worksheet_id, 0 AS depth
                FROM worksheet_assignment root
                WHERE root.id = :assignmentId

                UNION ALL

                SELECT derived.id, lineage.depth + 1
                FROM lineage
                JOIN worksheet derived
                  ON derived.parent_worksheet_id = lineage.worksheet_id
                 AND derived.origin = 'CUSTOM'
                 AND derived.deleted_at IS NULL
                 AND derived.source_assignment_id = :assignmentId
                WHERE lineage.depth < %d
            ),
            chain AS (
                SELECT DISTINCT ON (lineage.worksheet_id)
                       lineage.worksheet_id,
                       lineage.depth,
                       assignment.id AS assignment_id,
                       student.id AS assignment_student_id,
                       assignment.assigned_at,
                       student.graded_at
                FROM lineage
                JOIN worksheet_assignment assignment
                  ON assignment.worksheet_id = lineage.worksheet_id
                JOIN worksheet_assignment_student student
                  ON student.assignment_id = assignment.id
                 AND student.student_id = :studentId
                ORDER BY lineage.worksheet_id, student.id
            ),
            item_result AS (
                SELECT chain.assignment_id,
                       chain.depth,
                       chain.assigned_at,
                       chain.graded_at,
                       item.custom_stage,
                       item.question_id,
                       question.sub_unit_id,
                       question.difficulty,
                       question.evaluation_area,
                       question.question_type,
                       COUNT(answer_unit.id) AS expected_unit_count,
                       COUNT(answer.id) FILTER (
                           WHERE answer.grading_status = 'GRADED'
                       ) AS graded_unit_count,
                       CASE
                           WHEN COUNT(answer_unit.id) > 0
                            AND COUNT(answer.id) FILTER (
                                WHERE answer.grading_status = 'GRADED'
                            ) = COUNT(answer_unit.id)
                            AND CASE
                                WHEN item.max_score IS NOT NULL
                                    THEN COALESCE(SUM(answer.final_score), 0) = item.max_score
                                ELSE BOOL_AND(COALESCE(answer.final_score, 0) = 1)
                            END
                           THEN TRUE
                           ELSE FALSE
                       END AS is_correct
                FROM chain
                JOIN worksheet_item item
                  ON item.worksheet_id = chain.worksheet_id
                JOIN problem_question question
                  ON question.id = item.question_id
                 AND question.deleted_at IS NULL
                JOIN problem_answer_unit answer_unit
                  ON answer_unit.question_id = question.id
                LEFT JOIN submission_answer answer
                  ON answer.assignment_student_id = chain.assignment_student_id
                 AND answer.answer_unit_id = answer_unit.id
                GROUP BY chain.assignment_id, chain.depth, chain.assigned_at, chain.graded_at,
                         item.id, item.custom_stage, item.question_id, question.sub_unit_id,
                         question.difficulty, question.evaluation_area, question.question_type,
                         item.max_score
            )
            """.formatted(MAX_CHAIN_DEPTH);

    private final JdbcClient jdbcClient;

    /** 원본 배정에 대한 학생의 채점 상태를 반환한다. 배정받지 않았으면 비어 있다. */
    public Optional<String> findRootAssignmentStatus(long assignmentId, long studentId) {
        return jdbcClient.sql("""
                        SELECT status
                        FROM worksheet_assignment_student
                        WHERE assignment_id = :assignmentId
                          AND student_id = :studentId
                        """)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query(String.class)
                .optional();
    }

    /** 원본에서 파생된 맞춤 회차 수를 반환한다. 원본 자신은 세지 않는다. */
    public int countCustomSessions(long assignmentId, long studentId) {
        return query("""
                SELECT COUNT(*) AS session_count
                FROM chain
                WHERE depth > 0
                """, assignmentId, studentId,
                (rs, rowNum) -> rs.getInt("session_count")).getFirst();
    }

    /** 원본 배정이 다룬 소단원을 교육과정 순서대로 반환한다. 응답의 행이 된다. */
    public List<SubUnitRow> findSubUnits(long assignmentId, long studentId) {
        return query("""
                SELECT DISTINCT item_result.sub_unit_id, unit.name, unit.display_order
                FROM item_result
                JOIN curriculum_unit unit ON unit.id = item_result.sub_unit_id
                WHERE item_result.depth = 0
                ORDER BY unit.display_order, item_result.sub_unit_id
                """, assignmentId, studentId,
                (rs, rowNum) -> new SubUnitRow(rs.getLong("sub_unit_id"), rs.getString("name")));
    }

    /** 원본 배정의 소단원 × 난이도별 채점 완료 수와 완전 정답 수를 반환한다. 영점 조절의 입력이다. */
    public List<PlacementTallyRow> findPlacementTallies(long assignmentId, long studentId) {
        return query("""
                SELECT sub_unit_id,
                       difficulty,
                       COUNT(*) FILTER (WHERE %s) AS graded_count,
                       COUNT(*) FILTER (WHERE is_correct) AS correct_count
                FROM item_result
                WHERE depth = 0
                GROUP BY sub_unit_id, difficulty
                """.formatted(FULLY_GRADED), assignmentId, studentId,
                (rs, rowNum) -> new PlacementTallyRow(
                        rs.getLong("sub_unit_id"),
                        rs.getShort("difficulty"),
                        rs.getInt("graded_count"),
                        rs.getInt("correct_count")));
    }

    /**
     * 가장 최근 맞춤 회차의 소단원 × 난이도별 유사 문항 결과를 반환한다.
     *
     * <p>난이도 조절은 이 한 회차만 본다. 맞춤 회차가 아직 없으면 비어 있고, 그때는 원본 배정의
     * 영점 조절이 난이도를 정한다.
     *
     * <p>재고가 모자라 한 소단원의 유사 문항에 난이도가 섞였을 수 있어 난이도별로 쪼개 돌려준다.
     * 대표 난이도 선택은 서비스가 한다.
     */
    public List<LatestSimilarResultRow> findLatestSimilarResults(long assignmentId, long studentId) {
        return query("""
                , latest_session AS (
                    SELECT MAX(depth) AS depth FROM item_result WHERE depth > 0
                )
                SELECT item_result.sub_unit_id,
                       item_result.difficulty,
                       COUNT(*) FILTER (WHERE %s) AS graded_count,
                       COUNT(*) FILTER (WHERE item_result.is_correct) AS correct_count
                FROM item_result
                JOIN latest_session ON latest_session.depth = item_result.depth
                WHERE item_result.custom_stage = 'SIMILAR'
                GROUP BY item_result.sub_unit_id, item_result.difficulty
                """.formatted(FULLY_GRADED), assignmentId, studentId,
                (rs, rowNum) -> new LatestSimilarResultRow(
                        rs.getLong("sub_unit_id"),
                        rs.getShort("difficulty"),
                        rs.getInt("graded_count"),
                        rs.getInt("correct_count")));
    }

    /**
     * 사슬 전체에서 틀린 문항을 최근 순으로 반환한다.
     *
     * <p>{@code reissuable} 은 그 문항을 동일 문항으로 다시 낼 수 있는지다. 맞춤 학습지는
     * {@code GENERAL_LEARNING} 이라 전 문항이 {@code STEP_FILL} 이어야 저장이 통과한다. 종합평가에서
     * 틀린 객관식을 후보로 내보내면 학습지 저장 단계에서 막힌다.
     */
    public List<IncorrectQuestionRow> findIncorrectQuestions(long assignmentId, long studentId) {
        return query("""
                SELECT sub_unit_id,
                       question_id,
                       BOOL_OR(question_type = 'STEP_FILL') AS reissuable,
                       COUNT(*) AS incorrect_count,
                       MAX(COALESCE(graded_at, assigned_at)) AS last_incorrect_at
                FROM item_result
                WHERE %s AND %s AND NOT is_correct
                GROUP BY sub_unit_id, question_id
                ORDER BY last_incorrect_at DESC, incorrect_count DESC, question_id
                """.formatted(EXCLUDE_ADVANCED, FULLY_GRADED), assignmentId, studentId,
                (rs, rowNum) -> new IncorrectQuestionRow(
                        rs.getLong("sub_unit_id"),
                        rs.getLong("question_id"),
                        rs.getBoolean("reissuable"),
                        rs.getInt("incorrect_count"),
                        rs.getObject("last_incorrect_at", OffsetDateTime.class)));
    }

    /**
     * 사슬 전체에서 학생이 이미 받은 문항을 반환한다. 중복 출제 방지용이다.
     *
     * <p>응용 문항도 포함한다. 취약점 집계에서는 빼지만 같은 문항을 다시 내면 안 되는 것은 같다.
     */
    public List<QuestionOwnershipRow> findAnsweredQuestions(long assignmentId, long studentId) {
        return query("""
                SELECT DISTINCT sub_unit_id, question_id
                FROM item_result
                ORDER BY sub_unit_id, question_id
                """, assignmentId, studentId,
                (rs, rowNum) -> new QuestionOwnershipRow(
                        rs.getLong("sub_unit_id"), rs.getLong("question_id")));
    }

    /**
     * 소단원별 누적 오답 규모를 반환한다.
     *
     * <p>{@code historicalIncorrectItemCount} 는 평가 영역이 없는 문항의 오답까지 센다.
     * {@code problem_question.evaluation_area} 가 nullable 이라 평가 영역 분포의 합만으로는
     * 실제 오답 규모를 알 수 없다.
     */
    public List<SubUnitWeaknessRow> findSubUnitWeakness(long assignmentId, long studentId) {
        return query("""
                SELECT sub_unit_id,
                       COUNT(*) FILTER (
                           WHERE %s AND NOT is_correct
                       ) AS historical_incorrect_item_count,
                       COUNT(DISTINCT assignment_id) FILTER (
                           WHERE %s AND NOT is_correct
                       ) AS incorrect_session_count
                FROM item_result
                WHERE %s
                GROUP BY sub_unit_id
                """.formatted(FULLY_GRADED, FULLY_GRADED, EXCLUDE_ADVANCED),
                assignmentId, studentId,
                (rs, rowNum) -> new SubUnitWeaknessRow(
                        rs.getLong("sub_unit_id"),
                        rs.getInt("historical_incorrect_item_count"),
                        rs.getInt("incorrect_session_count")));
    }

    /** 소단원 × 평가 영역의 문항 단위 채점·오답 분포를 반환한다. 영역이 없는 문항은 빠진다. */
    public List<EvaluationAreaEvidenceRow> findEvaluationAreaEvidence(
            long assignmentId, long studentId
    ) {
        return query("""
                SELECT sub_unit_id,
                       evaluation_area,
                       COUNT(*) FILTER (WHERE %s) AS graded_item_count,
                       COUNT(*) FILTER (WHERE %s AND NOT is_correct) AS incorrect_item_count
                FROM item_result
                WHERE %s AND evaluation_area IS NOT NULL
                GROUP BY sub_unit_id, evaluation_area
                """.formatted(FULLY_GRADED, FULLY_GRADED, EXCLUDE_ADVANCED),
                assignmentId, studentId,
                (rs, rowNum) -> new EvaluationAreaEvidenceRow(
                        rs.getLong("sub_unit_id"),
                        EvaluationArea.valueOf(rs.getString("evaluation_area")),
                        rs.getInt("graded_item_count"),
                        rs.getInt("incorrect_item_count")));
    }

    /**
     * 소단원 × 풀이 단계의 답안 단위 채점·오답 분포를 반환한다.
     *
     * <p>평가 영역은 문항 단위이고 이쪽은 답안 단위라 두 분포의 합계는 서로 다르다. 부분 점수가
     * 붙은 칸은 오답으로 세지 않는다.
     */
    public List<DiagnosticStageEvidenceRow> findDiagnosticStageEvidence(
            long assignmentId, long studentId
    ) {
        return query("""
                SELECT question.sub_unit_id,
                       answer_unit.diagnostic_type,
                       COUNT(*) FILTER (
                           WHERE answer.grading_status = 'GRADED'
                       ) AS graded_unit_count,
                       COUNT(*) FILTER (
                           WHERE answer.grading_status = 'GRADED'
                             AND COALESCE(answer.final_score, 0) = 0
                       ) AS incorrect_unit_count
                FROM chain
                JOIN worksheet_item item
                  ON item.worksheet_id = chain.worksheet_id
                JOIN problem_question question
                  ON question.id = item.question_id
                 AND question.deleted_at IS NULL
                JOIN problem_answer_unit answer_unit
                  ON answer_unit.question_id = question.id
                LEFT JOIN submission_answer answer
                  ON answer.assignment_student_id = chain.assignment_student_id
                 AND answer.answer_unit_id = answer_unit.id
                WHERE (item.custom_stage IS NULL OR item.custom_stage <> 'ADVANCED')
                  AND answer_unit.diagnostic_type IS NOT NULL
                GROUP BY question.sub_unit_id, answer_unit.diagnostic_type
                """, assignmentId, studentId,
                (rs, rowNum) -> new DiagnosticStageEvidenceRow(
                        rs.getLong("sub_unit_id"),
                        DiagnosticStage.valueOf(rs.getString("diagnostic_type")),
                        rs.getInt("graded_unit_count"),
                        rs.getInt("incorrect_unit_count")));
    }

    /** 공통 사슬 CTE 를 앞에 붙여 실행한다. */
    private <T> List<T> query(String sql, long assignmentId, long studentId, RowMapper<T> mapper) {
        return jdbcClient.sql(CHAIN_CTE + sql)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query(mapper)
                .list();
    }
}
