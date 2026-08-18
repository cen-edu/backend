package com.cenedu.backend.domain.problem.entity;

import java.time.LocalDateTime;

import com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus;
import com.cenedu.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DB 커밋 이후 로컬 draft를 최종 S3 key로 옮기는 재시도 작업이다. */
@Entity
@Table(name = "problem_asset_storage_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAssetStorageTask extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_asset_id", nullable = false, unique = true)
    private ProblemAsset asset;
    @Column(name = "source_local_path", nullable = false, length = 500)
    private String sourceLocalPath;
    @Column(name = "target_storage_key", nullable = false, unique = true, length = 255)
    private String targetStorageKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ProblemAssetStorageStatus status;
    @Column(nullable = false) private int attemptCount;
    private LocalDateTime nextAttemptAt;
    @Column(length = 100) private String lastErrorCode;

    private ProblemAssetStorageTask(ProblemAsset asset, String sourceLocalPath, String targetStorageKey) {
        this.asset = asset; this.sourceLocalPath = sourceLocalPath; this.targetStorageKey = targetStorageKey;
        this.status = ProblemAssetStorageStatus.PENDING; this.attemptCount = 0;
    }

    /** 최종화 transaction 안에서 업로드 대기 작업을 만든다. */
    public static ProblemAssetStorageTask create(ProblemAsset asset, String sourceLocalPath, String targetStorageKey) {
        return new ProblemAssetStorageTask(asset, sourceLocalPath, targetStorageKey);
    }

    /** 업로드 선점을 기록한다. */
    public void start() { status = ProblemAssetStorageStatus.PROCESSING; attemptCount++; }

    /** 업로드 성공을 기록한다. */
    public void complete() { status = ProblemAssetStorageStatus.READY; nextAttemptAt = null; lastErrorCode = null; asset.markReady(); }

    /** 재시도 가능한 실패를 기록한다. */
    public void retry(String code, LocalDateTime next) { status = ProblemAssetStorageStatus.RETRY_WAIT; lastErrorCode = code; nextAttemptAt = next; asset.markFailed(true); }

    /** 재시도 소진으로 영구 실패를 기록한다. */
    public void fail(String code) { status = ProblemAssetStorageStatus.FAILED; lastErrorCode = code; asset.markFailed(false); }
}
