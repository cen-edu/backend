package com.cenedu.backend.infra.storage.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import jakarta.validation.constraints.NotBlank;

/**
 * 이미지 저장에 사용하는 AWS S3 설정값.
 *
 * <p>서술형 채점 파이프라인의 URL 만료를 설정으로 둔 이유: 이 URL 은 사람이 아니라 모델 쪽이
 * 한 번 가져가고 끝이라 짧아도 된다(D4 — 칸마다 채점 직전 발급, 2a 실측 10.6초). 그런데 측정
 * 중에는 실패한 실행의 이미지를 사람이 다시 열어 봐야 "모델이 잘못 읽었는가" 와 "이미지가 잘못
 * 올라갔는가" 가 갈린다. 두 요구가 반대라 값을 코드에 박지 않고 환경이 정하게 한다.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
        @NotBlank String region,
        String problemBucket,
        String answerBucket,
        @NotBlank String accessKeyId,
        @NotBlank String secretAccessKey,
        @DefaultValue("15m") Duration gradingPipelineUrlExpiration
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
