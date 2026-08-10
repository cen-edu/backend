package com.cenedu.backend.domain.analysis.reissue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.global.common.enums.DisplayLabels;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 맞춤 문제 화면의 "문항 구성" 표와 "제안 근거" 카드에 그대로 들어가는 모양.
 *
 * <p>소비자가 둘이다. <b>화면</b>은 한글 이름과 문장을 쓰고, <b>문제 생성</b>은 코드값을 쓴다.
 * 그래서 난이도·평가 영역이 코드와 한글 두 벌로 나간다. 중복이 아니라 각각 쓰는 곳이 다르다.
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
        String reason
) {

    public static ProposalResponse from(ReissueProposalService.Proposal proposal) {
        return new ProposalResponse(
                proposal.studentId(),
                proposal.configs().stream().map(ConfigResponse::from).toList(),
                proposal.reason());
    }

    @Schema(description = "표의 행 하나 — 개념 하나")
    public record ConfigResponse(

            @Schema(description = "생성 요청에 그대로 돌려보낼 키")
            String conceptId,
            @Schema(description = "표의 '취약 개념' 열. 소단원명보다 좁다", example = "최대공약수")
            String conceptLabel,
            @Schema(description = "문항을 고를 소단원. 생성에 쓴다",
                    example = "최대공약수와 최소공배수")
            String bankUnit,

            @Schema(description = "교사에게 보이는 한글 상태명", example = "집중 지도 필요")
            String statusName,

            @Schema(description = "이전 문제지에서 머물던 난이도 코드")
            String dwellDifficultyBand,
            String dwellDifficultyName,
            @Schema(description = "유사·응용을 만들 난이도 코드. 승급·강등이 반영된 값")
            String difficultyBand,
            String difficultyName,

            @Schema(description = "관찰된 평가 영역 코드. 선정에는 쓰지 않고 응용 생성의 참고 자료다",
                    example = "calculation")
            String evaluationArea,
            String evaluationAreaName,
            @Schema(description = "처음 틀린 풀이 구간 코드. 쓰임은 evaluationArea 와 같다",
                    example = "MODEL")
            String targetStage,

            @Schema(description = "이 개념에서 틀린 문항 번호", example = "[2, 3]")
            List<Integer> sourceQuestionNos,
            @Schema(description = "틀린 구간과 학생이 실제로 쓴 답. 제안 근거 카드의 내용")
            List<IncorrectStepResponse> incorrectSteps,
            @Schema(description = "시스템 오류로 기록되지 않은 문항 ID. 복습 칸의 근거다")
            List<String> lostProblemIds,

            @Schema(description = "칸별 제안 문항 수. 키는 retrace / basic / independent")
            Map<String, Integer> counts,
            @Schema(description = "유사 칸에서 실제 고를 수 있는 문항 수. 0이면 재고가 없다")
            int availableBasic
    ) {
        static ConfigResponse from(ReissueProposalService.Config config) {
            ConceptFocus focus = config.focus();
            return new ConfigResponse(
                    focus.conceptId(), focus.conceptName(), focus.bankUnit(),
                    statusName(focus),
                    focus.dwell().band(), DisplayLabels.difficulty(focus.dwell().band()),
                    focus.nextDifficulty().band(),
                    DisplayLabels.difficulty(focus.nextDifficulty().band()),
                    focus.evaluationArea(),
                    focus.evaluationArea() == null
                            ? null : DisplayLabels.area(focus.evaluationArea()),
                    focus.targetStage(),
                    focus.sourceQuestionNos(),
                    focus.incorrectSteps().stream().map(IncorrectStepResponse::from).toList(),
                    focus.lostProblemIds(),
                    byCode(config.counts()),
                    config.available().getOrDefault(ReissueStage.BASIC, 0));
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

    @Schema(description = "틀린 구간 하나")
    public record IncorrectStepResponse(
            @Schema(description = "문항 번호", example = "2")
            int questionNo,
            @Schema(description = "그 문항 안에서 몇 번째 구간인지", example = "1")
            int stepOrder,
            @Schema(description = "구간 이름", example = "공약수 찾기")
            String label,
            @Schema(description = "학생이 실제로 쓴 답. 비어 있으면 미입력", example = "2×6")
            String input
    ) {
        static IncorrectStepResponse from(ConceptFocus.IncorrectStep step) {
            return new IncorrectStepResponse(
                    step.questionNo(), step.stepOrder(), step.label(), step.input());
        }
    }
}
