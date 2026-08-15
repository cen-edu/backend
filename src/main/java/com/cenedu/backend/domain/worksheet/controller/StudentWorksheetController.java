package com.cenedu.backend.domain.worksheet.controller;

import com.cenedu.backend.domain.worksheet.dto.response.StudentAssignmentListResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentWorksheetDetailResponse;
import com.cenedu.backend.domain.worksheet.service.StudentResultQueryService;
import com.cenedu.backend.domain.worksheet.service.StudentWorksheetQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생이 배정받은 학습지를 조회·풀이하는 API. */
@RestController
@RequestMapping("/api/student/assignments")
@RequiredArgsConstructor
@Tag(name = "학생 - 학습지", description = "학생이 배정받은 학습지를 조회·풀이·제출하는 API")
public class StudentWorksheetController {

    private final StudentWorksheetQueryService studentWorksheetQueryService;
    private final StudentResultQueryService studentResultQueryService;

    @GetMapping
    @Operation(
            summary = "배정 학습지 목록 조회",
            description = """
                    로그인한 학생에게 배정된 학습지를 모두 반환한다.
                    진행 상태는 DB 4값을 프론트 계약값으로 변환해 내려준다.
                    """
    )
    public ApiResponse<StudentAssignmentListResponse> getAssignments(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(studentWorksheetQueryService.getAssignments(user.memberId()));
    }

    @GetMapping("/{assignmentStudentId}")
    @Operation(
            summary = "학습지 풀이 진입",
            description = """
                    문항 본문·선택지·빈칸·이어 풀기 상태까지 풀이 화면에 필요한 전부를 한 번에 반환한다.
                    본인 배정이 아니면 존재 여부를 노출하지 않기 위해 404를 반환한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배정된 학습지가 없거나 본인 배정이 아님", content = @Content)
    })
    public ApiResponse<StudentWorksheetDetailResponse> getAssignmentDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentStudentId
    ) {
        return ApiResponse.success(
                studentWorksheetQueryService.getAssignmentDetail(user.memberId(), assignmentStudentId));
    }

    @GetMapping("/{assignmentStudentId}/result")
    @Operation(
            summary = "채점 결과 조회",
            description = """
                    점수·정답·해설·(서술형이면) 루브릭 판정을 반환한다.
                    교사가 반 단위 확정을 누르기 전에는 409를 반환한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배정된 학습지가 없거나 본인 배정이 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "아직 채점 결과가 공개되지 않음", content = @Content)
    })
    public ApiResponse<StudentResultResponse> getResult(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentStudentId
    ) {
        return ApiResponse.success(studentResultQueryService.getResult(user.memberId(), assignmentStudentId));
    }
}
