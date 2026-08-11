package com.cenedu.backend.domain.member.controller;

import java.util.List;

import com.cenedu.backend.domain.member.dto.request.ClassEnrollmentCreateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassCreateRequest;
import com.cenedu.backend.domain.member.dto.response.ClassEnrollmentResponse;
import com.cenedu.backend.domain.member.dto.response.SchoolClassResponse;
import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 교사의 반 생성과 학생 배정 관리 API. */
@RestController
@RequestMapping("/api/teacher/classes")
@RequiredArgsConstructor
public class SchoolClassController {

    private final SchoolClassService schoolClassService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SchoolClassResponse> createClass(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SchoolClassCreateRequest request
    ) {
        return ApiResponse.success(schoolClassService.createClass(user.memberId(), request));
    }

    @GetMapping
    public ApiResponse<List<SchoolClassResponse>> getClasses(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(schoolClassService.getClasses(user.memberId()));
    }

    @PostMapping("/{classId}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ClassEnrollmentResponse> enrollStudent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long classId,
            @Valid @RequestBody ClassEnrollmentCreateRequest request
    ) {
        return ApiResponse.success(
                schoolClassService.enrollStudent(user.memberId(), classId, request));
    }

    @GetMapping("/{classId}/students")
    public ApiResponse<List<ClassEnrollmentResponse>> getEnrolledStudents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long classId
    ) {
        return ApiResponse.success(
                schoolClassService.getEnrolledStudents(user.memberId(), classId));
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    public ApiResponse<Void> removeStudent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long classId,
            @PathVariable long studentId
    ) {
        schoolClassService.removeStudent(user.memberId(), classId, studentId);
        return ApiResponse.successEmpty();
    }
}
