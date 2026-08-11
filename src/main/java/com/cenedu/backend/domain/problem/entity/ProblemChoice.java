package com.cenedu.backend.domain.problem.entity;

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

/**
 * 객관식 보기.
 *
 * <p>정답 여부 컬럼이 의도적으로 없다. 정답은 problem_answer_unit 에만 있어
 * 학생 응답 경로로 정답이 새지 않는다.
 */
@Entity
@Getter
@Table(name = "problem_choice",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_choice_question_order",
                columnNames = {"question_id", "display_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_problem_choice_question"))
    private ProblemQuestion question;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    private ProblemChoice(ProblemQuestion question, short displayOrder, String content) {
        this.question = question;
        this.displayOrder = displayOrder;
        this.content = content;
    }

    /** 객관식 보기를 생성한다. */
    public static ProblemChoice create(ProblemQuestion question, short displayOrder,
                                       String content) {
        return new ProblemChoice(question, displayOrder, content);
    }
}
