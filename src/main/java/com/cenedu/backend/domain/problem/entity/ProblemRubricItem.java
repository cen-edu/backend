package com.cenedu.backend.domain.problem.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 서술형 채점 기준. 항목별 가중치로 LLM 판정 결과를 점수로 환산한다. */
@Entity
@Getter
@Table(name = "problem_rubric_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_rubric_item_question_order",
                columnNames = {"question_id", "display_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemRubricItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_problem_rubric_item_question"))
    private ProblemQuestion question;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "label", nullable = false, columnDefinition = "TEXT")
    private String label;

    @Column(name = "weight", nullable = false)
    private short weight;

    /** 승인한 교사의 member 도메인 계정 ID. */
    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    private ProblemRubricItem(ProblemQuestion question, int displayOrder, String label,
                              short weight, Long approvedBy, OffsetDateTime approvedAt) {
        this.question = question;
        this.displayOrder = displayOrder;
        this.label = label;
        this.weight = weight;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    /** 채점 기준 항목을 생성한다. 미승인 항목은 approvedBy·approvedAt 이 null 이다. */
    public static ProblemRubricItem create(ProblemQuestion question, int displayOrder, String label,
                                           short weight, Long approvedBy,
                                           OffsetDateTime approvedAt) {
        return new ProblemRubricItem(question, displayOrder, label, weight, approvedBy, approvedAt);
    }
}
