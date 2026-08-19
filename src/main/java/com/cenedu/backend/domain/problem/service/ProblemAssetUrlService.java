package com.cenedu.backend.domain.problem.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageStorageService;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.storage.s3",
    name = "enabled",
    havingValue = "true"
)
public class ProblemAssetUrlService {

    private final ImageStorageService imageStorageService;
    private final S3Properties s3Properties;

    /** problem_asset.storage_key를 설정된 만료 시간 동안 조회 가능한 S3 URL로 변환한다. */
    public String createUrl(String storageKey) {
        return imageStorageService.createGetUrl(
            s3Properties.requiredProblemBucket(),
            storageKey,
            s3Properties.problemUrlExpiration()
        );
    }

    /** READY 상태의 문제 자산만 S3 조회 URL로 변환한다. */
    public String createUrl(ProblemAsset asset) {
        if (asset == null || asset.getStorageStatus() != ProblemAssetStorageStatus.READY) {
            throw new BusinessException(ErrorCode.PROBLEM_ASSET_NOT_READY);
        }
        return createUrl(asset.getStorageKey());
    }
}
