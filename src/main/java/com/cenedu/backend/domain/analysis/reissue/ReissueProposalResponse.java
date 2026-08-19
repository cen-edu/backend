package com.cenedu.backend.domain.analysis.reissue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;
import com.cenedu.backend.global.common.enums.EvaluationArea;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 맞춤 문제 재출제 제안.
 *
 * <p>소비자는 문제 생성 담당이다. 동일·유사 문항을 몇 개씩 어느 난이도로 뽑을지와, 응용 문항을
 * LLM 으로 만들 때 넣을 누적 취약 분포를 담는다. 문항 조립·학습지 저장·LLM 호출은 이 응답을 받는
 * 쪽의 일이고, 분석 도메인은 여기까지만 한다.
 *
 * <p>난이도 코드는 AGENTS.md 3절 3번에 따라 프론트 {@code labels.js} 와 같은 소문자를 쓴다.
 * 단계 키는 {@code CustomStage} 와 같은 이름({@code review} / {@code similar} / {@code advanced})
 * 으로 맞춰 매핑 축이 늘지 않게 한다.
 */
@Schema(description = "학생 한 명의 소단원별 재출제 제안")
public record ReissueProposalResponse(

        @Schema(description = "원본 배정이 다룬 소단원. 교육과정 순서다")
        List<SubUnitProposal> subcategories
) {

    /** 소단원 하나의 제안. */
    @Schema(description = "소단원 하나의 세 단계 제안")
    public record SubUnitProposal(

            long subUnitId,

            @Schema(description = "화면·로그·LLM 프롬프트에 쓰는 소단원명", example = "소인수분해")
            String subUnitName,

            AdaptiveState adaptive,
            ReviewProposal review,
            SimilarProposal similar,
            AdvancedProposal advanced
    ) {
    }

    /**
     * 이 소단원에서 학생이 지금 서 있는 난이도와 그 근거.
     *
     * <p>난이도 조절은 <b>직전 회차 하나만</b> 본다. 사슬을 뿌리까지 거슬러 올라가 접지 않는다.
     *
     * @param source          {@code placement} 원본 배정의 영점 조절 /
     *                        {@code judgement} 직전 맞춤 회차의 상태 판정 /
     *                        {@code default} 둘 다 없어 서버 기본값
     * @param placementRate   영점 조절의 가중 성취도(%). {@code source} 가 {@code placement} 가
     *                        아니면 참고값이다
     * @param placementMixed  진단지가 두 가지 이상 난이도로 출제됐는지. 거짓이면 절대 컷오프가
     *                        아니라 출제된 난이도 기준의 상대 조정으로 나온 값이다
     * @param lastStatus      직전 회차의 상태 판정. 맞춤 회차가 없으면 {@code null}
     */
    @Schema(description = "현재 난이도와 그 근거")
    public record AdaptiveState(

            @Schema(description = "유사 문항을 뽑을 난이도", example = "mid",
                    allowableValues = {"low", "mid", "high"})
            String currentDifficulty,

            @Schema(example = "placement",
                    allowableValues = {"placement", "judgement", "default"})
            String source,

            @Schema(example = "55.00")
            BigDecimal placementRate,

            Boolean placementMixed,

            @Schema(description = "원본에서 파생된 맞춤 회차 수")
            int customSessionCount,

            MasteryStatus lastStatus
    ) {
    }

    /**
     * 동일 문항 제안.
     *
     * <p>{@code candidateQuestionIds} 는 <b>우선순위 순 후보 목록</b>이지 확정된 선택이 아니다.
     * 교사가 수를 올릴 수 있으므로 상한까지 담아 보내고, 앞에서부터 필요한 만큼 취한다.
     *
     * <p>맞춤 학습지는 {@code GENERAL_LEARNING} 이라 전 문항이 {@code STEP_FILL} 이어야 저장이
     * 통과한다. 후보는 그 조건을 만족하는 문항만 담는다.
     */
    @Schema(description = "동일 문항 — 틀렸던 문항을 그대로 다시 낸다")
    public record ReviewProposal(

            @Schema(description = "서버가 제안하는 문항 수", example = "1")
            int proposedCount,

            @Schema(description = "교사가 올릴 수 있는 상한. 후보가 모자라면 후보 수만큼이다")
            int maxCount,

            @Schema(description = "최근 오답 순 후보. 앞에서부터 쓴다")
            List<Long> candidateQuestionIds
    ) {
    }

    /**
     * 유사 문항 제안.
     *
     * <p>{@code referenceQuestions} 는 유사도 계산의 기준일 뿐 출제 후보가 아니다. 이미
     * {@code excludedQuestionIds} 에 들어 있어 다시 뽑히지 않는다.
     */
    @Schema(description = "유사 문항 — 문제 은행에서 같은 소단원·난이도로 뽑는다")
    public record SimilarProposal(

            @Schema(example = "5")
            int proposedCount,

            @Schema(example = "10")
            int maxCount,

            @Schema(example = "mid", allowableValues = {"low", "mid", "high"})
            String difficulty,

            @Schema(description = "유사도 계산의 기준이 되는 실제 오답 문항")
            List<ReferenceQuestion> referenceQuestions,

            @Schema(description = "학습 흐름에서 이미 받은 문항. 응용 문항도 포함한다")
            List<Long> excludedQuestionIds
    ) {
    }

    /**
     * 유사도 기준 문항 하나.
     *
     * @param incorrectCount  이 학습 흐름에서 이 문항을 틀린 횟수. 반복해서 틀린 문항일수록
     *                        중요한 기준이다
     */
    @Schema(description = "유사도 기준 문항")
    public record ReferenceQuestion(
            long questionId,
            int incorrectCount,
            OffsetDateTime lastIncorrectAt
    ) {
    }

    /**
     * 응용 문항 제안.
     *
     * <p>{@code triggered} 는 발동 조건 충족 여부이고, {@code proposedCount} 는 그와 무관하게
     * 기본 0 이다. 응용은 취약한 학생에게 주는 보충이 아니라 <b>상 난이도를 통과한 학생에게 주는
     * 보너스</b>이며, 기본으로는 내지 않고 교사가 직접 올려야 나간다.
     *
     * <p>{@code historicalIncorrectItemCount} 는 {@code evaluationAreaEvidence} 의 오답 합과
     * 다를 수 있다. {@code problem_question.evaluation_area} 가 nullable 이라 미분류 문항의 오답이
     * 분포 배열에는 잡히지 않기 때문이다. 규모는 이 스칼라로 읽어야 한다.
     */
    @Schema(description = "응용 문항 — LLM 생성에 넣을 누적 취약 분포")
    public record AdvancedProposal(

            @Schema(description = "발동 조건 충족 여부. 상 난이도에서 CLEAR 한 경우에만 참이다")
            boolean triggered,

            @Schema(description = "기본 0. 발동 가능해도 교사가 올려야 나간다", example = "0")
            int proposedCount,

            @Schema(description = "발동하지 않았으면 0")
            int maxCount,

            @Schema(description = "이 소단원 문항을 틀린 누적 횟수. 미분류 문항의 오답도 센다")
            int historicalIncorrectItemCount,

            @Schema(description = "오답이 한 개 이상 난 학습 회차 수")
            int incorrectSessionCount,

            @Schema(description = "우선 참고할 평가 영역. 표본이 부족하면 null")
            EvaluationArea primaryEvaluationArea,

            @Schema(description = "우선 참고할 풀이 단계. 표본이 부족하면 null")
            DiagnosticStage primaryTargetStage,

            List<EvaluationAreaEvidence> evaluationAreaEvidence,
            List<DiagnosticStageEvidence> diagnosticStageEvidence
    ) {
    }

    /**
     * 평가 영역별 문항 단위 분포.
     *
     * @param incorrectRate 채점된 문항이 없으면 {@code null}. 0.0 으로 내보내면 "완벽하게 잘함"
     *                      으로 읽힌다
     */
    @Schema(description = "평가 영역별 문항 단위 채점·오답 분포")
    public record EvaluationAreaEvidence(
            EvaluationArea evaluationArea,
            int gradedItemCount,
            int incorrectItemCount,
            BigDecimal incorrectRate
    ) {
    }

    /**
     * 풀이 단계별 답안 단위 분포.
     *
     * <p>평가 영역은 문항 단위, 이쪽은 답안 단위라 두 배열의 합계는 서로 다르다.
     *
     * @param incorrectRate 채점된 칸이 없으면 {@code null}
     */
    @Schema(description = "풀이 단계별 답안 단위 채점·오답 분포")
    public record DiagnosticStageEvidence(
            DiagnosticStage diagnosticType,
            int gradedUnitCount,
            int incorrectUnitCount,
            BigDecimal incorrectRate
    ) {
    }
}
