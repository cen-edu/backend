package com.cenedu.backend.domain.member.controller;

import com.cenedu.backend.domain.member.dto.response.AcademicContextResponse;
import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 여러 교사 화면이 공유하는 학급 필터 옵션 API. */
@RestController
@RequestMapping("/api/teacher/academic-contexts")
@RequiredArgsConstructor
public class AcademicContextController {

    private final SchoolClassService schoolClassService;

    @GetMapping
    @Operation(summary = "공통 학급 필터 옵션", description = """
            로그인 교사가 담당하는 활성 반을 학년도·학년·반 계층으로 반환한다.
            학기는 반과 무관한 독립 옵션이며, 기본 학년도는 현재 연도 또는 가장 최근 담당 학년도다.
            """)
    public ApiResponse<AcademicContextResponse> getAcademicContexts(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(schoolClassService.getAcademicContexts(user.memberId()));
    }
}
