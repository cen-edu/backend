package com.cenedu.backend.domain.member.controller;

import com.cenedu.backend.domain.auth.dto.request.TeacherPasswordChangeRequest;
import com.cenedu.backend.domain.auth.service.AuthService;
import com.cenedu.backend.domain.member.dto.response.TeacherAccountResponse;
import com.cenedu.backend.domain.member.service.MemberAccountService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 교사 본인의 계정 관리 API. */
@RestController
@RequestMapping("/api/teacher/account")
@RequiredArgsConstructor
public class TeacherAccountController {

    private final MemberAccountService memberAccountService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<TeacherAccountResponse> getMyAccount(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(memberAccountService.getTeacherAccount(user.memberId()));
    }

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TeacherPasswordChangeRequest request
    ) {
        authService.changeTeacherPassword(user.memberId(), request);
        return ApiResponse.successEmpty();
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        memberAccountService.withdrawTeacher(user.memberId());
        return ApiResponse.successEmpty();
    }
}
