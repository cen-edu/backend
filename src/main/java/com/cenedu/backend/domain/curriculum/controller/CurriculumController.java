package com.cenedu.backend.domain.curriculum.controller;

import java.util.List;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumUnitResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teacher/problems")
public class CurriculumController {

    private final CurriculumUnitQueryService curriculumUnitQueryService;

    @GetMapping("/units")
    public ApiResponse<List<CurriculumUnitResponse>> getUnits(
        @RequestParam short grade,
        @RequestParam short semester,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<CurriculumUnitResponse> response =
            curriculumUnitQueryService.getUnits(grade, semester);

        return ApiResponse.success(response);
    }
}
