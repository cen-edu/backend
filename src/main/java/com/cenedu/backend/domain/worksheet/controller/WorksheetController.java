package com.cenedu.backend.domain.worksheet.controller;

import com.cenedu.backend.domain.worksheet.dto.request.WorksheetCreateRequest;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetCreateResponse;
import com.cenedu.backend.domain.worksheet.service.WorksheetCommandService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
}
