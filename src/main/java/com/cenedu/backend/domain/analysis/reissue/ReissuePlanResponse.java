package com.cenedu.backend.domain.analysis.reissue;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.LearningState;
import com.cenedu.backend.global.common.enums.DisplayLabels;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재출제 계획을 화면이 쓰는 모양으로 옮긴다.
 *
 * <p>한글 표기와 설명 문장을 서버가 만들어 보낸다. 화면이 상태 코드로 문장을 지으면 같은 규칙이
 * 두 곳에 생기고, 규칙을 바꿀 때 한쪽만 고쳐진다.
 *
 * <p>내부 코드({@code WATCH} 등)는 교사에게 보이지 않는다.
 */
@Schema(description = "다음 회차에 낼 문항과 그 근거")
public record ReissuePlanResponse(

        String studentId,

        @Schema(description = "SAME 동일 · SIMILAR 유사 · APPLIED 응용")
        ReissueMode mode,
        @Schema(description = "왜 이 모드인지. 교사 화면에 그대로 쓴다")
        String modeReason,

        String conceptId,
        @Schema(description = "뱅크 소단원명")
        String bankUnit,

        @Schema(description = "다음에 낼 난이도. 승급·강등이 반영된 값")
        String difficultyBand,
        String difficultyName,
        @Schema(description = "이전 문제지에서 머물던 난이도. 승급 전 값")
        String dwellDifficultyBand,
        String dwellDifficultyName,
        @Schema(description = "체류 난이도가 어디서 왔고 어디로 움직였는지")
        String difficultyReason,

        @Schema(description = "관찰된 평가 영역. 선정 기준이 아니라 참고 표시")
        String evaluationArea,
        String evaluationAreaName,
        @Schema(description = "처음 틀린 풀이 구간. 관찰되지 않았으면 null")
        String targetStage,
        @Schema(description = "관찰 정보 설명. 선정 근거가 아님을 명시한다")
        String observationNote,

        @Schema(description = "교사에게 보이는 한글 상태명")
        String statusName,
        LearningState state,

        List<CandidateResponse> questions,
        @Schema(description = "하드 필터를 통과한 문항 수. 재고가 마르는지 보려면 이 값을 본다")
        int candidateCount,
        @Schema(description = "어떤 기준으로 골랐는지")
        String selectionReason,
        @Schema(description = "문항을 고르지 못한 모드에서 이유를 담는다")
        String note
) {

    public static ReissuePlanResponse from(ReissueService.Plan plan) {
        return new ReissuePlanResponse(
                plan.studentId(),
                plan.mode(), modeReason(plan.mode()),
                plan.conceptId(), plan.bankUnit(),
                plan.difficulty().band(), DisplayLabels.difficulty(plan.difficulty().band()),
                plan.dwellDifficulty().band(),
                DisplayLabels.difficulty(plan.dwellDifficulty().band()),
                difficultyReason(plan),
                plan.evaluationArea(),
                plan.evaluationArea() == null ? null : DisplayLabels.area(plan.evaluationArea()),
                plan.targetStage(), observationNote(plan),
                statusName(plan.state()), plan.state(),
                plan.questions().stream().map(CandidateResponse::from).toList(),
                plan.candidateCount(), SELECTION_REASON, plan.note());
    }

    private static String modeReason(ReissueMode mode) {
        return switch (mode) {
            case SIMILAR -> "오류가 관찰된 단계라 같은 소단원에서 유사 문항을 고릅니다.";
            case SAME -> "시스템 오류로 답이 기록되지 않아 같은 문항을 다시 냅니다. "
                    + "학생이 틀린 것이 아니므로 이 문항은 판정에 넣지 않았습니다.";
            case APPLIED -> "확인이 필요한 오류가 없어 다른 맥락으로 옮길 수 있는지 봅니다.";
        };
    }

    /** 교사 화면에 그대로 쓰는 한글 상태명. */
    private static String statusName(LearningState state) {
        if (state == null) {
            return null;
        }
        String code = switch (state.status()) {
            case CLEAR, IMPROVED -> "stable";
            case WATCH -> "review";
            case NEEDS_SUPPORT -> "priority";
        };
        return DisplayLabels.status(code);
    }

    private static String difficultyReason(ReissueService.Plan plan) {
        String from = "이전 문제지에서 머문 난이도는 "
                + DisplayLabels.difficulty(plan.dwellDifficulty().band()) + "입니다";
        if (plan.state() == null) {
            return from + ". 기록된 응답이 없어 그대로 둡니다.";
        }
        // 모드를 먼저 본다. 상태로만 문장을 지으면 같은 문항을 다시 내는데 "한 칸 올렸습니다"
        // 처럼 실제 난이도와 어긋난 설명이 나간다.
        if (plan.mode() == ReissueMode.SAME) {
            return from + ". 같은 문항을 다시 내므로 난이도를 움직이지 않습니다.";
        }
        String move = switch (plan.state().status()) {
            case NEEDS_SUPPORT -> "누적 오류로 지원이 필요해 한 칸 낮췄습니다";
            case WATCH -> "오류가 보여 같은 난이도를 유지합니다";
            case CLEAR, IMPROVED -> plan.dwellDifficulty() == QuestionDifficulty.HIGH
                    ? "상에서 오류가 없어 더 올릴 칸이 없습니다"
                    : "오류가 없어 한 칸 올렸습니다";
        };
        return from + ". " + move + ".";
    }

    /** 관찰된 구간 정보. 선정 기준이 아니라 참고 표시다. */
    private static String observationNote(ReissueService.Plan plan) {
        if (plan.state() == null) {
            return "이 회분에서 기록된 응답이 없어 관찰된 것이 없습니다. "
                    + "모든 문항이 시스템 오류로 기록되지 않았습니다.";
        }
        String area = plan.evaluationArea() == null ? "평가 영역은 관찰되지 않았습니다"
                : "오답 문항에서 가장 많이 관찰된 평가 영역은 "
                        + DisplayLabels.area(plan.evaluationArea()) + "입니다";
        String stage = plan.targetStage() == null ? "처음 틀린 구간은 관찰되지 않았습니다"
                : "처음 틀린 구간이 관찰되었습니다";
        return area + ". " + stage + ". 둘 다 참고 정보이고 문항 선정에는 쓰지 않습니다.";
    }

    /**
     * 어떤 기준으로 골랐는지. 분류 축은 쓰지 않는다.
     *
     * <p>메서드가 아니라 상수인 이유는 레코드 접근자 {@code selectionReason()}과 시그니처가
     * 겹치기 때문이다.
     */
    private static final String SELECTION_REASON =
            "같은 소단원과 난이도에서, 이미 낸 문항과 이미지 문항을 빼고 고릅니다. "
                    + "빈칸이 적은 문항을 먼저 냅니다. 평가 영역·소주제·풀이 구간은 "
                    + "선정 기준으로 쓰지 않습니다.";

    @Schema(description = "뽑힌 문항 하나")
    public record CandidateResponse(
            String questionId,
            String prompt,
            String unitName,
            String difficultyBand,
            String difficultyName,
            @Schema(description = "관찰 정보. 선정 기준이 아니다")
            String evaluationArea,
            String evaluationAreaName,
            int blankCount,
            @Schema(description = "겨냥 구간이 몇 번째 빈칸인지. 없으면 -1. 표시 전용")
            int stagePosition,
            String reason
    ) {
        static CandidateResponse from(ReissueService.Candidate candidate) {
            return new CandidateResponse(
                    candidate.questionId(), candidate.prompt(), candidate.unitName(),
                    candidate.difficultyBand(),
                    DisplayLabels.difficulty(candidate.difficultyBand()),
                    candidate.evaluationArea(),
                    candidate.evaluationArea() == null
                            ? null : DisplayLabels.area(candidate.evaluationArea()),
                    candidate.blankCount(), candidate.stagePosition(), candidate.reason());
        }
    }
}
