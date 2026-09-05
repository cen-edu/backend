package com.cenedu.backend.domain.chat.controller;

import com.cenedu.backend.domain.chat.dto.request.ReviewChatRequest;
import com.cenedu.backend.domain.chat.dto.response.ChatResponse;
import com.cenedu.backend.domain.chat.service.ReviewChatService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/student/assignments/{assignmentStudentId}/result/items/{worksheetItemId}/chat")
@Tag(name = "해설 챗봇", description = "공개된 본인 문항의 해설 질문")
public class ReviewChatController {
    private final ReviewChatService service;

    @PostMapping
    @Operation(summary = "해설 질문", description = "본인 배정·결과 공개·문항 소속을 검사한 뒤 해설 정책을 적용합니다.")
    public ApiResponse<ChatResponse> answer(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentStudentId, @PathVariable long worksheetItemId,
            @Valid @RequestBody ReviewChatRequest request) {
        return ApiResponse.success(service.answer(user.memberId(), user.role(), assignmentStudentId, worksheetItemId, request));
    }
}
