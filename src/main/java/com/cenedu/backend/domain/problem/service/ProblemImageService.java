package com.cenedu.backend.domain.problem.service;

import java.time.Duration;

import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
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
    private static final Duration PROBLEM_IMAGE_URL_EXPIRATION = Duration.ofHours(1);

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
        getQuestion(questionId);
        ProblemAsset asset = assetRepository
                .findByQuestionIdAndAssetKey(questionId, MAIN_ASSET_KEY)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        return imageStorageService.createGetUrl(
                s3Properties.requiredProblemBucket(),
                asset.getStorageKey(),
                PROBLEM_IMAGE_URL_EXPIRATION
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
