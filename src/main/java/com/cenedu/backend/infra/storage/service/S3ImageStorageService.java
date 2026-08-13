package com.cenedu.backend.infra.storage.service;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** AWS S3에 이미지를 저장하고 presigned GET URL을 생성한다. */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean({S3Client.class, S3Presigner.class})
public class S3ImageStorageService implements ImageStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    /** 지정한 S3 버킷과 객체 키에 이미지 원본 바이트를 저장한다. */
    @Override
    public void upload(String bucket, String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            log.error("S3 image upload failed: bucket={}, key={}", bucket, key, exception);
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_FAILED);
        }
    }

    /** 지정한 S3 객체를 정해진 시간 동안 조회할 수 있는 URL을 생성한다. */
    @Override
    public String createGetUrl(String bucket, String key, Duration expiration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.headObject(headObjectRequest);
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
            }
            log.error("S3 image lookup failed: bucket={}, key={}", bucket, key, exception);
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_FAILED);
        } catch (SdkException exception) {
            log.error("S3 image URL creation failed: bucket={}, key={}", bucket, key, exception);
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_FAILED);
        }
    }
}
