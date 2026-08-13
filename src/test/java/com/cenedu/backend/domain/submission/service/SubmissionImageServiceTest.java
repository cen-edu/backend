package com.cenedu.backend.domain.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService;
import com.cenedu.backend.domain.worksheet.service.WorksheetImageAccessService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class SubmissionImageServiceTest {

    @Mock
    private WorksheetImageAccessService worksheetImageAccessService;
    @Mock
    private ProblemAnswerUnitService answerUnitService;
    @Mock
    private ImageFileValidator imageFileValidator;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private MultipartFile file;

    private SubmissionImageService submissionImageService;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
                "ap-northeast-2", "problem-bucket", "answer-bucket",
                "test-access-key", "test-secret-key");
        submissionImageService = new SubmissionImageService(
                worksheetImageAccessService,
                answerUnitService,
                imageFileValidator,
                imageStorageService,
                properties
        );
    }

    @Test
    @DisplayName("학생 답안 이미지의 수행 회차와 답안 칸을 검증한 뒤 S3에 저장한다")
    void uploadsAuthorizedStudentAnswerImage() {
        byte[] content = new byte[]{1, 2, 3};
        when(worksheetImageAccessService.getAuthorizedWorksheetId(
                11L, UserRole.STUDENT, 1001L)).thenReturn(101L);
        when(answerUnitService.getQuestionId(501L)).thenReturn(35L);
        when(imageFileValidator.validate(file))
                .thenReturn(new ValidatedImage(content, "image/png", 100, 80));

        submissionImageService.upload(11L, UserRole.STUDENT, 1001L, 501L, file);

        verify(worksheetImageAccessService).validateQuestionIncluded(101L, 35L);
        verify(imageStorageService).upload(
                "answer-bucket", "answers/1001/501", content, "image/png");
    }

    @Test
    @DisplayName("교사는 학생 답안 이미지를 업로드할 수 없다")
    void rejectsTeacherUpload() {
        assertThatThrownBy(() -> submissionImageService.upload(
                7L, UserRole.TEACHER, 1001L, 501L, file))
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
    @DisplayName("학생과 교사는 권한 검증 후 같은 답안 이미지 조회 URL을 받는다")
    void createsAnswerImageGetUrl() {
        when(worksheetImageAccessService.getAuthorizedWorksheetId(
                7L, UserRole.TEACHER, 1001L)).thenReturn(101L);
        when(answerUnitService.getQuestionId(501L)).thenReturn(35L);
        when(imageStorageService.createGetUrl(
                "answer-bucket", "answers/1001/501", Duration.ofMinutes(10)))
                .thenReturn("https://example.com/image");

        String url = submissionImageService.createGetUrl(
                7L, UserRole.TEACHER, 1001L, 501L);

        assertThat(url).isEqualTo("https://example.com/image");
        verify(worksheetImageAccessService).validateQuestionIncluded(101L, 35L);
    }
}
