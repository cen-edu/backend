package com.cenedu.backend.domain.analysis.reissue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.analysis.entity.LearningStatus;
import com.cenedu.backend.global.common.enums.DisplayLabels;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 맞춤 문제 화면의 "문항 구성" 표와 "제안 근거" 카드에 그대로 들어가는 모양.
 *
 * <p>표는 행이 개념, 열이 세 칸이다. 서버가 정한 숫자를 교사가 조정하고, 조정된 값으로 생성을
 * 부른다. 칸 키는 프론트의 {@code customStages}와 같은 문자열을 쓴다.
 */
@Schema(description = "개념별 세 칸의 문항 수 제안과 그 이유")
public record ProposalResponse(

        String studentId,

        @Schema(description = "표의 행. 나쁜 상태가 위로 온다")
        List<ConfigResponse> configs,

        @Schema(description = "세 칸을 아우르는 선정 이유. 0인 칸도 왜 0인지 적는다")
        String reason,

        @Schema(description = "모든 칸의 합계")
        int totalCount
) {

    public static ProposalResponse from(ReissueProposalService.Proposal proposal) {
        return new ProposalResponse(
                proposal.studentId(),
                proposal.configs().stream().map(ConfigResponse::from).toList(),
                proposal.reason(),
                proposal.totalCount());
    }

    @Schema(description = "표의 행 하나 — 개념 하나")
    public record ConfigResponse(
            String conceptId,
            @Schema(description = "표에 보이는 이름. 뱅크 소단원명을 쓴다")
            String conceptLabel,
            String bankUnit,

            @Schema(description = "내부 상태 코드. 화면에는 statusName 을 쓸 것")
            LearningStatus status,
            String statusName,

            @Schema(description = "이전 문제지에서 머물던 난이도")
            String dwellDifficultyBand,
            String dwellDifficultyName,
            @Schema(description = "유사 문항을 고를 난이도. 승급·강등이 반영된 값")
            String difficultyBand,
            String difficultyName,

            @Schema(description = "이 개념에서 틀린 문항 ID")
            List<String> wrongProblemIds,
            @Schema(description = "시스템 오류로 기록되지 않은 문항 ID. 복습 칸의 근거다")
            List<String> lostProblemIds,

            @Schema(description = "관찰 정보. 선정 기준이 아니다")
            String evaluationAreaName,
            String targetStage,

            @Schema(description = "칸별 제안 문항 수. 키는 retrace / basic / independent")
            Map<String, Integer> counts,
            @Schema(description = "칸별로 실제 고를 수 있는 문항 수. 제안값보다 적으면 재고 부족")
            Map<String, Integer> available
    ) {
        static ConfigResponse from(ReissueProposalService.Config config) {
            ConceptFocus focus = config.focus();
            return new ConfigResponse(
                    focus.conceptId(), focus.bankUnit(), focus.bankUnit(),
                    focus.state() == null ? null : focus.state().status(),
                    statusName(focus),
                    focus.dwell().band(), DisplayLabels.difficulty(focus.dwell().band()),
                    focus.nextDifficulty().band(),
                    DisplayLabels.difficulty(focus.nextDifficulty().band()),
                    focus.wrongProblemIds(), focus.lostProblemIds(),
                    focus.evaluationArea() == null
                            ? null : DisplayLabels.area(focus.evaluationArea()),
                    focus.targetStage(),
                    byCode(config.counts()), byCode(config.available()));
        }

        private static String statusName(ConceptFocus focus) {
            if (focus.state() == null) {
                return DisplayLabels.status("insufficient");
            }
            String code = switch (focus.state().status()) {
                case CLEAR, IMPROVED -> "stable";
                case WATCH -> "review";
                case NEEDS_SUPPORT -> "priority";
            };
            return DisplayLabels.status(code);
        }

        private static Map<String, Integer> byCode(Map<ReissueStage, Integer> counts) {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (ReissueStage stage : ReissueStage.values()) {
                out.put(stage.code(), counts.getOrDefault(stage, 0));
            }
            return out;
        }
    }
}
