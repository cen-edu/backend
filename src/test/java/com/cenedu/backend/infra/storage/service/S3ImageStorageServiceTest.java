package com.cenedu.backend.infra.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3ImageStorageService imageStorageService;

    @Test
    @DisplayName("이미지 원본 바이트와 Content-Type을 지정한 S3 위치에 저장한다")
    void uploadsOriginalImageBytes() throws IOException {
        byte[] image = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

        imageStorageService.upload(
                "answer-bucket",
                "answers/1001/501",
                image,
                "image/png"
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("answer-bucket");
        assertThat(request.key()).isEqualTo("answers/1001/501");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.contentLength()).isEqualTo(image.length);
        assertThat(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes())
                .isEqualTo(image);
    }

    @Test
    @DisplayName("지정한 객체와 만료시간으로 조회 URL을 생성한다")
    void createsPresignedGetUrl() throws Exception {
        PresignedGetObjectRequest presignedRequest = anyPresignedRequest();
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        String url = imageStorageService.createGetUrl(
                "answer-bucket",
                "answers/1001/501",
                Duration.ofMinutes(10)
        );

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());

        GetObjectPresignRequest request = requestCaptor.getValue();
        assertThat(request.signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(request.getObjectRequest().bucket()).isEqualTo("answer-bucket");
        assertThat(request.getObjectRequest().key()).isEqualTo("answers/1001/501");
        assertThat(url).isEqualTo("https://example.com/answers/1001/501");
    }

    @Test
    @DisplayName("S3에 객체가 없으면 이미지 없음 오류로 변환한다")
    void convertsMissingObjectError() {
        S3Exception missing = (S3Exception) S3Exception.builder()
                .statusCode(404)
                .message("not found")
                .build();
        doThrow(missing).when(s3Client).headObject(any(HeadObjectRequest.class));

        assertThatThrownBy(() -> imageStorageService.createGetUrl(
                "answer-bucket", "answers/1001/501", Duration.ofMinutes(10)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);
    }

    private PresignedGetObjectRequest anyPresignedRequest() throws Exception {
        PresignedGetObjectRequest request = org.mockito.Mockito.mock(
                PresignedGetObjectRequest.class);
        when(request.url()).thenReturn(
                URI.create("https://example.com/answers/1001/501").toURL());
        return request;
    }
}
