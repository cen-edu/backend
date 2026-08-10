package com.cenedu.backend.domain.analysis.reissue;

import com.cenedu.backend.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public ReissueController(ReissueService reissue) {
        this.reissue = reissue;
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
