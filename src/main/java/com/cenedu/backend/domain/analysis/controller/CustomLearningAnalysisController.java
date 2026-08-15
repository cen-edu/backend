package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.dto.response.CustomLearningSessionListResponse;
import com.cenedu.backend.domain.analysis.service.CustomLearningQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학습평가·종합평가 학생 상세 화면의 맞춤 학습 결과 API. */
@RestController
@RequestMapping("/api/teacher/analysis/assignments/{assignmentId}/students/{studentId}")
@RequiredArgsConstructor
public class CustomLearningAnalysisController {

    private final CustomLearningQueryService queryService;

    @GetMapping("/custom-learning-sessions")
    public ApiResponse<CustomLearningSessionListResponse> getSessions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(queryService.getSessions(
                user.memberId(), assignmentId, studentId));
    }
}
