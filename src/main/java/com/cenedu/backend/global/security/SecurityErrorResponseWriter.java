package com.cenedu.backend.global.security;

import java.io.IOException;

import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.common.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Spring Security 필터 계층의 오류를 공통 API 응답 형식으로 기록한다. */
@Component
@RequiredArgsConstructor
class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(errorCode));
    }
}
