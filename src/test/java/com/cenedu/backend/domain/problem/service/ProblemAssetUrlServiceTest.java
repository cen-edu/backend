package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageStorageService;

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
    @DisplayName("storage key를 문항 버킷의 6시간 presigned URL로 변환한다")
    void createsProblemAssetUrl() {
        String storageKey =
            "questions/110/M1_2_06_11319_11635_F1.png";
        when(imageStorageService.createGetUrl(
            "problem-bucket",
            storageKey,
            Duration.ofHours(6)
        )).thenReturn("https://example.com/question-image");

        String url = problemAssetUrlService.createUrl(storageKey);

        assertThat(url).isEqualTo(
            "https://example.com/question-image"
        );
        verify(imageStorageService).createGetUrl(
            "problem-bucket",
            storageKey,
            Duration.ofHours(6)
        );
    }
}
