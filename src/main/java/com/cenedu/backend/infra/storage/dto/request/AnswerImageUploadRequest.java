package com.cenedu.backend.infra.storage.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;

/** 학생 답안 이미지 업로드 요청. */
public record AnswerImageUploadRequest(
        @NotNull(message = "이미지 파일은 필수입니다.")
        MultipartFile file
) {
}
