package com.cenedu.backend.domain.analysis.reissue;

import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;
import com.cenedu.backend.domain.analysis.service.DifficultyLadder;
import com.cenedu.backend.global.common.enums.DisplayLabels;
import com.cenedu.backend.global.common.enums.EvaluationArea;

/**
 * 교사 화면에 그대로 실릴 한국어 설명을 만든다.
 *
 * <p>보통 화면 라벨은 프론트가 붙이지만(AGENTS.md 3절 3번), 이 문장은 라벨이 아니라 <b>교사가
 * 읽는 설명</b>이다. 같은 이유로 PDF 보고서도 서버가 끝까지 문장을 만든다
 * ({@code ReportLabels}).
 *
 * <p>LLM 을 쓰지 않는다. 이 API 는 조회 전용이라 같은 입력이면 같은 답이 나오는 것이 성질인데,
 * 생성 모델을 끼우면 지연·비용과 함께 그 성질이 사라진다. 여기서 만드는 문장은 전부 이미 계산된
 * 값을 규칙대로 옮긴 것이라 재현되고 테스트된다.
 *
 * <p>숫자는 응답의 다른 필드와 같은 값을 쓴다. 문장과 필드가 어긋나면 교사는 둘 중 어느 쪽을
 * 믿어야 할지 알 수 없다.
 */
final class ReissueGuidanceWriter {

    private ReissueGuidanceWriter() {
    }

    /**
     * 소단원 하나의 설명 세 문장을 만든다.
     *
     * @param coverageEnough 취약 분포가 실제 오답을 설명하기에 충분한지. 거짓이면 대표값이 없는
     *                       이유가 "취약점 없음"이 아니라 "자료 부족"이다
     */
    static ReissueProposalResponse.Guidance write(
            Adaptive adaptive,
            ReissueProposalResponse.ReviewProposal review,
            ReissueProposalResponse.SimilarProposal similar,
            ReissueProposalResponse.AdvancedProposal advanced,
            boolean coverageEnough
    ) {
        return new ReissueProposalResponse.Guidance(
                status(adaptive),
                plan(review, similar, advanced, adaptive),
                weakness(advanced, coverageEnough));
    }

    /** 지금 이 난이도에 서 있는 이유. */
    private static String status(Adaptive adaptive) {
        String difficulty = label(adaptive.difficulty());
        return switch (adaptive.source()) {
            case "judgement" -> judgementSentence(adaptive, difficulty);
            case "placement" -> placementSentence(adaptive, difficulty);
            default -> "채점된 문항이 없어 " + difficulty + " 난이도에서 시작합니다.";
        };
    }

    /** 직전 회차 결과로 난이도가 정해진 경우. */
    private static String judgementSentence(Adaptive adaptive, String difficulty) {
        String head = "유사 %d문항 중 %d개 정답(%s%%)으로 %s".formatted(
                adaptive.similarTotalCount(),
                adaptive.similarCorrectCount(),
                trim(adaptive.accuracyRate()),
                statusLabel(adaptive.lastStatus()));
        if (adaptive.difficultyBefore() == adaptive.difficulty()) {
            return head + " 판정, " + difficulty + " 난이도를 이어갑니다.";
        }
        String direction = adaptive.difficulty() > adaptive.difficultyBefore() ? "올립니다" : "내립니다";
        return head + " 판정, %s 난이도에서 %s 난이도로 %s."
                .formatted(label(adaptive.difficultyBefore()), difficulty, direction);
    }

    /** 진단 평가로 난이도가 정해진 경우. 단일 난이도 진단은 무엇을 풀어 본 결과인지 함께 밝힌다. */
    private static String placementSentence(Adaptive adaptive, String difficulty) {
        if (adaptive.placementSoleDifficulty() != null) {
            return "%s 난이도만 출제된 진단에서 정답률 %s%%로 %s 난이도에서 시작합니다.".formatted(
                    label(adaptive.placementSoleDifficulty()),
                    trim(adaptive.placementRate()),
                    difficulty);
        }
        return "진단 평가 가중 성취도 %s%%로 %s 난이도에서 시작합니다.".formatted(
                trim(adaptive.placementRate()), difficulty);
    }

