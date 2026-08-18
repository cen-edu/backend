package com.cenedu.backend.domain.problem.service;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.storage.s3",
    name = "enabled",
    havingValue = "true"
)
public class ProblemAssetUrlService {

    private static final Duration URL_EXPIRATION =
        Duration.ofHours(6);

    private final ImageStorageService imageStorageService;
    private final S3Properties s3Properties;

    /** problem_asset.storage_key를 6시간 동안 조회 가능한 S3 URL로 변환한다. */
    public String createUrl(String storageKey) {
        return imageStorageService.createGetUrl(
            s3Properties.requiredProblemBucket(),
            storageKey,
            URL_EXPIRATION
        );
    }
}
