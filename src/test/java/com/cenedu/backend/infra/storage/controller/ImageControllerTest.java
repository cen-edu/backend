package com.cenedu.backend.infra.storage.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cenedu.backend.domain.submission.service.SubmissionImageService;
import com.cenedu.backend.domain.problem.service.ProblemImageService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.AuthenticatedUser;
import com.cenedu.backend.infra.storage.dto.request.AnswerImageUploadRequest;
import com.cenedu.backend.infra.storage.dto.response.ImageUrlResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class ImageControllerTest {

    private final SubmissionImageService submissionImageService =
            mock(SubmissionImageService.class);
    private final ProblemImageService problemImageService = mock(ProblemImageService.class);
    private final ImageController controller = new ImageController(
            submissionImageService, problemImageService);

    @Test
    @DisplayName("업로드 API는 JWT 학생 정보와 경로 ID, 파일을 서비스에 전달한다")
    void uploadsWithAuthenticatedStudent() {
        AuthenticatedUser user = new AuthenticatedUser(11L, UserRole.STUDENT);
        MultipartFile file = mock(MultipartFile.class);

        controller.uploadAnswerImage(
                user, 1001L, 501L, new AnswerImageUploadRequest(file));

        verify(submissionImageService).upload(
                11L, UserRole.STUDENT, 1001L, 501L, file);
    }

    @Test
    @DisplayName("조회 API는 JWT 사용자 정보로 생성한 이미지 URL을 공통 응답에 담는다")
    void getsUrlWithAuthenticatedTeacher() {
        AuthenticatedUser user = new AuthenticatedUser(7L, UserRole.TEACHER);
        when(submissionImageService.createGetUrl(
                7L, UserRole.TEACHER, 1001L, 501L))
                .thenReturn("https://example.com/image");

        ApiResponse<ImageUrlResponse> response = controller.getAnswerImageUrl(
                user, 1001L, 501L);

        assertThat(response.success()).isTrue();
        assertThat(response.data().url()).isEqualTo("https://example.com/image");
    }
}
