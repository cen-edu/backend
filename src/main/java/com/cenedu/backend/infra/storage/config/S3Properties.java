package com.cenedu.backend.infra.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import jakarta.validation.constraints.NotBlank;

/** 이미지 저장에 사용하는 AWS S3 설정값. */
@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
        @NotBlank String region,
        String problemBucket,
        String answerBucket,
        @NotBlank String accessKeyId,
        @NotBlank String secretAccessKey
) {

    /** 문항 이미지 버킷 설정을 반환하고 비어 있으면 설정 오류로 처리한다. */
    public String requiredProblemBucket() {
        return requireBucket(problemBucket);
    }

    /** 학생 답안 이미지 버킷 설정을 반환하고 비어 있으면 설정 오류로 처리한다. */
    public String requiredAnswerBucket() {
        return requireBucket(answerBucket);
    }

    private String requireBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        }
        return bucket;
    }
}
