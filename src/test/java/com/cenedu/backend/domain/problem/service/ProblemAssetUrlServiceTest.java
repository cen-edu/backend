package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageStorageService;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.global.common.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemAssetUrlServiceTest {

    @Mock
    private ImageStorageService imageStorageService;

    private ProblemAssetUrlService problemAssetUrlService;

    @BeforeEach
    void setUp() {
        problemAssetUrlService = new ProblemAssetUrlService(
            imageStorageService,
            new S3Properties(
                "ap-northeast-2",
                "problem-bucket",
                "answer-bucket",
                "test-access-key",
                "test-secret-key"
            )
        );
    }

    @Test
    @DisplayName("storage key를 문항 버킷의 1시간 presigned URL로 변환한다")
    void createsProblemAssetUrl() {
        String storageKey =
            "questions/110/M1_2_06_11319_11635_F1.png";
        when(imageStorageService.createGetUrl(
            "problem-bucket",
            storageKey,
            Duration.ofHours(1)
        )).thenReturn("https://example.com/question-image");

        String url = problemAssetUrlService.createUrl(storageKey);

        assertThat(url).isEqualTo(
            "https://example.com/question-image"
        );
        verify(imageStorageService).createGetUrl(
            "problem-bucket",
            storageKey,
            Duration.ofHours(1)
        );
    }

    @Test
    @DisplayName("S3 업로드 전 자산은 URL을 발급하지 않는다")
    void rejectsNonReadyAsset() {
        ProblemAsset asset = ProblemAsset.createPending(null, "F1", AssetRole.FIGURE, (short) 0,
                "questions/generated/short-input/1/F1-hash.svg", 0, 0, "그림");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> problemAssetUrlService.createUrl(asset))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.cenedu.backend.global.common.ErrorCode.PROBLEM_ASSET_NOT_READY);
    }
}
