package com.cenedu.backend.domain.member.controller;

import java.util.List;

import com.cenedu.backend.domain.member.dto.request.ClassStudentCandidateListRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassCreateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassListRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassOrderUpdateRequest;
import com.cenedu.backend.domain.member.dto.request.SchoolClassUpdateRequest;
import com.cenedu.backend.domain.member.dto.response.ClassStudentCandidateResponse;
import com.cenedu.backend.domain.member.dto.response.SchoolClassDetailResponse;
import com.cenedu.backend.domain.member.dto.response.SchoolClassResponse;
import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute SchoolClassListRequest request
    ) {
        return ApiResponse.success(schoolClassService.getClasses(user.memberId(), request));
    }

    @GetMapping("/available-students")
    public ApiResponse<List<ClassStudentCandidateResponse>> getAvailableStudents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute ClassStudentCandidateListRequest request
    ) {
        return ApiResponse.success(
                schoolClassService.getClassStudentCandidates(user.memberId(), request));
    }

    @GetMapping("/{classId}")
    public ApiResponse<SchoolClassDetailResponse> getClassDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long classId
    ) {
        return ApiResponse.success(
                schoolClassService.getClassDetail(user.memberId(), classId));
    }

    @PatchMapping("/{classId}")
    public ApiResponse<SchoolClassDetailResponse> updateClass(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long classId,
            @Valid @RequestBody SchoolClassUpdateRequest request
    ) {
        return ApiResponse.success(
                schoolClassService.updateClass(user.memberId(), classId, request));
    }

    @PatchMapping("/order")
    public ApiResponse<Void> updateClassOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SchoolClassOrderUpdateRequest request
    ) {
        schoolClassService.updateClassOrder(user.memberId(), request);
        return ApiResponse.successEmpty();
    }

}
