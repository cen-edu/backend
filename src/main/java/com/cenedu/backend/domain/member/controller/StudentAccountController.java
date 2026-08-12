package com.cenedu.backend.domain.member.controller;

import com.cenedu.backend.domain.auth.dto.request.PasswordChangeRequest;
import com.cenedu.backend.domain.auth.service.AuthService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생 본인의 계정 관리 API. */
@RestController
@RequestMapping("/api/student/account")
@RequiredArgsConstructor
public class StudentAccountController {

    private final AuthService authService;

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        authService.changeStudentPassword(user.memberId(), request);
        return ApiResponse.successEmpty();
    }
}
