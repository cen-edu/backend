package com.cenedu.backend.domain.auth.controller;

import com.cenedu.backend.domain.auth.dto.request.LoginRequest;
import com.cenedu.backend.domain.auth.dto.request.SignupRequest;
import com.cenedu.backend.domain.auth.dto.response.LoginResponse;
import com.cenedu.backend.domain.auth.dto.response.SignupResponse;
import com.cenedu.backend.domain.auth.service.AuthService;
import com.cenedu.backend.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 가입과 로그인을 제공하는 인증 API. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
