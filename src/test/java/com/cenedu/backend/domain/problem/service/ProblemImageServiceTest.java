package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageFileValidator;
import com.cenedu.backend.infra.storage.service.ImageStorageService;
import com.cenedu.backend.infra.storage.service.ValidatedImage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ProblemImageServiceTest {

    @Mock
    private ProblemQuestionRepository questionRepository;
    @Mock
    private ProblemAssetRepository assetRepository;
    @Mock
    private ImageFileValidator imageFileValidator;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private MultipartFile file;

    private ProblemImageService problemImageService;

    @BeforeEach
    void setUp() {
        problemImageService = new ProblemImageService(
                questionRepository,
                assetRepository,
                imageFileValidator,
                imageStorageService,
                new S3Properties(
                        "ap-northeast-2", "problem-bucket", "answer-bucket",
                        "test-access-key", "test-secret-key",
                        Duration.ofHours(2), Duration.ofHours(6))
        );
    }

    @Test
    @DisplayName("교사가 문항 대표 이미지를 올리면 S3와 problem_asset에 저장한다")
    void uploadsProblemImage() {
        ProblemQuestion question = mock(ProblemQuestion.class);
        byte[] content = new byte[]{1, 2, 3};
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(assetRepository.findByQuestionIdAndAssetKey(1L, "MAIN"))
                .thenReturn(Optional.empty());
        when(imageFileValidator.validate(file))
                .thenReturn(new ValidatedImage(content, "image/png", 320, 180));

        problemImageService.upload(7L, UserRole.TEACHER, 1L, file);

        verify(imageStorageService).upload(
                "problem-bucket", "problems/1", content, "image/png");
        ArgumentCaptor<ProblemAsset> assetCaptor = ArgumentCaptor.forClass(ProblemAsset.class);
        verify(assetRepository).save(assetCaptor.capture());
        assertThat(assetCaptor.getValue().getStorageKey()).isEqualTo("problems/1");
        assertThat(assetCaptor.getValue().getWidthPx()).isEqualTo(320);
        assertThat(assetCaptor.getValue().getHeightPx()).isEqualTo(180);
    }

    @Test
    @DisplayName("학생은 문항 이미지를 업로드할 수 없다")
    void rejectsStudentUpload() {
        assertThatThrownBy(() -> problemImageService.upload(
                11L, UserRole.STUDENT, 1L, file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ACCESS_DENIED);

        verify(imageStorageService, never()).upload(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("저장된 문항 대표 이미지의 설정된 만료 시간 조회 URL을 반환한다")
    void createsProblemImageGetUrl() {
        ProblemQuestion question = mock(ProblemQuestion.class);
        ProblemAsset asset = mock(ProblemAsset.class);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(assetRepository.findByQuestionIdAndAssetKey(1L, "MAIN"))
                .thenReturn(Optional.of(asset));
        when(asset.getStorageKey()).thenReturn("problems/1");
        when(imageStorageService.createGetUrl(
                "problem-bucket", "problems/1", Duration.ofHours(6)))
                .thenReturn("https://example.com/problem");

        String url = problemImageService.createGetUrl(1L);

        assertThat(url).isEqualTo("https://example.com/problem");
    }

    @Test
    @DisplayName("READY 자산은 자산 키로 조회 URL을 다시 발급한다")
    void reissuesReadyAssetUrl() {
        ProblemQuestion question = mock(ProblemQuestion.class);
        ProblemAsset asset = mock(ProblemAsset.class);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(assetRepository.findByQuestionIdAndAssetKey(1L, "F1"))
                .thenReturn(Optional.of(asset));
        when(asset.getStorageStatus()).thenReturn(ProblemAssetStorageStatus.READY);
        when(asset.getStorageKey()).thenReturn("questions/30/F1.png");
        when(imageStorageService.createGetUrl(
                "problem-bucket", "questions/30/F1.png", Duration.ofHours(6)))
                .thenReturn("https://example.com/asset");

        String url = problemImageService.createAssetGetUrl(1L, "F1");

        assertThat(url).isEqualTo("https://example.com/asset");
    }

    @Test
    @DisplayName("READY 가 아닌 자산은 재발급하지 않는다")
    void rejectsNonReadyAssetReissue() {
        ProblemQuestion question = mock(ProblemQuestion.class);
        ProblemAsset asset = mock(ProblemAsset.class);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(assetRepository.findByQuestionIdAndAssetKey(1L, "F1"))
                .thenReturn(Optional.of(asset));
        when(asset.getStorageStatus()).thenReturn(ProblemAssetStorageStatus.PENDING);

        assertThatThrownBy(() -> problemImageService.createAssetGetUrl(1L, "F1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROBLEM_ASSET_NOT_READY);
    }
}
