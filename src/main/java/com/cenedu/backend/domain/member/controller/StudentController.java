package com.cenedu.backend.domain.member.controller;

import com.cenedu.backend.domain.member.dto.request.StudentCreateRequest;
import com.cenedu.backend.domain.member.dto.request.StudentListRequest;
import com.cenedu.backend.domain.member.dto.response.StudentCreateResponse;
import com.cenedu.backend.domain.member.dto.response.StudentListResponse;
import com.cenedu.backend.domain.member.service.StudentListQueryService;
import com.cenedu.backend.domain.member.service.StudentService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

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

/** 교사의 학생 계정 관리 API. */
@RestController
@RequestMapping("/api/teacher/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final StudentListQueryService studentListQueryService;

    @GetMapping
    public ApiResponse<StudentListResponse> getStudents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute StudentListRequest request
    ) {
        return ApiResponse.success(studentListQueryService.getStudents(user.memberId(), request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StudentCreateResponse> createStudent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StudentCreateRequest request
    ) {
        return ApiResponse.success(studentService.createStudent(user.memberId(), request));
    }

    @DeleteMapping("/{studentId}")
    public ApiResponse<Void> deleteStudent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long studentId
    ) {
        studentService.deleteStudent(user.memberId(), studentId);
        return ApiResponse.successEmpty();
    }
}
