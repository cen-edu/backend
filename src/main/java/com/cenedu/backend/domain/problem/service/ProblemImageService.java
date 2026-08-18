package com.cenedu.backend.domain.problem.service;


import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
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

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 문항 대표 이미지의 S3 저장과 problem_asset 기록을 관리한다. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
public class ProblemImageService {

    private static final String MAIN_ASSET_KEY = "MAIN";

    private final ProblemQuestionRepository questionRepository;
    private final ProblemAssetRepository assetRepository;
    private final ImageFileValidator imageFileValidator;
    private final ImageStorageService imageStorageService;
    private final S3Properties s3Properties;

    /** 교사가 문항의 대표 이미지를 업로드하고 저장 위치를 자산 테이블에 기록한다. */
    @Transactional
    public void upload(long memberId, UserRole role, long questionId, MultipartFile file) {
        if (role != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.IMAGE_ACCESS_DENIED);
        }
        ProblemQuestion question = getQuestion(questionId);
        ValidatedImage image = imageFileValidator.validate(file);
        String storageKey = problemKey(questionId);

        imageStorageService.upload(
                s3Properties.requiredProblemBucket(),
                storageKey,
                image.content(),
                image.contentType()
        );

        ProblemAsset asset = assetRepository
                .findByQuestionIdAndAssetKey(questionId, MAIN_ASSET_KEY)
                .orElseGet(() -> ProblemAsset.create(
                        question,
                        MAIN_ASSET_KEY,
                        AssetRole.FIGURE,
                        (short) 0,
                        storageKey,
                        image.width(),
                        image.height(),
                        null
                ));
        asset.replaceImage(storageKey, image.width(), image.height());
        assetRepository.save(asset);
    }

    /** 인증된 사용자가 조회할 문항 대표 이미지의 만료 URL을 반환한다. */
    @Transactional(readOnly = true)
    public String createGetUrl(long questionId) {
        return presign(findAsset(questionId, MAIN_ASSET_KEY));
    }

    /**
     * 화면에 이미 그려진 문항 자산 한 장의 조회 URL을 다시 발급한다.
     *
     * <p>풀이·채점 화면은 화면을 열 때 자산 URL을 한 번에 받는다. 서명이 만료되면 S3가 403을
     * 주는데, 이 API가 없으면 이미지 한 장 때문에 화면 조회를 통째로 다시 해야 한다.
     *
     * <p>READY 가 아닌 자산은 거절한다. 목록 응답({@code getAssetsByQuestionIds})이 READY 만
     * URL로 내려보내므로, 재발급도 같은 기준이어야 화면에 없던 이미지가 이 경로로 새어 나오지 않는다.
     */
    @Transactional(readOnly = true)
    public String createAssetGetUrl(long questionId, String assetKey) {
        ProblemAsset asset = findAsset(questionId, assetKey);
        if (asset.getStorageStatus() != ProblemAssetStorageStatus.READY) {
            throw new BusinessException(ErrorCode.PROBLEM_ASSET_NOT_READY);
        }
        return presign(asset);
    }

    private ProblemAsset findAsset(long questionId, String assetKey) {
        getQuestion(questionId);
        return assetRepository
                .findByQuestionIdAndAssetKey(questionId, assetKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
    }

    private String presign(ProblemAsset asset) {
        return imageStorageService.createGetUrl(
                s3Properties.requiredProblemBucket(),
                asset.getStorageKey(),
                s3Properties.problemUrlExpiration()
        );
    }

    private ProblemQuestion getQuestion(long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.IMAGE_QUESTION_NOT_FOUND));
    }

    private String problemKey(long questionId) {
        return "problems/%d".formatted(questionId);
    }
}
