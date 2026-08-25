package com.cenedu.backend.domain.analysis.reissue;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;
import com.cenedu.backend.domain.analysis.reissue.row.DiagnosticStageEvidenceRow;
import com.cenedu.backend.domain.analysis.reissue.row.EvaluationAreaEvidenceRow;
import com.cenedu.backend.domain.analysis.reissue.row.IncorrectQuestionRow;
import com.cenedu.backend.domain.analysis.reissue.row.LatestSimilarResultRow;
import com.cenedu.backend.domain.analysis.reissue.row.PlacementTallyRow;
import com.cenedu.backend.domain.analysis.reissue.row.QuestionOwnershipRow;
import com.cenedu.backend.domain.analysis.reissue.row.SubUnitRow;
import com.cenedu.backend.domain.analysis.reissue.row.SubUnitWeaknessRow;
import com.cenedu.backend.domain.analysis.service.AnalysisClassQueryService;
import com.cenedu.backend.domain.analysis.service.DifficultyLadder;
import com.cenedu.backend.domain.analysis.service.MasteryStatusJudge;
import com.cenedu.backend.domain.analysis.service.PlacementScorer;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import com.cenedu.backend.global.common.enums.EvaluationArea;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 맞춤 문제 재출제 제안을 만든다.
 *
 * <p>조회 전용이다. 상태를 저장하지 않고 원본 배정과 파생 맞춤 회차의 채점 결과에서 매번 다시
 * 계산한다. 같은 입력이면 같은 출력이라 몇 번을 불러도 안전하고, 채점 확정 시점에 판정을 쓰는
 * 훅도 필요 없다.
 *
 * <p>난이도는 <b>직전 회차 하나만</b> 보고 정한다. 맞춤 회차가 아직 없으면 원본 배정으로 영점
 * 조절을 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReissueProposalService {

    /** 출제 기본값 — 동일 1. */
    private static final int DEFAULT_REVIEW_COUNT = 1;

    /** 출제 기본값 — 유사 5. */
    private static final int DEFAULT_SIMILAR_COUNT = 5;

    /** 출제 기본값 — 응용 0. 발동 가능해도 교사가 직접 올려야 나간다. */
    private static final int DEFAULT_ADVANCED_COUNT = 0;

    /** 교사가 올릴 수 있는 상한. */
    private static final int MAX_PROPOSED_COUNT = 10;

    /** 유사도 기준으로 넘길 오답 문항 수 상한. 프롬프트가 무한정 길어지지 않게 자른다. */
    private static final int MAX_REFERENCE_QUESTIONS = 10;

    /**
     * 대표값으로 뽑기 위한 최소 채점 표본.
     *
     * <p>오답률이 아니라 오답 개수로 고르게 되면서 크게 걸 이유가 없어졌다. 한 문항만 채점된
     * 영역을 후보에서 빼는 정도로 둔다.
     */
    private static final int MIN_EVIDENCE_SAMPLE = 2;

    /** 근거가 하나도 없을 때 서 있을 자리. 위아래 어느 쪽으로도 조절할 수 있는 가운데다. */
    private static final short FALLBACK_DIFFICULTY = DifficultyLadder.MID;

    private final AnalysisClassQueryService classQueryService;
    private final ReissueProposalRepository repository;

    /** 원본 배정에서 파생된 학습 흐름 전체를 근거로 학생의 소단원별 재출제 제안을 반환한다. */
    public ReissueProposalResponse getProposal(long teacherId, long assignmentId, long studentId) {
        classQueryService.getAuthorizedAssignment(teacherId, assignmentId);
        validateGraded(assignmentId, studentId);

        List<SubUnitRow> subUnits = repository.findSubUnits(assignmentId, studentId);
        Context context = loadContext(assignmentId, studentId);

        return new ReissueProposalResponse(subUnits.stream()
                .map(subUnit -> toProposal(subUnit, context))
                .filter(ReissueProposalService::hasSomethingToMake)
                .toList());
    }

    /**
     * 이 소단원으로 만들 수 있는 것이 하나라도 있는지.
     *
     * <p>조회는 학생이 푼 모든 소단원을 가져온다. 다 맞힌 단원도 섞여 오는데, 그런 단원은
     * 복습할 오답도 없고 유사 문항을 만들 기준 문항도 없어 세 단계가 모두 비어 있다.
     * 그대로 내보내면 교사가 아무것도 할 수 없는 줄이 "취약 소분류" 목록에 남는다.
     *
     * <p>{@code proposedCount} 가 아니라 {@code maxCount} 를 본다. 추천이 0 이어도 교사가
     * 올릴 수 있으면 남겨야 한다.
     *
     * <p>응용은 보지 않는다. 상한이 항상 열려 있어 판단 기준이 되지 못한다. 뜻으로 풀면
     * 이렇다 — 복습할 오답도 없고 유사 문항을 만들 기준도 없다면 취약하지 않다. 응용만
     * 낼 수 있다고 취약 목록에 올리지 않는다.
     */
    private static boolean hasSomethingToMake(ReissueProposalResponse.SubUnitProposal proposal) {
        return proposal.review().maxCount() > 0 || proposal.similar().maxCount() > 0;
    }

    /**
     * 원본 배정이 학생에게 배정됐고 채점이 끝났는지 확인한다.
     *
     * <p>채점 전에는 영점 조절도 상태 판정도 성립하지 않는다. 조용히 기본 난이도로 대체하면
     * 아무도 그 값이 근거 없는 값인 줄 모른다.
     */
    private void validateGraded(long assignmentId, long studentId) {
        String status = repository.findRootAssignmentStatus(assignmentId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED));
        if (!AssignmentStatus.GRADED.name().equals(status)) {
            throw new BusinessException(ErrorCode.ANALYSIS_REISSUE_NOT_GRADED);
        }
    }

    /** 소단원별로 갈라 둔 조회 결과를 한 번에 모은다. */
    private Context loadContext(long assignmentId, long studentId) {
        return new Context(
                groupBy(repository.findPlacementTallies(assignmentId, studentId),
                        PlacementTallyRow::subUnitId),
                groupBy(repository.findLatestSimilarResults(assignmentId, studentId),
                        LatestSimilarResultRow::subUnitId),
                groupBy(repository.findIncorrectQuestions(assignmentId, studentId),
                        IncorrectQuestionRow::subUnitId),
                groupBy(repository.findAnsweredQuestions(assignmentId, studentId),
                        QuestionOwnershipRow::subUnitId),
                repository.findSubUnitWeakness(assignmentId, studentId).stream()
                        .collect(Collectors.toMap(SubUnitWeaknessRow::subUnitId, row -> row)),
                groupBy(repository.findEvaluationAreaEvidence(assignmentId, studentId),
                        EvaluationAreaEvidenceRow::subUnitId),
                groupBy(repository.findDiagnosticStageEvidence(assignmentId, studentId),
                        DiagnosticStageEvidenceRow::subUnitId),
                repository.countCustomSessions(assignmentId, studentId));
    }

    private static <T> Map<Long, List<T>> groupBy(
            List<T> rows, java.util.function.Function<T, Long> key
    ) {
        return rows.stream().collect(Collectors.groupingBy(key));
    }

    /** 소단원 하나의 세 단계 제안을 만든다. */
    private ReissueProposalResponse.SubUnitProposal toProposal(SubUnitRow subUnit, Context context) {
        long subUnitId = subUnit.subUnitId();
        Adaptive adaptive = resolveAdaptive(subUnitId, context);

        List<IncorrectQuestionRow> incorrect =
                context.incorrectQuestions().getOrDefault(subUnitId, List.of());
        SubUnitWeaknessRow weakness = context.weakness().get(subUnitId);

        ReissueProposalResponse.ReviewProposal review = toReview(incorrect);
        ReissueProposalResponse.SimilarProposal similar =
                toSimilar(subUnitId, adaptive.difficulty(), incorrect, context);
        ReissueProposalResponse.AdvancedProposal advanced =
                toAdvanced(subUnitId, adaptive.advancedTriggered(), weakness, context);

        return new ReissueProposalResponse.SubUnitProposal(
                subUnitId,
                subUnit.subUnitName(),
                ReissueGuidanceWriter.write(adaptive, review, similar, advanced),
                adaptive.toResponse(context.customSessionCount()),
                review,
                similar,
                advanced);
    }

    /**
     * 이 소단원의 현재 난이도를 정한다.
     *
     * <p>직전 맞춤 회차의 유사 문항 결과가 있으면 그것으로 판정하고, 없으면 원본 배정으로 영점
     * 조절을 한다. 둘 다 없으면(그 소단원에 채점된 문항이 없으면) 가운데에 세운다.
     */
    private Adaptive resolveAdaptive(long subUnitId, Context context) {
        Adaptive judged = judgeFromLatestSession(subUnitId, context);
        if (judged != null) {
            return judged;
        }

        PlacementScorer.PlacementResult placement = PlacementScorer.score(
                toTally(context.placementTallies().getOrDefault(subUnitId, List.of())));
        if (placement != null) {
            return Adaptive.fromPlacement(placement.difficulty(), placement.rate(),
                    placement.mixed(), placement.soleDifficulty());
        }

        return Adaptive.unknown(FALLBACK_DIFFICULTY);
    }

    /** 직전 회차의 유사 문항 결과로 승급·유지·강등을 판정한다. 채점된 유사 문항이 없으면 null. */
    private Adaptive judgeFromLatestSession(long subUnitId, Context context) {
        List<LatestSimilarResultRow> rows =
                context.latestSimilarResults().getOrDefault(subUnitId, List.of());
        int totalCount = rows.stream().mapToInt(LatestSimilarResultRow::gradedCount).sum();
        if (totalCount == 0) {
            return null;
        }

        int correctCount = rows.stream().mapToInt(LatestSimilarResultRow::correctCount).sum();
        MasteryStatusJudge.Judgement judgement =
                MasteryStatusJudge.judge(dominantDifficulty(rows), totalCount, correctCount);

        return Adaptive.fromJudgement(judgement.difficultyBefore(), totalCount, correctCount,
                judgement.status(), judgement.cutoffRule(), judgement.accuracyRate(),
                judgement.difficultyAfter(), judgement.advancedTriggered());
    }

    /**
     * 직전 회차 유사 문항의 대표 난이도.
     *
     * <p>재고가 모자라 난이도가 섞였으면 문항이 가장 많은 쪽을 쓰고, 같으면 낮은 쪽을 쓴다.
     * 섞인 회차에서 높은 쪽을 기준으로 잡으면 실제로 풀지 않은 난이도에서 승급하게 된다.
     */
    private static short dominantDifficulty(List<LatestSimilarResultRow> rows) {
        Comparator<LatestSimilarResultRow> byCountThenLowerDifficulty =
                Comparator.comparingInt(LatestSimilarResultRow::gradedCount)
                        .thenComparingInt(row -> -row.difficulty());
        return rows.stream()
                .filter(row -> row.gradedCount() > 0)
                .max(byCountThenLowerDifficulty)
                .map(LatestSimilarResultRow::difficulty)
                .orElse(FALLBACK_DIFFICULTY);
    }

    /** 난이도별 행을 영점 조절 입력으로 합친다. */
    private static PlacementScorer.PlacementTally toTally(List<PlacementTallyRow> rows) {
        int lowTotal = 0;
        int lowCorrect = 0;
        int midTotal = 0;
        int midCorrect = 0;
        int highTotal = 0;
        int highCorrect = 0;
        for (PlacementTallyRow row : rows) {
            switch (row.difficulty()) {
                case DifficultyLadder.LOW -> {
                    lowTotal += row.gradedCount();
                    lowCorrect += row.correctCount();
                }
                case DifficultyLadder.MID -> {
                    midTotal += row.gradedCount();
                    midCorrect += row.correctCount();
                }
                case DifficultyLadder.HIGH -> {
                    highTotal += row.gradedCount();
                    highCorrect += row.correctCount();
                }
                default -> {
                    // 1~3 이 아닌 난이도는 판정 축이 없어 집계에서 뺀다.
                }
            }
        }
        return new PlacementScorer.PlacementTally(
                lowTotal, lowCorrect, midTotal, midCorrect, highTotal, highCorrect);
    }

    /** 동일 문항 후보를 최근 오답 순으로 상한까지 담는다. 다시 낼 수 있는 유형만 남긴다. */
    private ReissueProposalResponse.ReviewProposal toReview(List<IncorrectQuestionRow> incorrect) {
        List<Long> candidates = incorrect.stream()
                .filter(IncorrectQuestionRow::reissuable)
                .limit(MAX_PROPOSED_COUNT)
                .map(IncorrectQuestionRow::questionId)
                .toList();
        return new ReissueProposalResponse.ReviewProposal(
                Math.min(DEFAULT_REVIEW_COUNT, candidates.size()),
                candidates.size(),
                candidates);
    }

    /** 유사 문항 제안. 유사도 기준 문항은 반복해서 틀린 순으로 담는다. */
    private ReissueProposalResponse.SimilarProposal toSimilar(
            long subUnitId, short difficulty, List<IncorrectQuestionRow> incorrect, Context context
    ) {
        List<ReissueProposalResponse.ReferenceQuestion> references = incorrect.stream()
                .sorted(Comparator.comparingInt(IncorrectQuestionRow::incorrectCount).reversed()
                        .thenComparing(IncorrectQuestionRow::lastIncorrectAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_REFERENCE_QUESTIONS)
                .map(row -> new ReissueProposalResponse.ReferenceQuestion(
                        row.questionId(), row.incorrectCount(), row.lastIncorrectAt()))
                .toList();

        List<Long> excluded = context.answeredQuestions()
                .getOrDefault(subUnitId, List.<QuestionOwnershipRow>of()).stream()
                .map(QuestionOwnershipRow::questionId)
                .toList();

        int proposedCount = references.isEmpty() ? 0 : DEFAULT_SIMILAR_COUNT;
        int maxCount = references.isEmpty() ? 0 : MAX_PROPOSED_COUNT;
        return new ReissueProposalResponse.SimilarProposal(
                proposedCount,
                maxCount,
                DifficultyLadder.code(difficulty),
                references,
                excluded);
    }

    /**
     * 응용 문항 제안.
     *
     * <p>상한은 발동 여부와 무관하게 연다. 세 필드가 이미 역할을 나눠 갖고 있다 —
     * {@code proposedCount} 가 "권하지 않는다"를, {@code triggered} 가 "조건 미충족"을
     * 말한다. 상한까지 0 으로 막으면 같은 말을 세 번 하면서 교사의 판단만 뺏는다.
     *
     * <p>다른 두 단계의 {@code maxCount} 는 "만들 수 있는 개수"다 — 복습은 재출제 가능한
     * 오답 수, 유사는 기준 문항이 있는지. 응용만 정책 판단을 같은 필드에 실으면 뜻이
     * 어긋난다.
     *
     * <p>교사는 시스템이 모르는 근거를 갖고 있을 수 있다(지필 성적, 수업 중 이해도).
     * 발동 조건은 {@code triggered} 로 알리고, 낼지 말지는 교사가 정한다.
     */
    private ReissueProposalResponse.AdvancedProposal toAdvanced(
            long subUnitId, boolean triggered, SubUnitWeaknessRow weakness, Context context
    ) {
        List<ReissueProposalResponse.EvaluationAreaEvidence> areaEvidence =
                context.evaluationAreaEvidence().getOrDefault(subUnitId, List.of()).stream()
                        .map(row -> new ReissueProposalResponse.EvaluationAreaEvidence(
                                row.evaluationArea(), row.gradedItemCount(),
                                row.incorrectItemCount(),
                                rate(row.incorrectItemCount(), row.gradedItemCount())))
                        .toList();
        List<ReissueProposalResponse.DiagnosticStageEvidence> stageEvidence =
                context.diagnosticStageEvidence().getOrDefault(subUnitId, List.of()).stream()
                        .map(row -> new ReissueProposalResponse.DiagnosticStageEvidence(
                                row.diagnosticType(), row.gradedUnitCount(),
                                row.incorrectUnitCount(),
                                rate(row.incorrectUnitCount(), row.gradedUnitCount())))
                        .toList();

        int historicalIncorrectItemCount =
                weakness == null ? 0 : weakness.historicalIncorrectItemCount();

        return new ReissueProposalResponse.AdvancedProposal(
                triggered,
                DEFAULT_ADVANCED_COUNT,
                MAX_PROPOSED_COUNT,
                historicalIncorrectItemCount,
                weakness == null ? 0 : weakness.incorrectSessionCount(),
                primaryEvaluationArea(areaEvidence),
                primaryTargetStage(stageEvidence),
                areaEvidence,
                stageEvidence);
    }

    /**
     * 우선 참고할 평가 영역.
     *
     * <p>오답률이 아니라 <b>오답 개수</b>로 고른다. 이 값은 LLM 에게 "어느 영역을 겨냥해 문제를
     * 만들라" 고 알려 주는 것이라, 비율이 높은 영역보다 실제로 많이 틀린 영역이 맞다.
     *
     * <p>개수로 고르면 표본이 적은 영역이 대표를 가로채지 못한다. 오답률로 고를 때는 2문항 중
     * 1개 틀린 영역(50%)이 10문항 중 4개 틀린 영역(40%)을 이겨서 최소 채점 수를 크게 걸어야
     * 했는데, 그럴 필요가 없어졌다.
     *
     * <p>오답 개수에 바닥을 두지 않는다. 소단원 하나에서 나온 오답이 한 건뿐이면 그 한 건이
     * 근거의 전부다 — 약한 근거가 아니라 완전한 근거다. 교사에게는 "이 영역에서 틀렸다" 는
     * 사실이 아무 말도 하지 않는 것보다 쓸모 있다.
     *
     * <p>커버리지(분포가 실제 오답을 얼마나 설명하는지)로는 막지 않는다. 커버리지가 낮은 원인은
     * 학생이 아니라 {@code problem_question.evaluation_area} 가 비어 있는 것이고, 문항 은행의
     * 분류율이 절반 남짓이라 기준선 근처에서 통과·차단이 갈려 교사 눈에는 변덕으로 보였다.
     * 분류된 오답이 적어도 그 몇 건은 진짜 데이터다 — 아무 말도 하지 않는 것보다 낫다.
     */
    private EvaluationArea primaryEvaluationArea(
            List<ReissueProposalResponse.EvaluationAreaEvidence> evidence
    ) {
        return evidence.stream()
                .filter(item -> item.gradedItemCount() >= MIN_EVIDENCE_SAMPLE)
                .filter(item -> item.incorrectItemCount() > 0)
                .max(Comparator.comparingInt(
                                ReissueProposalResponse.EvaluationAreaEvidence::incorrectItemCount)
                        .thenComparing(
                                ReissueProposalResponse.EvaluationAreaEvidence::incorrectRate))
                .map(ReissueProposalResponse.EvaluationAreaEvidence::evaluationArea)
                .orElse(null);
    }

    /** 우선 참고할 풀이 단계. 평가 영역과 같은 기준으로 고른다 — 오답 개수 우선. */
    private DiagnosticStage primaryTargetStage(
            List<ReissueProposalResponse.DiagnosticStageEvidence> evidence
    ) {
        return evidence.stream()
                .filter(item -> item.gradedUnitCount() >= MIN_EVIDENCE_SAMPLE)
                .filter(item -> item.incorrectUnitCount() > 0)
                .max(Comparator.comparingInt(
                                ReissueProposalResponse.DiagnosticStageEvidence::incorrectUnitCount)
                        .thenComparing(
                                ReissueProposalResponse.DiagnosticStageEvidence::incorrectRate))
                .map(ReissueProposalResponse.DiagnosticStageEvidence::diagnosticType)
                .orElse(null);
    }

    /** 분모가 0 이면 비율을 만들지 않는다. 0.0 은 "완벽하게 잘함"으로 읽힌다. */
    private static BigDecimal rate(int part, int total) {
        return total == 0 ? null : MasteryStatusJudge.rate(part, total);
    }

    /** 소단원별로 갈라 둔 조회 결과 묶음. */
    private record Context(
            Map<Long, List<PlacementTallyRow>> placementTallies,
            Map<Long, List<LatestSimilarResultRow>> latestSimilarResults,
            Map<Long, List<IncorrectQuestionRow>> incorrectQuestions,
            Map<Long, List<QuestionOwnershipRow>> answeredQuestions,
            Map<Long, SubUnitWeaknessRow> weakness,
            Map<Long, List<EvaluationAreaEvidenceRow>> evaluationAreaEvidence,
            Map<Long, List<DiagnosticStageEvidenceRow>> diagnosticStageEvidence,
            int customSessionCount
    ) {
    }
}
