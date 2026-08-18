package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.problem.dto.response.ProblemAssetResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 문항 이미지 URL 조립만 본다. 학생 풀이·학생 결과·교사 채점 세 화면이 이 메서드를 함께 쓰므로,
 * 여기서 던지면 세 화면이 한꺼번에 죽는다.
 */
@ExtendWith(MockitoExtension.class)
class ProblemQuestionDetailServiceTest {

    @Mock
    private ProblemAssetRepository problemAssetRepository;
    @Mock
    private ProblemAssetUrlService problemAssetUrlService;
    @Mock
    private ObjectProvider<ProblemAssetUrlService> problemAssetUrlServiceProvider;

    private ProblemQuestionDetailService problemQuestionDetailService;

    @BeforeEach
    void setUp() {
        // 자산 조회 경로만 쓰는 메서드라 나머지 협력자는 이 테스트에서 호출되지 않는다.
        problemQuestionDetailService = new ProblemQuestionDetailService(
                null, null, null, null,
                problemAssetRepository,
                null, null,
                problemAssetUrlServiceProvider
        );
    }

    @Test
    @DisplayName("버킷에 없는 이미지는 url만 null이고 나머지 자산은 그대로 나온다")
    void fallsBackToNullUrlWhenObjectMissing() {
        ProblemAsset missing = asset(1L, "F1", "questions/30/missing_F1.png");
        ProblemAsset present = asset(1L, "F2", "questions/30/present_F2.png");
        when(problemAssetRepository.findAllByQuestionIds(List.of(1L))).thenReturn(List.of(missing, present));
        when(problemAssetUrlServiceProvider.getIfAvailable()).thenReturn(problemAssetUrlService);
        when(problemAssetUrlService.createUrl("questions/30/missing_F1.png"))
                .thenThrow(new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        when(problemAssetUrlService.createUrl("questions/30/present_F2.png"))
                .thenReturn("https://example.com/present");

        Map<Long, List<ProblemAssetResponse>> assets =
                problemQuestionDetailService.getAssetsByQuestionIds(List.of(1L));

        assertThat(assets.get(1L))
                .extracting(ProblemAssetResponse::assetKey, ProblemAssetResponse::url)
                .containsExactly(
                        tuple("F1", null),
                        tuple("F2", "https://example.com/present"));
    }

    @Test
    @DisplayName("저장소 장애는 삼키지 않고 그대로 올린다 — 이미지 없는 화면으로 숨으면 안 된다")
    void propagatesStorageFailure() {
        ProblemAsset broken = asset(1L, "F1", "questions/30/broken_F1.png");
        when(problemAssetRepository.findAllByQuestionIds(List.of(1L))).thenReturn(List.of(broken));
        when(problemAssetUrlServiceProvider.getIfAvailable()).thenReturn(problemAssetUrlService);
        when(problemAssetUrlService.createUrl("questions/30/broken_F1.png"))
                .thenThrow(new BusinessException(ErrorCode.IMAGE_STORAGE_FAILED));

        assertThatThrownBy(() -> problemQuestionDetailService.getAssetsByQuestionIds(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_STORAGE_FAILED);
    }

    @Test
    @DisplayName("S3 기능이 꺼져 있으면 url 없이 자산만 내려간다")
    void returnsAssetsWithoutUrlWhenS3Disabled() {
        ProblemAsset any = asset(1L, "F1", "questions/30/any_F1.png");
        when(problemAssetRepository.findAllByQuestionIds(List.of(1L))).thenReturn(List.of(any));
        when(problemAssetUrlServiceProvider.getIfAvailable()).thenReturn(null);

        Map<Long, List<ProblemAssetResponse>> assets =
                problemQuestionDetailService.getAssetsByQuestionIds(List.of(1L));

        assertThat(assets.get(1L)).singleElement()
                .satisfies(response -> {
                    assertThat(response.assetKey()).isEqualTo("F1");
                    assertThat(response.url()).isNull();
                });
    }

    /**
     * 공유 픽스처라 테스트마다 실제로 읽는 필드가 다르다. 장애 전파 테스트는 응답을 만들기 전에
     * 던지므로 assetKey·role 을 읽지 않는다 — 그래서 lenient 로 둔다.
     */
    private ProblemAsset asset(long questionId, String assetKey, String storageKey) {
        ProblemQuestion question = mock(ProblemQuestion.class);
        lenient().when(question.getId()).thenReturn(questionId);

        ProblemAsset asset = mock(ProblemAsset.class);
        lenient().when(asset.getQuestion()).thenReturn(question);
        lenient().when(asset.getAssetKey()).thenReturn(assetKey);
        lenient().when(asset.getStorageKey()).thenReturn(storageKey);
        lenient().when(asset.getRole()).thenReturn(AssetRole.FIGURE);
        return asset;
    }
}
