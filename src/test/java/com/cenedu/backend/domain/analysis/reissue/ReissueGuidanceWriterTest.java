package com.cenedu.backend.domain.analysis.reissue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.CutoffRule;
import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;
import com.cenedu.backend.domain.analysis.service.DifficultyLadder;
import com.cenedu.backend.global.common.enums.EvaluationArea;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 교사가 그대로 읽는 문장이라 문구를 고정한다. 값이 아니라 문장이 계약이다. */
class ReissueGuidanceWriterTest {

    @Test
    @DisplayName("직전 회차 유지 판정은 정답률과 이어갈 난이도를 함께 말한다")
    void describesWatchJudgement() {
        Adaptive adaptive = Adaptive.fromJudgement(
                DifficultyLadder.MID, 5, 3, MasteryStatus.WATCH, CutoffRule.RATIO,
                new BigDecimal("60.00"), DifficultyLadder.MID, false);

        ReissueProposalResponse.Guidance guidance = ReissueGuidanceWriter.write(
                adaptive, review(1), similar(5, "mid"),
                advanced(false, 0, 8, EvaluationArea.CALCULATION, DiagnosticStage.EXECUTE), true);

        assertThat(guidance.status())
                .isEqualTo("유사 5문항 중 3개 정답(60%)으로 유지 판정, 중 난이도를 이어갑니다.");
        assertThat(guidance.plan())
                .isEqualTo("동일 1문항(최근 오답), 유사 5문항(중). 응용은 상 난이도가 아니라 내지 않습니다.");
        assertThat(guidance.weakness()).isEqualTo("계산 영역·실행 단계가 약합니다.");
    }

    @Test
    @DisplayName("승급·강등은 어느 난이도에서 어디로 움직였는지 밝힌다")
    void describesDifficultyMove() {
        Adaptive promoted = Adaptive.fromJudgement(
                DifficultyLadder.MID, 5, 5, MasteryStatus.CLEAR, CutoffRule.RATIO,
                new BigDecimal("100.00"), DifficultyLadder.HIGH, false);
        Adaptive demoted = Adaptive.fromJudgement(
                DifficultyLadder.MID, 5, 1, MasteryStatus.NEEDS_SUPPORT, CutoffRule.RATIO,
                new BigDecimal("20.00"), DifficultyLadder.LOW, false);

        assertThat(write(promoted).status())
                .isEqualTo("유사 5문항 중 5개 정답(100%)으로 통과 판정, 중 난이도에서 상 난이도로 올립니다.");
        assertThat(write(demoted).status())
                .isEqualTo("유사 5문항 중 1개 정답(20%)으로 지원 필요 판정, 중 난이도에서 하 난이도로 내립니다.");
    }

    @Test
    @DisplayName("상 난이도에서 통과하면 사다리 끝이라 난이도는 그대로지만 응용이 열린다")
    void describesAdvancedTrigger() {
        Adaptive adaptive = Adaptive.fromJudgement(
                DifficultyLadder.HIGH, 5, 5, MasteryStatus.CLEAR, CutoffRule.RATIO,
                new BigDecimal("100.00"), DifficultyLadder.HIGH, true);

        ReissueProposalResponse.Guidance guidance = ReissueGuidanceWriter.write(
                adaptive, review(1), similar(5, "high"),
                advanced(true, 10, 8, EvaluationArea.REASONING, null), true);

        assertThat(guidance.status())
                .isEqualTo("유사 5문항 중 5개 정답(100%)으로 통과 판정, 상 난이도를 이어갑니다.");
        assertThat(guidance.plan()).endsWith(
                "응용은 상 난이도를 통과해 최대 10문항까지 낼 수 있습니다(기본 0문항).");
        assertThat(guidance.weakness()).isEqualTo("추론 영역이 약합니다.");
    }

    @Test
    @DisplayName("진단 평가로 정해진 난이도는 혼합인지 단일인지 구분해 말한다")
    void describesPlacement() {
        Adaptive mixed = Adaptive.fromPlacement(
                DifficultyLadder.MID, new BigDecimal("55.00"), true, null);
        Adaptive singleBand = Adaptive.fromPlacement(
                DifficultyLadder.HIGH, new BigDecimal("80.00"), false, DifficultyLadder.MID);

        assertThat(write(mixed).status())
                .isEqualTo("진단 평가 가중 성취도 55%로 중 난이도에서 시작합니다.");
        assertThat(write(singleBand).status())
                .isEqualTo("중 난이도만 출제된 진단에서 정답률 80%로 상 난이도에서 시작합니다.");
    }

    @Test
    @DisplayName("동일 문항이 0개면 왜 0인지 밝힌다 — 빈 칸은 고장으로 읽힌다")
    void explainsEmptyReview() {
        ReissueProposalResponse.Guidance guidance = ReissueGuidanceWriter.write(
                Adaptive.fromPlacement(DifficultyLadder.LOW, new BigDecimal("30.00"), true, null),
                review(0), similar(5, "low"), advanced(false, 0, 0, null, null), true);

        assertThat(guidance.plan()).startsWith("동일 문항은 다시 낼 오답이 없어 건너뜁니다,");
    }

    @Test
    @DisplayName("대표값이 없을 때 취약점 없음과 자료 부족을 구분한다")
    void separatesNoWeaknessFromNoData() {
        Adaptive adaptive = Adaptive.fromPlacement(
                DifficultyLadder.MID, new BigDecimal("60.00"), true, null);

        assertThat(ReissueGuidanceWriter.write(adaptive, review(1), similar(5, "mid"),
                advanced(false, 0, 0, null, null), true).weakness())
                .isEqualTo("누적 오답이 없어 특정할 취약점이 없습니다.");
        assertThat(ReissueGuidanceWriter.write(adaptive, review(1), similar(5, "mid"),
                advanced(false, 0, 12, null, null), false).weakness())
                .isEqualTo("오답 12건 중 분류된 문항이 적어 취약 영역을 특정하지 못했습니다.");
        assertThat(ReissueGuidanceWriter.write(adaptive, review(1), similar(5, "mid"),
                advanced(false, 0, 12, null, null), true).weakness())
                .isEqualTo("오답 12건이 특정 영역에 몰리지 않아 취약 영역을 특정하지 못했습니다.");
    }

    @Test
    @DisplayName("채점된 문항이 아예 없으면 근거 없이 시작한다는 것을 밝힌다")
    void describesMissingEvidence() {
        assertThat(write(Adaptive.unknown(DifficultyLadder.MID)).status())
                .isEqualTo("채점된 문항이 없어 중 난이도에서 시작합니다.");
    }

    private ReissueProposalResponse.Guidance write(Adaptive adaptive) {
        return ReissueGuidanceWriter.write(adaptive, review(1), similar(5, "mid"),
                advanced(false, 0, 0, null, null), true);
    }

    private ReissueProposalResponse.ReviewProposal review(int count) {
        return new ReissueProposalResponse.ReviewProposal(count, 4, List.of(1L));
    }

    private ReissueProposalResponse.SimilarProposal similar(int count, String difficulty) {
        return new ReissueProposalResponse.SimilarProposal(
                count, 10, difficulty, List.of(), List.of());
    }

    private ReissueProposalResponse.AdvancedProposal advanced(
            boolean triggered, int maxCount, int historicalIncorrect,
            EvaluationArea area, DiagnosticStage stage
    ) {
        return new ReissueProposalResponse.AdvancedProposal(
                triggered, 0, maxCount, historicalIncorrect, 0, area, stage,
                List.of(), List.of());
    }
}
