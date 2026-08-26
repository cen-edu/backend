package com.cenedu.backend.domain.worksheet.entity;

import java.math.BigDecimal;

import com.cenedu.backend.global.common.enums.CustomStage;
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

    /**
     * 이 문항을 다시 수정하려고 연 작성 Session. 수정 중이 아니면 null이다.
     *
     * <p>수정 결과를 반영할 때 그 Session이 정말 이 문항에서 열린 것인지 확인하고, 문제은행
     * 조회 교체가 같은 학습지의 다른 문항을 후보에서 빼는 데에도 이 연결을 쓴다.
     */
    @Column(name = "editing_session_id")
    private Long editingSessionId;

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

    /**
     * 배치·배점은 그대로 두고 가리키는 문항만 교사가 수정한 결과로 바꾼다.
     *
     * <p>표시 순서와 배점을 유지해야 이미 계산된 총점과 문항 번호가 흔들리지 않는다.
     * 배포 여부·유형 제약·중복 검사는 호출하는 서비스가 먼저 확인한다.
     */
    public void replaceQuestion(Long replacementQuestionId) {
        if (replacementQuestionId == null) {
            throw new IllegalArgumentException("교체할 문항 ID가 필요합니다.");
        }
        this.questionId = replacementQuestionId;
    }

    /**
     * 이 문항을 수정할 작성 Session을 연결한다.
     *
     * <p>같은 문항에서 수정을 다시 시작하면 이전 Session 연결은 끊긴다 — 확정되지 않은 채
     * 남은 Session이 나중에 반영되지 않도록 마지막 하나만 유효하게 둔다.
     */
    public void startEditSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("수정 세션 ID가 필요합니다.");
        }
        this.editingSessionId = sessionId;
    }

    /** 수정 결과 반영이 끝났거나 취소되어 더 이상 유효하지 않은 Session 연결을 끊는다. */
    public void clearEditSession() {
        this.editingSessionId = null;
    }
}
