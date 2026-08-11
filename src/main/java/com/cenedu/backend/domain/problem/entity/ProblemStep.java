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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 빈칸형 문항의 풀이 단계. 빈칸형만 행을 가진다는 것은 서비스가 검증한다. */
@Entity
@Getter
@Table(name = "problem_step",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_step_question_order",
                columnNames = {"question_id", "display_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_problem_step_question"))
    private ProblemQuestion question;

    /** 원천 order 는 1부터이므로 적재 시 0부터 재부여한다. */
    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "label", length = 100)
    private String label;

    /** TEXT/BLANK 세그먼트 배열. BLANK 의 unitKey 가 problem_answer_unit.unit_key 와 대응한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segments", nullable = false)
    private String segments;

    private ProblemStep(ProblemQuestion question, short displayOrder, String label,
                        String segments) {
        this.question = question;
        this.displayOrder = displayOrder;
        this.label = label;
        this.segments = segments;
    }

    /** 문항에 속한 풀이 단계를 생성한다. */
    public static ProblemStep create(ProblemQuestion question, short displayOrder, String label,
                                     String segments) {
        return new ProblemStep(question, displayOrder, label, segments);
    }
}
