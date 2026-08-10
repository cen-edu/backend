package com.cenedu.backend.domain.analysis.reissue;

import com.cenedu.backend.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다음 회차에 낼 문항을 돌려준다.
 *
 * <p>저장된 응답만 읽고 LLM을 부르지 않는다. 보고서와 같은 상태값에서 갈라지지만 서로 독립이라
 * 보고서 생성을 기다리지 않는다.
 *
 * <p>같은 분석 도메인의 {@code /api/assessments} 쪽과 달리 여기는 AGENTS.md 2절의
 * {@code /api/teacher/analysis} 접두어와 7절의 {@code ApiResponse} 래핑을 따른다. 그쪽은 프론트
 * 계약이 이미 굳어 있어 못 따랐지만, 재출제는 아직 붙은 화면이 없어 규약대로 시작할 수 있다.
 */
@Tag(name = "재출제", description = "취약점 판정에서 갈라져 나오는 다음 회차 출제 계획")
@RestController
@RequestMapping("/api/teacher/analysis/reissue")
public class ReissueController {

    private final ReissueService reissue;
    private final ReissueProposalService proposals;

    public ReissueController(ReissueService reissue, ReissueProposalService proposals) {
        this.reissue = reissue;
        this.proposals = proposals;
    }

    @Operation(summary = "맞춤 문제 구성 제안",
            description = """
                    개념마다 세 칸(`retrace` 복습 / `basic` 유사 / `independent` 응용)의 문항 수를
                    **서버가 정해서** 돌려준다. 교사는 이 숫자만 조정하고, 조정된 값으로
                    `POST .../questions` 를 부른다.

                    - `복습`은 시스템 오류로 답이 기록되지 않은 문항의 원본 재출제다. 그런 문항이
                      없으면 0이다. 답을 쓴 문항을 다시 내면 실력이 아니라 기억을 잰다
                    - `유사`는 오류가 관찰된 개념에만. 난이도는 이전 문제지의 체류 난이도에서
                      상태에 따라 한 칸 오르내린다
                    - `응용`은 상 난이도까지 올라와 오류가 없을 때만. **생성이 미구현이라
                      `available` 이 항상 0이다**

                    `reason` 은 세 칸을 아우르는 설명 하나다. 0인 칸도 왜 0인지 적는다.
                    """)
    @GetMapping("/assessments/{assessmentId}/students/{studentId}/proposal")
    public ApiResponse<ProposalResponse> propose(
            @Parameter(description = "회차 ID") @PathVariable String assessmentId,
            @Parameter(description = "학생 ID") @PathVariable String studentId
    ) {
        return ApiResponse.success(
                ProposalResponse.from(proposals.propose(assessmentId, studentId)));
    }

    @Operation(summary = "조정된 구성으로 문항 생성",
            description = """
                    제안을 교사가 조정한 값으로 실제 문항을 고른다. 칸당 최대 5문항이며 서버도
                    같은 상한을 건다.

                    `복습`은 뱅크를 보지 않고 기록되지 않은 원본 문항을 그대로 돌려준다.
                    `응용`은 생성이 미구현이라 문항이 나가지 않고 `pendingAppliedCount` 로만
                    알린다.
                    """)
    @PostMapping("/assessments/{assessmentId}/students/{studentId}/questions")
    public ApiResponse<ReissueProposalService.Generated> generate(
            @Parameter(description = "회차 ID") @PathVariable String assessmentId,
            @Parameter(description = "학생 ID") @PathVariable String studentId,
            @Valid @RequestBody GenerateRequest request
    ) {
        return ApiResponse.success(
                proposals.generate(assessmentId, studentId, request.toRequests()));
    }

    @Operation(summary = "다음 회차 출제 계획",
            description = """
                    한 학생의 한 회차를 읽어 다음에 낼 문항을 정한다.

                    모드는 셋이다.
                    - `SAME` 동일 — 시스템 오류로 답이 기록되지 않은 문항이 있을 때. 가장 먼저 걸린다
                    - `SIMILAR` 유사 — 오류가 관찰된 단계. 뱅크에서 3문항을 고른다
                    - `APPLIED` 응용 — 상 난이도까지 올라와 오류가 없을 때. **아직 생성이 미구현이라
                      문항 없이 안내 문구만 나간다**

                    난이도는 이전 문제지에서 읽는다(`dwellDifficultyBand`). 저장하지 않는다.
                    상태에 따라 한 칸 올리거나 내려서 `difficultyBand`가 된다.

                    뱅크 파일이 없으면 500과 함께 경로를 알려 준다.
                    """)
    @GetMapping("/assessments/{assessmentId}/students/{studentId}")
    public ApiResponse<ReissuePlanResponse> plan(
            @Parameter(description = "회차 ID") @PathVariable String assessmentId,
            @Parameter(description = "학생 ID") @PathVariable String studentId
    ) {
        return ApiResponse.success(
                ReissuePlanResponse.from(reissue.plan(assessmentId, studentId)));
    }
}
