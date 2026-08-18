package com.cenedu.backend.domain.problem.entity;

import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_status", nullable = false, length = 20)
    private ProblemAssetStorageStatus storageStatus;

    @Column(name = "width_px", nullable = false)
    private int widthPx;

    @Column(name = "height_px", nullable = false)
    private int heightPx;

    @Column(name = "alt_text", columnDefinition = "TEXT")
    private String altText;

    private ProblemAsset(ProblemQuestion question, String assetKey, AssetRole role,
                         short displayOrder, String storageKey, int widthPx, int heightPx,
                         String altText, ProblemAssetStorageStatus storageStatus) {
        this.question = question;
        this.assetKey = assetKey;
        this.role = role;
        this.displayOrder = displayOrder;
        this.storageKey = storageKey;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.altText = altText;
        this.storageStatus = storageStatus;
    }

    /** 문항 이미지를 생성한다. */
    public static ProblemAsset create(ProblemQuestion question, String assetKey, AssetRole role,
                                      short displayOrder, String storageKey, int widthPx,
                                      int heightPx, String altText) {
        return new ProblemAsset(question, assetKey, role, displayOrder, storageKey, widthPx,
                heightPx, altText, ProblemAssetStorageStatus.READY);
    }

    /** 승인된 생성 자산을 S3 업로드 대기 상태로 생성한다. */
    public static ProblemAsset createPending(ProblemQuestion question, String assetKey, AssetRole role,
                                             short displayOrder, String storageKey, int widthPx,
                                             int heightPx, String altText) {
        return new ProblemAsset(question, assetKey, role, displayOrder, storageKey, widthPx,
                heightPx, altText, ProblemAssetStorageStatus.PENDING);
    }

    /** 같은 이미지 자리에 새 원본을 저장했을 때 저장 위치와 크기를 갱신한다. */
    public void replaceImage(String storageKey, int widthPx, int heightPx) {
        this.storageKey = storageKey;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    /** S3 업로드를 시작한다. */
    public void markProcessing() { storageStatus = ProblemAssetStorageStatus.PROCESSING; }

    /** 최종화 transaction에서 S3 업로드 대기 상태로 전환한다. */
    public void markPending() { storageStatus = ProblemAssetStorageStatus.PENDING; }

    /** 최종 S3 객체가 확인되었음을 기록한다. */
    public void markReady() { storageStatus = ProblemAssetStorageStatus.READY; }

    /** 업로드 재시도 대기 또는 최종 실패 상태를 기록한다. */
    public void markFailed(boolean retryable) {
        storageStatus = retryable ? ProblemAssetStorageStatus.RETRY_WAIT : ProblemAssetStorageStatus.FAILED;
    }
}