    /** 이번에 무엇을 몇 개 낼지. 0인 칸은 왜 0인지 밝힌다 — 빈 칸은 고장으로 읽힌다. */
    private static String plan(
            ReissueProposalResponse.ReviewProposal review,
            ReissueProposalResponse.SimilarProposal similar,
            ReissueProposalResponse.AdvancedProposal advanced,
            Adaptive adaptive
    ) {
        String reviewPart = review.proposedCount() > 0
                ? "동일 %d문항(최근 오답)".formatted(review.proposedCount())
                : "동일 문항은 다시 낼 오답이 없어 건너뜁니다";
        String similarPart = "유사 %d문항(%s)".formatted(
                similar.proposedCount(), label(adaptive.difficulty()));
        return "%s, %s. %s".formatted(reviewPart, similarPart, advancedPart(advanced, adaptive));
    }

    /** 응용은 발동해도 기본 0문항이다. 교사가 직접 올려야 나간다는 것까지 밝힌다. */
    private static String advancedPart(
            ReissueProposalResponse.AdvancedProposal advanced, Adaptive adaptive
    ) {
        if (advanced.triggered()) {
            return "응용은 상 난이도를 통과해 최대 %d문항까지 낼 수 있습니다(기본 0문항)."
                    .formatted(advanced.maxCount());
        }
        if (adaptive.difficultyBefore() != DifficultyLadder.HIGH) {
            return "응용은 상 난이도가 아니라 내지 않습니다.";
        }
        return "응용은 상 난이도를 통과하지 못해 내지 않습니다.";
    }

    /** 어디가 약한지. 대표값이 없을 때 "취약점 없음"과 "자료 부족"을 구분한다. */
    private static String weakness(
            ReissueProposalResponse.AdvancedProposal advanced, boolean coverageEnough
    ) {
        String area = label(advanced.primaryEvaluationArea());
        String stage = label(advanced.primaryTargetStage());
        if (area != null && stage != null) {
            return "%s 영역·%s 단계가 약합니다.".formatted(area, stage);
        }
        if (area != null) {
            return "%s 영역이 약합니다.".formatted(area);
        }
        if (stage != null) {
            return "%s 단계가 약합니다.".formatted(stage);
        }
        if (advanced.historicalIncorrectItemCount() == 0) {
            return "누적 오답이 없어 특정할 취약점이 없습니다.";
        }
        if (!coverageEnough) {
            return "오답 %d건 중 분류된 문항이 적어 취약 영역을 특정하지 못했습니다."
                    .formatted(advanced.historicalIncorrectItemCount());
        }
        return "오답 %d건이 특정 영역에 몰리지 않아 취약 영역을 특정하지 못했습니다."
                .formatted(advanced.historicalIncorrectItemCount());
    }

    private static String label(short difficulty) {
        return DisplayLabels.difficulty(DifficultyLadder.code(difficulty));
    }

    private static String label(EvaluationArea area) {
        return area == null ? null : DisplayLabels.area(area.name());
    }

    /**
     * 풀이 단계 표기.
     *
     * <p>평가 영역과 헷갈리지 않는 말을 쓴다. {@code EXECUTE} 를 "계산"이라고 하면 평가 영역의
     * "계산"과 같은 말이 되는데, 둘은 다른 축이다(한 문항 안에서 여러 단계가 나온다).
     *
     * <p>지금은 이 화면만 쓴다. PDF 보고서 등 두 번째 소비자가 생기면 {@code DisplayLabels} 로
     * 올린다(AGENTS.md 3절 3번).
     */
    private static String label(DiagnosticStage stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case INTERPRET -> "해석";
            case MODEL -> "식 세우기";
            case EXECUTE -> "실행";
            case ANSWER -> "답 쓰기";
        };
    }

    private static String statusLabel(MasteryStatus status) {
        if (status == null) {
            return "판단 보류";
        }
        return switch (status) {
            case CLEAR -> "통과";
            case WATCH -> "유지";
            case NEEDS_SUPPORT -> "지원 필요";
        };
    }

    /** 60.00 을 60 으로 줄인다. 문장 안에서 의미 없는 소수점 두 자리는 읽기만 어렵다. */
    private static String trim(BigDecimal rate) {
        return rate == null ? "-" : rate.stripTrailingZeros().toPlainString();
    }
}
