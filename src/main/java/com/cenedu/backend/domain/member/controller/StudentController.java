package com.cenedu.backend.domain.member.controller;

import com.cenedu.backend.domain.member.dto.request.StudentCreateRequest;
import com.cenedu.backend.domain.member.dto.request.StudentListRequest;
import com.cenedu.backend.domain.member.dto.response.StudentBulkCreateResponse;
import com.cenedu.backend.domain.member.dto.response.StudentCreateResponse;
import com.cenedu.backend.domain.member.dto.response.StudentDetailResponse;
import com.cenedu.backend.domain.member.dto.response.StudentListResponse;
import com.cenedu.backend.domain.member.service.StudentListQueryService;
import com.cenedu.backend.domain.member.service.StudentService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/{studentId}")
    public ApiResponse<StudentDetailResponse> getStudentDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(
                studentListQueryService.getStudentDetail(user.memberId(), studentId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StudentCreateResponse> createStudent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StudentCreateRequest request
    ) {
        return ApiResponse.success(studentService.createStudent(user.memberId(), request));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StudentBulkCreateResponse> createStudentsBulk(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ApiResponse.success(studentService.createStudentsBulk(user.memberId(), file));
    }

    @PatchMapping("/{studentId}/password/reset")
    public ApiResponse<Void> resetStudentPassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long studentId
    ) {
        studentService.resetStudentPassword(user.memberId(), studentId);
        return ApiResponse.successEmpty();
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
