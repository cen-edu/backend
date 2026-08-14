package com.cenedu.backend.domain.submission.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.domain.submission.entity.enums.AnswerInputMode;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.global.common.enums.CompareMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학생 답안. 채점 칸마다 한 행이며 문항 이탈 시점마다 즉시 저장된다(제출 시점 일괄 생성 아님). */
@Entity
@Getter
@Table(name = "submission_answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_submission_answer",
                columnNames = {"assignment_student_id", "answer_unit_id"}),
        indexes = @Index(name = "idx_submission_answer_answer_unit",
                columnList = "answer_unit_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** worksheet 도메인의 학생별 배정 ID. */
    @Column(name = "assignment_student_id", nullable = false)
    private Long assignmentStudentId;

    /** problem 도메인의 채점 단위 ID. */
    @Column(name = "answer_unit_id", nullable = false)
    private Long answerUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode", nullable = false, length = 20)
    private AnswerInputMode inputMode;

    /** problem 도메인의 보기 ID. 객관식이 아니면 null 이다. */
    @Column(name = "selected_choice_id")
    private Long selectedChoiceId;

    @Column(name = "raw_latex", columnDefinition = "TEXT")
    private String rawLatex;

    @Column(name = "normalized", columnDefinition = "TEXT")
    private String normalized;

    @Column(name = "answer_image_ref", length = 255)
    private String answerImageRef;

    /** 자동채점값. 최초 기록 후 불변이다. */
    @Column(name = "auto_score", precision = 5, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    /** 점수를 고친 교사의 member 도메인 계정 ID. */
    @Column(name = "overridden_by")
    private Long overriddenBy;

    @Column(name = "overridden_at")
    private OffsetDateTime overriddenAt;

    /** 실제 적용된 채점 방법의 스냅샷. */
    @Enumerated(EnumType.STRING)
    @Column(name = "compare_method", nullable = false, length = 20)
    private CompareMethod compareMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false, length = 20)
    private GradingStatus gradingStatus;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private SubmissionAnswer(Long assignmentStudentId, Long answerUnitId,
                             AnswerInputMode inputMode, Long selectedChoiceId, String rawLatex,
                             String normalized, String answerImageRef,
                             CompareMethod compareMethod) {
        this.assignmentStudentId = assignmentStudentId;
        this.answerUnitId = answerUnitId;
        this.inputMode = inputMode;
        this.selectedChoiceId = selectedChoiceId;
        this.rawLatex = rawLatex;
        this.normalized = normalized;
        this.answerImageRef = answerImageRef;
        this.compareMethod = compareMethod;
        this.gradingStatus = GradingStatus.NOT_GRADED;
        this.createdAt = OffsetDateTime.now();
    }

    /** 제출된 답안 한 칸을 생성한다. 채점 전 상태로 만들어진다. */
    public static SubmissionAnswer create(Long assignmentStudentId, Long answerUnitId,
                                          AnswerInputMode inputMode, Long selectedChoiceId,
                                          String rawLatex, String normalized,
                                          String answerImageRef, CompareMethod compareMethod) {
        return new SubmissionAnswer(assignmentStudentId, answerUnitId, inputMode, selectedChoiceId,
                rawLatex, normalized, answerImageRef, compareMethod);
    }

    /**
     * 같은 칸에 다시 저장할 때 값을 갈아 끼운다(멱등 upsert). {@code normalized}는 여기서 건드리지
     * 않는다 — 정규화는 채점 작업 소관이라 저장 경로가 대충 채우면 안 된다. {@code auto_score}·
     * {@code final_score}·{@code overridden_*}·{@code grading_status}도 마찬가지로 건드리지
     * 않는다 — 저장 경로에서 채점 결과를 바꿀 수 있으면 안 된다.
     */
    public void updateAnswer(AnswerInputMode inputMode, Long selectedChoiceId, String rawLatex,
                             String answerImageRef, CompareMethod compareMethod) {
        this.inputMode = inputMode;
        this.selectedChoiceId = selectedChoiceId;
        this.rawLatex = rawLatex;
        this.answerImageRef = answerImageRef;
        this.compareMethod = compareMethod;
    }
}
