package com.cenedu.backend.domain.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 교사 이메일 또는 서버 발급 학생 아이디와 비밀번호를 사용하는 로그인 요청. */
public record LoginRequest(
        @Schema(example = "user@example.com")
        @JsonAlias("email")
        @NotBlank(message = "로그인 아이디는 필수입니다.")
        @Size(max = 64, message = "로그인 아이디는 64자 이하여야 합니다.")
        String loginId,

        @Schema(example = "stringst")
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        String password
) {
}
