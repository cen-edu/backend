package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.analysis.reissue.ReissueProposalService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 맞춤 문제 재출제 제안 조회 API. */
@RestController
@RequestMapping("/api/teacher/analysis/assignments/{assignmentId}/students/{studentId}")
@RequiredArgsConstructor
public class ReissueProposalController {

    private final ReissueProposalService proposalService;

    @Operation(summary = "맞춤 문제 재출제 제안 조회",
            description = """
                    학생의 소단원별 동일·유사 문항 수와 목표 난이도, 응용 문항 생성에 넣을 누적
                    취약 분포를 반환한다. 조회 전용이라 여러 번 불러도 결과가 달라지지 않는다.

                    난이도는 직전 맞춤 회차의 유사 문항 결과로 정하고, 맞춤 회차가 아직 없으면
                    원본 배정으로 영점 조절을 한다.
                    """)
    @GetMapping("/reissue-proposal")
    public ApiResponse<ReissueProposalResponse> getProposal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "취약점의 원본 학습지 배정 ID. 맞춤 회차가 쌓여도 바뀌지 않는다")
            @PathVariable long assignmentId,
            @Parameter(description = "재출제 대상 학생 ID")
            @PathVariable long studentId
    ) {
        return ApiResponse.success(
                proposalService.getProposal(user.memberId(), assignmentId, studentId));
    }
}
