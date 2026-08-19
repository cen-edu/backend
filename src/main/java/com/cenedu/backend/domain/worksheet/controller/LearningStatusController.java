package com.cenedu.backend.domain.worksheet.controller;

import com.cenedu.backend.domain.worksheet.dto.request.LearningStatusListRequest;
import com.cenedu.backend.domain.worksheet.dto.response.CustomLearningStatusResponse;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusListResponse;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusStudentsResponse;
import com.cenedu.backend.domain.worksheet.service.CustomLearningStatusQueryService;
import com.cenedu.backend.domain.worksheet.service.LearningStatusQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 교사의 학습 현황 API. 배포한 학습지별 학생 진행 상황을 본다. */
@RestController
@RequestMapping("/api/teacher/learning-status")
@RequiredArgsConstructor
@Tag(name = "교사 - 학습 현황", description = "배포한 학습지별 학생 진행 상황을 조회하는 API")
public class LearningStatusController {

    private final LearningStatusQueryService learningStatusQueryService;
    private final CustomLearningStatusQueryService customLearningStatusQueryService;

    @GetMapping
    @Operation(summary = "요약 카드와 학습지 목록", description = """
            내가 배포한 학습지를 배포일 최신순으로 반환한다. 맞춤 배포도 같은 배열에 담기며
            프론트가 origin 으로 갈라 렌더한다.

            요약 세 값(submitted/inProgress/unsubmitted)은 학생 수가 아니라 배정 건수(연인원)다.
            학습지 status 는 컬럼이 아니라 due_at 경과로 파생한다.
            """)
    public ApiResponse<LearningStatusListResponse> getLearningStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute LearningStatusListRequest request
    ) {
        return ApiResponse.success(learningStatusQueryService.getLearningStatus(user.memberId(), request));
    }

    @GetMapping("/{assignmentId}/students")
    @Operation(summary = "학생별 진행 표", description = """
            선택한 배포의 학생 행을 표시 순번과 함께 반환한다. 명단은 배포 시점 기준이라
            배포 후 전학 온 학생은 나오지 않는다.

            displayNumber 는 출석번호가 아니라 이 배포 명단 안의 표시 순번이며,
            상태 필터를 걸어도 같은 학생의 값이 유지된다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배포가 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<LearningStatusStudentsResponse> getStudents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @Parameter(description = "학생 진행 상태 필터. 생략하면 전체")
            @RequestParam(required = false)
            @Schema(allowableValues = {"not-started", "in-progress", "submitted", "not-submitted"})
            String status
    ) {
        return ApiResponse.success(
                learningStatusQueryService.getStudents(user.memberId(), assignmentId, status));
    }

    @GetMapping("/{sourceAssignmentId}/custom-learning")
    @Operation(summary = "맞춤 학습 현황", description = """
            원본 배정에서 파생된 맞춤 학습을 학습지별로 묶어 학생 현황과 함께 반환한다.

            맞춤 배정은 학생 한 명씩이라 카드는 맞춤 학습지 단위로 묶고,
            배정 ID 는 students[].assignmentId 에 있다.

            sessionNumber 는 저장 컬럼이 없어 배정일 오름차순으로 파생하며 1부터 시작한다.
            displayNumber 는 원본 배정 명단 기준이라 1, 4, 6 처럼 띄엄띄엄 나오는 것이 정상이다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배포가 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<CustomLearningStatusResponse> getCustomLearning(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long sourceAssignmentId
    ) {
        return ApiResponse.success(
                customLearningStatusQueryService.getCustomLearning(user.memberId(), sourceAssignmentId));
    }
}
