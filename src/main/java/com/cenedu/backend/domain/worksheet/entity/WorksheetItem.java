package com.cenedu.backend.domain.worksheet.entity;

import java.math.BigDecimal;

import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;
import com.cenedu.backend.domain.worksheet.entity.enums.SupportMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 학습지에 담긴 문항 한 줄. */
@Entity
@Getter
@Table(name = "worksheet_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_worksheet_item_question",
                        columnNames = {"worksheet_id", "question_id"}),
                @UniqueConstraint(name = "uk_worksheet_item_order",
                        columnNames = {"worksheet_id", "display_order"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worksheet_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_worksheet_item_worksheet"))
    private Worksheet worksheet;

    /** problem 도메인의 문항 ID. */
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 종합평가만 값이 있다. */
    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore;

    /** 맞춤 학습만 값이 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "custom_stage", length = 20)
    private CustomStage customStage;

    /** 단일 컬럼이라 챗봇/개념보기 배타가 구조적으로 보장된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "support_mode", length = 20)
    private SupportMode supportMode;

    private WorksheetItem(Worksheet worksheet, Long questionId, int displayOrder,
                          BigDecimal maxScore, CustomStage customStage, SupportMode supportMode) {
        this.worksheet = worksheet;
        this.questionId = questionId;
        this.displayOrder = displayOrder;
        this.maxScore = maxScore;
        this.customStage = customStage;
        this.supportMode = supportMode;
    }

    /** 학습지에 문항을 배치한다. */
    public static WorksheetItem create(Worksheet worksheet, Long questionId, int displayOrder,
                                       BigDecimal maxScore, CustomStage customStage,
                                       SupportMode supportMode) {
        return new WorksheetItem(worksheet, questionId, displayOrder, maxScore, customStage,
                supportMode);
    }
}
