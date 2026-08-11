package com.cenedu.backend.domain.grading.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서술형 채점 기준 항목별 판정 결과.
 *
 * <p>채점에 실패하면 행을 만들지 않는다. 행이 없으면 판정하지 않은 것이고,
 * satisfied 가 false 면 판정 결과가 미충족이라는 뜻이다.
 */
@Entity
@Getter
@Table(name = "grading_rubric_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_grading_rubric_result",
                columnNames = {"student_answer_id", "rubric_item_id"}),
        indexes = @Index(name = "idx_grading_rubric_result_rubric_item",
                columnList = "rubric_item_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingRubricResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** submission 도메인의 학생 답안 ID. */
    @Column(name = "student_answer_id", nullable = false)
    private Long studentAnswerId;

    /** problem 도메인의 채점 기준 항목 ID. */
    @Column(name = "rubric_item_id", nullable = false)
    private Long rubricItemId;

    @Column(name = "satisfied", nullable = false)
    private boolean satisfied;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    private GradingRubricResult(Long studentAnswerId, Long rubricItemId, boolean satisfied,
                                String evidence) {
        this.studentAnswerId = studentAnswerId;
        this.rubricItemId = rubricItemId;
        this.satisfied = satisfied;
        this.evidence = evidence;
    }

    /** 채점 기준 항목 하나에 대한 판정 결과를 기록한다. */
    public static GradingRubricResult create(Long studentAnswerId, Long rubricItemId,
                                             boolean satisfied, String evidence) {
        return new GradingRubricResult(studentAnswerId, rubricItemId, satisfied, evidence);
    }
}
