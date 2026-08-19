package com.cenedu.backend.infra.storage.controller;

import com.cenedu.backend.domain.submission.service.SubmissionImageService;
import com.cenedu.backend.domain.problem.service.ProblemImageService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import com.cenedu.backend.infra.storage.dto.request.AnswerImageUploadRequest;
import com.cenedu.backend.infra.storage.dto.request.ProblemImageUploadRequest;
import com.cenedu.backend.infra.storage.dto.response.ImageUrlResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 학생 답안 이미지 업로드와 학생·교사 공통 조회 API. */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
public class ImageController {

    private final SubmissionImageService submissionImageService;
    private final ProblemImageService problemImageService;

    @PostMapping(path = "/problems/{questionId}", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadProblemImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long questionId,
            @Valid @ModelAttribute ProblemImageUploadRequest request
    ) {
        problemImageService.upload(
                user.memberId(), user.role(), questionId, request.file());
    }

    @GetMapping("/problems/{questionId}")
    public ApiResponse<ImageUrlResponse> getProblemImageUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long questionId
    ) {
        String url = problemImageService.createGetUrl(questionId);
        return ApiResponse.success(new ImageUrlResponse(url));
    }

    @GetMapping("/problems/{questionId}/assets/{assetKey}")
    public ApiResponse<ImageUrlResponse> getProblemAssetUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long questionId,
            @PathVariable String assetKey
    ) {
        String url = problemImageService.createAssetGetUrl(questionId, assetKey);
        return ApiResponse.success(new ImageUrlResponse(url));
    }

    @PostMapping(path = "/answers/{assignmentStudentId}/answer-units/{answerUnitId}",
            consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadAnswerImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentStudentId,
            @PathVariable long answerUnitId,
            @Valid @ModelAttribute AnswerImageUploadRequest request
    ) {
        submissionImageService.upload(
                user.memberId(), user.role(), assignmentStudentId, answerUnitId, request.file());
    }

    @GetMapping("/answers/{assignmentStudentId}/answer-units/{answerUnitId}")
    public ApiResponse<ImageUrlResponse> getAnswerImageUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentStudentId,
            @PathVariable long answerUnitId
    ) {
        String url = submissionImageService.createGetUrl(
                user.memberId(), user.role(), assignmentStudentId, answerUnitId);
        return ApiResponse.success(new ImageUrlResponse(url));
    }
}
