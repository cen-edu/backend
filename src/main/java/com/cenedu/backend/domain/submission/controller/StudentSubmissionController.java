package com.cenedu.backend.domain.submission.controller;

import com.cenedu.backend.domain.submission.dto.request.StudentAnswerSaveRequest;
import com.cenedu.backend.domain.submission.dto.response.StudentAnswerSaveResponse;
import com.cenedu.backend.domain.submission.dto.response.StudentSubmitResponse;
import com.cenedu.backend.domain.submission.service.StudentSubmissionService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생이 배정받은 학습지의 답안을 저장·제출하는 API. */
@RestController
@RequestMapping("/api/student/assignments")
@RequiredArgsConstructor
@Tag(name = "학생 - 학습지", description = "학생이 배정받은 학습지를 조회·풀이·제출하는 API")
public class StudentSubmissionController {

    private final StudentSubmissionService studentSubmissionService;

    @PutMapping("/{assignmentStudentId}/items/{worksheetItemId}")
    @Operation(
            summary = "문항 답안 저장",
            description = """
                    문항 하나의 답안을 멱등 upsert한다. 같은 칸에 두 번 저장해도 행이 늘지 않는다.
                    문항 이탈 시 1회 호출을 전제한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "이 문항의 채점 칸이 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배정된 학습지가 없거나 본인 배정이 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 제출했거나 제출 기한이 지남", content = @Content)
    })
    public ApiResponse<StudentAnswerSaveResponse> saveAnswers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentStudentId,
            @PathVariable long worksheetItemId,
            @Valid @RequestBody StudentAnswerSaveRequest request
    ) {
        return ApiResponse.success(studentSubmissionService.saveAnswers(
                user.memberId(), assignmentStudentId, worksheetItemId, request));
    }

    @PostMapping("/{assignmentStudentId}/submit")
    @Operation(
            summary = "학습지 제출",
            description = """
                    제출을 확정한다. 채점하지 않는다 — 채점은 교사가 트리거한다.
                    미응답 칸이 있어도 제출을 막지 않는다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "제출 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배정된 학습지가 없거나 본인 배정이 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 제출했거나 제출 기한이 지남", content = @Content)
    })
    public ApiResponse<StudentSubmitResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentStudentId
    ) {
        return ApiResponse.success(studentSubmissionService.submit(user.memberId(), assignmentStudentId));
    }
}
