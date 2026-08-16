package com.cenedu.backend.domain.chat.controller;

import com.cenedu.backend.domain.chat.dto.request.ChatRequest;
import com.cenedu.backend.domain.chat.dto.response.ChatResponse;
import com.cenedu.backend.domain.chat.service.ChatService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생이 문제를 푸는 중에 쓰는 개념 챗봇 API.
 *
 * <p><b>무상태다.</b> 대화 이력을 서버에 저장하지 않고 클라이언트가 매 요청에 실어 보낸다.
 * 그래서 이 API 는 클라이언트가 계약을 지켜야만 동작한다 — {@code history} 가 안 오면 매 턴이
 * 대화의 첫 마디로 보여 하향 탐색이 죽고, {@code currentConceptId} 가 안 돌아오면 한 칸 내려간
 * 앵커가 다음 턴에 원위치한다. 자세한 것은 {@code docs/api/api_chat.md}.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "개념 챗봇", description = "학생이 문제 풀이 중 개념을 묻는 API")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(
            summary = "개념 질문",
            description = """
                    학생 질문에 교육과정 개념 자료로 답한다. 대화 이력은 서버가 저장하지 않으므로
                    클라이언트가 history 로 실어 보낸다.

                    응답의 currentConceptId 는 화면에 쓰는 값이 아니라 다음 요청에 그대로
                    되돌려줄 연속성 토큰이다. 이 값이 왕복하지 않으면 "더 쉽게 설명해 주세요" 가
                    직전에 설명한 개념이 아니라 대화 첫 개념을 기준으로 동작한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "답변 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "질문이 비었거나 너무 길거나, 이력 형식이 잘못됨",
                    content = @Content)
    })
    public ApiResponse<ChatResponse> answer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChatRequest request
    ) {
        return ApiResponse.success(chatService.answer(user.memberId(), user.role(), request));
    }
}
