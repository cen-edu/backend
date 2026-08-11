package com.cenedu.backend.domain.problem.entity;

import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.enums.CompareMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채점의 최소 단위이자 정답의 유일한 보관처. */
@Entity
@Getter
@Table(name = "problem_answer_unit",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_answer_unit_question_key",
                columnNames = {"question_id", "unit_key"}),
        indexes = @Index(name = "idx_problem_answer_unit_step", columnList = "step_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAnswerUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_problem_answer_unit_question"))
    private ProblemQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id",
            foreignKey = @ForeignKey(name = "fk_problem_answer_unit_step"))
    private ProblemStep step;

    /** 빈칸형은 B1, B2… 객관식·서술형은 MAIN. */
    @Column(name = "unit_key", nullable = false, length = 20)
    private String unitKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "label", length = 100)
    private String label;

    /** 서술형만 null 이다(모범답안 미저장). 객관식은 1 기준 보기 번호. */
    @Column(name = "answer_raw", columnDefinition = "TEXT")
    private String answerRaw;

    @Column(name = "answer_normalized", columnDefinition = "TEXT")
    private String answerNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "compare_method", nullable = false, length = 20)
    private CompareMethod compareMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagnostic_type", length = 20)
    private DiagnosticType diagnosticType;

    @Column(name = "display_unit", length = 20)
    private String displayUnit;

    private ProblemAnswerUnit(ProblemQuestion question, ProblemStep step, String unitKey,
                              int displayOrder, String label, String answerRaw,
                              String answerNormalized, CompareMethod compareMethod,
                              DiagnosticType diagnosticType, String displayUnit) {
        this.question = question;
        this.step = step;
        this.unitKey = unitKey;
        this.displayOrder = displayOrder;
        this.label = label;
        this.answerRaw = answerRaw;
        this.answerNormalized = answerNormalized;
        this.compareMethod = compareMethod;
        this.diagnosticType = diagnosticType;
        this.displayUnit = displayUnit;
    }

    /** 채점 단위를 생성한다. 빈칸형이 아니면 step 이 null 이다. */
    public static ProblemAnswerUnit create(ProblemQuestion question, ProblemStep step,
                                           String unitKey, int displayOrder, String label,
                                           String answerRaw, String answerNormalized,
                                           CompareMethod compareMethod,
                                           DiagnosticType diagnosticType, String displayUnit) {
        return new ProblemAnswerUnit(question, step, unitKey, displayOrder, label, answerRaw,
                answerNormalized, compareMethod, diagnosticType, displayUnit);
    }
}
