package com.cenedu.backend.domain.member.controller;

import com.cenedu.backend.domain.member.service.MemberAccountService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 교사 본인의 계정 관리 API. */
@RestController
@RequestMapping("/api/teacher/account")
@RequiredArgsConstructor
public class TeacherAccountController {

    private final MemberAccountService memberAccountService;

    @DeleteMapping
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        memberAccountService.withdrawTeacher(user.memberId());
        return ApiResponse.successEmpty();
    }
}
