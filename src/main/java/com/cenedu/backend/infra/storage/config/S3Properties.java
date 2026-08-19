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
 * <p>조회 URL 만료 시간을 설정으로 둔 이유: 학생 풀이 화면과 교사 채점 화면은 화면을 열 때 URL을
 * 한 번 받고 그대로 쓴다. 만료가 화면에 머무는 시간보다 짧으면 S3가 403을 돌려주고 이미지가
 * 깨진다. 적정값은 실제 사용 시간에 달렸으므로 재배포 없이 조정할 수 있어야 한다.
 *
 * <p><b>서술형 채점 파이프라인은 만료가 따로다.</b> 그 URL 은 사람이 아니라 모델 쪽이 한 번
 * 가져가고 끝이라 화면용보다 훨씬 짧아도 된다(D4 — 칸마다 채점 직전 발급, 실측 10.6초). 다만
 * 측정 중에는 실패한 실행의 이미지를 사람이 다시 열어 봐야 "모델이 잘못 읽었는가" 와 "이미지가
 * 잘못 올라갔는가" 가 갈리므로, 값을 코드에 박지 않고 환경이 정하게 한다.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
        @NotBlank String region,
        String problemBucket,
        String answerBucket,
        @NotBlank String accessKeyId,
        @NotBlank String secretAccessKey,
        @DefaultValue("2h") Duration answerUrlExpiration,
        @DefaultValue("6h") Duration problemUrlExpiration,
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
