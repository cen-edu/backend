package com.cenedu.backend.domain.grading.controller;

import com.cenedu.backend.domain.grading.dto.request.GradingAnswerPatchRequest;
import com.cenedu.backend.domain.grading.dto.request.GradingAutoRequest;
import com.cenedu.backend.domain.grading.dto.request.GradingListRequest;
import com.cenedu.backend.domain.grading.dto.response.GradingAnswerPatchResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingAutoProgressResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingAutoStartResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingReleaseResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingScoreTableResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingStudentDetailResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingWorksheetListResponse;
import com.cenedu.backend.domain.grading.service.GradingCommandService;
import com.cenedu.backend.domain.grading.service.GradingExecutionService;
import com.cenedu.backend.domain.grading.service.GradingQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 교사의 채점 API. 제출 답안을 검토하고 점수를 확정한다. */
@RestController
@RequestMapping("/api/teacher/grading")
@RequiredArgsConstructor
@Tag(name = "교사 - 채점", description = "제출 답안을 자동채점하고 점수를 검토·확정하는 API")
public class GradingController {

    private final GradingQueryService gradingQueryService;
    private final GradingExecutionService gradingExecutionService;
    private final GradingCommandService gradingCommandService;

    @GetMapping
    @Operation(summary = "평가 결과 목록", description = """
            채점할 학습지 배포 목록을 배포일 최신순으로 반환한다.
            채점 상태(grading/graded/confirmed)는 컬럼이 아니라 서버가 파생한 값이다.

            맞춤 학습은 최상위에 형제로 나오지 않고, 파생된 원본의 customLearning 안에
            학생 → 차수 2단으로 들어간다. 차수는 저장 컬럼이 아니라 학습지 계보의 깊이다.
            그래서 status 필터로 원본이 걸러지면 그 아래 맞춤도 함께 사라지고,
            반 필터를 걸면 맞춤은 필터를 통과한 원본 아래에 그대로 남는다.

            문항별 정오는 여기 없다. 행을 열 때 점수표 API(GET /{assignmentId})를 부르며,
            맞춤은 customLearning...sessions[].assignmentId 를 그 경로에 넣으면 된다.
            """)
    public ApiResponse<GradingWorksheetListResponse> getWorksheets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute GradingListRequest request
    ) {
        return ApiResponse.success(gradingQueryService.getWorksheets(user.memberId(), request));
    }

    @GetMapping("/{assignmentId}")
    @Operation(summary = "점수표", description = """
            학생 × 문항 행렬과 평균·최고·최저를 반환한다.
            제출한 학생만 담기며, 몇 명이 안 냈는지는 studentCount 와 submittedCount 로 알린다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배포가 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<GradingScoreTableResponse> getScoreTable(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(gradingQueryService.getScoreTable(user.memberId(), assignmentId));
    }

    @GetMapping("/{assignmentId}/students/{assignmentStudentId}")
    @Operation(summary = "학생 채점 화면", description = """
            학생 한 명의 문항별 답안·정답·채점 결과를 반환한다.
            발문은 prompt_text 가 아니라 contentBlocks 다.
            필기 이미지 URL 은 만료가 10분이라 화면을 오래 열어두면 다시 조회해야 한다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배정이 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<GradingStudentDetailResponse> getStudentDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long assignmentStudentId
    ) {
        return ApiResponse.success(gradingQueryService.getStudentDetail(
                user.memberId(), assignmentId, assignmentStudentId));
    }

    @PostMapping("/{assignmentId}/auto")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "자동채점 실행", description = """
            규칙 채점과 서술형 채점을 백그라운드에서 시작하고 즉시 응답한다.
            결과 확인은 진행률 조회가 정본이다.

            대상에서 빠지는 칸(skippedCount 로 센다)
            - 교사가 이미 점수를 고친 칸
            - 이미 채점이 끝난 서술형 칸 — 서술형은 LLM 이 한 번만 채점하고 이후 정정은 교사 몫이다

            서술형(RUBRIC)은 학생 필기 이미지를 읽어 채점 기준 항목별로 판정한다.
            점수는 그 문항 가중치의 실제 합을 분모로 환산한다(100 고정이 아니다).

            배점이 없는 학습지(일반·맞춤 학습)의 서술형은 점수 없이 판정만 남기고 GRADED 가 된다.
            즉 gradingStatus 가 GRADED 인데 autoScore·finalScore 가 null 인 칸이 생긴다.

            판정하지 못하면 FAILED 로 남고 failureReason 앞에 코드가 붙는다.
            다시 돌려볼 값이 있는 실패  TURN_LIMIT · MALFORMED · LLM_ERROR
            교사가 직접 채점해야 하는 것 UNJUDGEABLE · NO_RUBRIC · NO_ANSWER · NO_IMAGE
                                        · IMAGE_STORE_UNAVAILABLE
            FAILED 인 칸은 다시 실행하면 대상에 들어간다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "채점 작업이 시작됨. 진행률은 GET .../auto 로 조회"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 실행 중이거나 미제출 학생이 대상에 있음", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배포가 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<GradingAutoStartResponse> startAutoGrading(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @Valid @RequestBody(required = false) GradingAutoRequest request
    ) {
        return ApiResponse.success(gradingExecutionService.start(
                user.memberId(), assignmentId,
                request != null ? request : new GradingAutoRequest(null)));
    }

    @GetMapping("/{assignmentId}/auto")
    @Operation(summary = "자동채점 진행률", description = """
            칸 단위 채점 상태를 센다. running 은 메모리 상태라 서버를 재시작하면 풀린다 —
            running 이 false 인데 remainingCount 가 남아 있으면 끊긴 것이고, 다시 실행하면 이어진다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배포가 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<GradingAutoProgressResponse> getAutoGradingProgress(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(
                gradingExecutionService.getProgress(user.memberId(), assignmentId));
    }

    @PatchMapping("/{assignmentId}/answers/{submissionAnswerId}")
    @Operation(summary = "점수·판정 수정", description = """
            finalScore / rubricChecks / resetToAuto 중 하나만 보낸다.
            rubricChecks 를 보내면 서버가 배점을 합산한다 — 프론트 계산값은 받지 않는다.
            확정 후에도 수정할 수 있고, 그 경우 목록의 modified 가 켜진다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "점수 범위를 벗어났거나 이 문항의 채점 기준이 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배포·답안이 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<GradingAnswerPatchResponse> patchAnswer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long submissionAnswerId,
            @Valid @RequestBody GradingAnswerPatchRequest request
    ) {
        return ApiResponse.success(gradingCommandService.patchAnswer(
                user.memberId(), assignmentId, submissionAnswerId, request));
    }

    @PostMapping("/{assignmentId}/release")
    @Operation(summary = "확정", description = """
            제출한 학생 전원에게 점수·정답·해설을 공개한다. body 는 없다.
            제출자 중 채점이 끝나지 않은 학생이 있으면 거절한다 — 버튼 비활성은 UX 이지 검증이 아니다.
            미제출자는 확정을 막지 않으며 released_at 도 채우지 않는다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "확정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 확정됐거나 미채점이 남음", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배포가 없거나 내 학습지가 아님", content = @Content)
    })
    public ApiResponse<GradingReleaseResponse> release(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(gradingCommandService.release(user.memberId(), assignmentId));
    }
}
