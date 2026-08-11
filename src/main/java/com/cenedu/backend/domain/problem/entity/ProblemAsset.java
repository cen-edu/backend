package com.cenedu.backend.domain.problem.entity;

import com.cenedu.backend.domain.problem.entity.enums.AssetRole;

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

/** 문항에 딸린 이미지. 학생 답안 이미지와 달리 공개 버킷에 둔다. */
@Entity
@Getter
@Table(name = "problem_asset",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_asset_question_key",
                columnNames = {"question_id", "asset_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_problem_asset_question"))
    private ProblemQuestion question;

    /** content_blocks 의 assetRef 와 조인하는 키. */
    @Column(name = "asset_key", nullable = false, length = 16)
    private String assetKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AssetRole role;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "width_px", nullable = false)
    private int widthPx;

    @Column(name = "height_px", nullable = false)
    private int heightPx;

    @Column(name = "alt_text", columnDefinition = "TEXT")
    private String altText;

    private ProblemAsset(ProblemQuestion question, String assetKey, AssetRole role,
                         short displayOrder, String storageKey, int widthPx, int heightPx,
                         String altText) {
        this.question = question;
        this.assetKey = assetKey;
        this.role = role;
        this.displayOrder = displayOrder;
        this.storageKey = storageKey;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.altText = altText;
    }

    /** 문항 이미지를 생성한다. */
    public static ProblemAsset create(ProblemQuestion question, String assetKey, AssetRole role,
                                      short displayOrder, String storageKey, int widthPx,
                                      int heightPx, String altText) {
        return new ProblemAsset(question, assetKey, role, displayOrder, storageKey, widthPx,
                heightPx, altText);
    }
}
