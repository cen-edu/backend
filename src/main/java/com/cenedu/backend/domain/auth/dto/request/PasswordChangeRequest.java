package com.cenedu.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인한 회원의 비밀번호 변경 요청. */
public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "현재 비밀번호는 8자 이상 64자 이하여야 합니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "새 비밀번호는 8자 이상 64자 이하여야 합니다.")
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
        @Size(min = 8, max = 64, message = "새 비밀번호 확인은 8자 이상 64자 이하여야 합니다.")
        String newPasswordConfirm
) {
}
