package com.cenedu.backend.domain.worksheet.controller;

import com.cenedu.backend.domain.worksheet.dto.request.WorksheetAssignmentCreateRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetCreateRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetListRequest;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetAssignmentCreateResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetCreateResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetDetailResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetGenSpecPrefillResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetListResponse;
import com.cenedu.backend.domain.worksheet.service.WorksheetCommandService;
import com.cenedu.backend.domain.worksheet.service.WorksheetQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 교사의 문제 보관함(학습지) 관리 API. */
@RestController
@RequestMapping("/api/teacher/worksheets")
@RequiredArgsConstructor
@Tag(name = "교사 - 문제 보관함", description = "학습지를 저장·조회·배포·삭제하는 API")
public class WorksheetController {

    private final WorksheetCommandService worksheetCommandService;
    private final WorksheetQueryService worksheetQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "학습지 저장", description = """
            문제 생성 결과를 보관함에 확정 저장한다.
            이 시점부터 학습지는 불변이며, 종합평가 배점은 서버가 균등 배분한다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "검증 실패", content = @Content)
    })
    public ApiResponse<WorksheetCreateResponse> createWorksheet(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody WorksheetCreateRequest request
    ) {
        return ApiResponse.success(
                worksheetCommandService.createWorksheet(user.memberId(), request));
    }

    @GetMapping
    @Operation(summary = "문제 보관함 목록", description = "탭·학년·학기·검색어로 필터링한 학습지 목록을 최신순으로 반환한다.")
    public ApiResponse<WorksheetListResponse> getWorksheets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute WorksheetListRequest request
    ) {
        return ApiResponse.success(worksheetQueryService.getWorksheets(user.memberId(), request));
    }

    @GetMapping("/{worksheetId}")
    @Operation(summary = "학습지 상세", description = "문항 본문·정답·해설과 출제 이력을 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "내 학습지가 아니거나 삭제됨", content = @Content)
    })
    public ApiResponse<WorksheetDetailResponse> getWorksheetDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long worksheetId
    ) {
        return ApiResponse.success(
                worksheetQueryService.getWorksheetDetail(user.memberId(), worksheetId));
    }

    @GetMapping("/{worksheetId}/gen-spec")
    @Operation(summary = "복제 프리필", description = "학습지 생성 화면에 채울 출제 조건을 반환한다.")
    public ApiResponse<WorksheetGenSpecPrefillResponse> getGenSpecPrefill(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long worksheetId
    ) {
        return ApiResponse.success(
                worksheetQueryService.getGenSpecPrefill(user.memberId(), worksheetId));
    }

    @PostMapping("/{worksheetId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "학습지 배포", description = "학습지를 반에 배포하고 반 학생 전원을 배정한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "배포 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "학습지 또는 반이 없거나 내 것이 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 배포된 반", content = @Content)
    })
    public ApiResponse<WorksheetAssignmentCreateResponse> assignWorksheet(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long worksheetId,
            @Valid @RequestBody WorksheetAssignmentCreateRequest request
    ) {
        return ApiResponse.success(worksheetCommandService.assignWorksheet(
                user.memberId(), worksheetId, request));
    }

    @DeleteMapping("/{worksheetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "학습지 삭제", description = "학습지를 소프트 삭제한다. 배포된 학습지는 삭제할 수 없다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "배포된 학습지", content = @Content)
    })
    public void deleteWorksheet(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long worksheetId
    ) {
        worksheetCommandService.deleteWorksheet(user.memberId(), worksheetId);
    }
}
