package com.cenedu.backend.domain.worksheet.entity;

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

/** 출제 조건. 소단원 × 난이도별 문항 수로, 복제 후 재구성의 입력값이 된다. */
@Entity
@Getter
@Table(name = "worksheet_gen_spec",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_worksheet_gen_spec",
                columnNames = {"worksheet_id", "sub_unit_id", "difficulty"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetGenSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worksheet_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_worksheet_gen_spec_worksheet"))
    private Worksheet worksheet;

    /** curriculum 도메인의 소단원 ID. */
    @Column(name = "sub_unit_id", nullable = false)
    private Long subUnitId;

    @Column(name = "difficulty", nullable = false)
    private short difficulty;

    @Column(name = "question_count", nullable = false)
    private short questionCount;

    private WorksheetGenSpec(Worksheet worksheet, Long subUnitId, short difficulty,
                             short questionCount) {
        this.worksheet = worksheet;
        this.subUnitId = subUnitId;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
    }

    /** 학습지의 출제 조건 한 줄을 생성한다. */
    public static WorksheetGenSpec create(Worksheet worksheet, Long subUnitId, short difficulty,
                                          short questionCount) {
        return new WorksheetGenSpec(worksheet, subUnitId, difficulty, questionCount);
    }
}
