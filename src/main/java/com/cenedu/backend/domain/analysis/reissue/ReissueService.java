package com.cenedu.backend.domain.analysis.reissue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.cenedu.backend.domain.analysis.dto.AttemptResult;
import com.cenedu.backend.domain.analysis.dto.LearningState;
import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;
import com.cenedu.backend.domain.analysis.entity.LearningStatus;
import com.cenedu.backend.domain.analysis.repository.AnalysisAttemptRepository;
import com.cenedu.backend.domain.analysis.service.WeaknessAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 응답에서 다음에 낼 문항을 고른다.
 *
 * <p>보고서와 같은 상태값에서 갈라져 나오지만 서로 독립이다. 보고서를 만들지 않아도 재출제는
 * 되고 그 반대도 된다.
 */
@Service
public class ReissueService {

    /**
     * 저장된 {@code step_responses_json} 을 읽기만 한다. 이 컨텍스트에는 공용
     * {@code ObjectMapper} 빈이 없고, 빈을 만드는 자리({@code global/config})는 다른 담당자
     * 소유라 여기서 손대지 않는다(AGENTS.md 2절).
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AnalysisAttemptRepository attempts;
    private final QuestionBank bank;
    private final BankUnitCrosswalk crosswalk;

    public ReissueService(AnalysisAttemptRepository attempts,
                          QuestionBank bank, BankUnitCrosswalk crosswalk) {
        this.attempts = attempts;
        this.bank = bank;
        this.crosswalk = crosswalk;
    }

    @Transactional(readOnly = true)
    public Plan plan(String assessmentId, String studentId) {
        if (bank.isEmpty()) {
            throw new IllegalStateException(bank.missingMessage());
        }
        List<AnalysisAttempt> rows = attempts
                .findByAssessmentIdAndStudentIdOrderByProblemNumberAscOccurredAtAscEventIdAsc(
                        assessmentId, studentId);
        if (rows.isEmpty()) {
            throw new NoSuchElementException(
                    "저장된 응답이 없습니다: " + assessmentId + " / " + studentId);
        }

        // 오류로 기록되지 않은 응답은 학생에 대한 근거가 아니다. 판정에서 먼저 빼고, 남은
        // 응답으로만 상태를 읽는다. 전부 빠지면 읽을 상태 자체가 없다.
        String failedProblemId = failedProblemId(rows);
        List<AnalysisAttempt> answered = rows.stream()
                .filter(row -> !row.isSubmissionFailed())
                .toList();

        Focus focus = answered.isEmpty() ? null : focus(answered, studentId);
        LearningState state = focus == null ? null : focus.state();
        String conceptId = state == null ? rows.get(0).getConceptId() : state.conceptId();
        String unit = crosswalk.bankUnit(conceptId);
        if (unit == null) {
            throw new IllegalStateException("뱅크 소단원 대응이 없는 개념입니다: " + conceptId);
        }

        // 체류 난이도는 이전 문제지에서 읽는다. 재출제가 그 한 회분만 참조하므로 따로 저장해
        // 들고 다닐 이유가 없다.
        QuestionDifficulty dwell = focus == null ? QuestionDifficulty.MEDIUM : focus.dwell();
        ReissueMode mode = mode(state, failedProblemId, dwell);
        QuestionDifficulty difficulty = state == null
                ? dwell : nextDifficulty(state.status(), dwell);

        if (mode == ReissueMode.SAME) {
            // 같은 문항을 그대로 다시 내므로 난이도를 움직이지 않는다.
            return new Plan(studentId, mode, conceptId, unit, dwell, dwell,
                    focus == null ? null : focus.area(),
                    focus == null ? null : focus.stage(), state, List.of(), 0,
                    "시스템 오류로 답이 기록되지 않아 같은 문항을 다시 냅니다: " + failedProblemId);
        }
        if (mode == ReissueMode.APPLIED) {
            String why = state.status() == LearningStatus.CLEAR
                    ? "이 문제지에서 관찰된 오류가 없어 진단할 지점이 없습니다."
                    : "지도 후 회복을 확인했습니다.";
            return new Plan(studentId, mode, conceptId, unit, dwell, dwell,
                    focus.area(), focus.stage(), state, List.of(), 0,
                    why + " 상 난이도까지 올라와 더 올릴 칸이 없습니다. "
                            + "응용 문항 대상이며 응용 문항은 생성이 필요합니다.");
        }

        var target = new ReissueSelector.ReissueTarget(unit, difficulty);
        var result = ReissueSelector.select(
                bank.questions(), target, servedIds(rows), studentId);
        return new Plan(studentId, mode, conceptId, unit, difficulty, dwell,
                focus.area(), focus.stage(), state,
                result.questions().stream()
                        .map(question -> Candidate.from(question, focus.stage()))
                        .toList(),
                result.candidateCount(), null);
    }

    /**
     * 어떤 모드로 낼지 정한다.
     *
     * <p>같은 문항을 다시 내는 근거는 하나뿐이다. 시스템 오류로 학생이 답을 쓰지 못한 경우다.
     * 그때는 학생에 대해 알게 된 것이 없으므로 다른 문항으로 넘어갈 수 없고, 나머지 응답이
     * 아무리 깨끗해 보여도 그 판단을 믿을 수 없다. 그래서 이 검사가 가장 먼저 온다.
     *
     * <p>학생이 답을 쓰긴 썼는데 어느 구간에서 막혔는지 안 보이는 경우는 여기 해당하지 않는다.
     * 그때는 유사 문항으로 간다. 유사 문항도 앞 구간부터 풀게 하므로 관찰은 거기서 얻는다.
     * 같은 문항을 다시 내면 학생은 답을 외워서 낼 뿐이다.
     *
     * <p>오류가 없다고 바로 응용으로 보내지 않는다. <b>난이도 사다리를 끝까지 올라간 학생만</b>
     * 응용 대상이다. 체류 난이도가 하나 중이면 아직 올라갈 칸이 남아 있으므로 한 칸 승급해서
     * 유사 문항을 낸다. 상에서 오류가 없을 때 비로소 더 줄 것이 없다.
     *
     * @param state           모든 응답이 오류로 기록되지 않았으면 {@code null}이다. 그 경우는
     *                        첫 분기에서 걸러지므로 뒤 분기까지 오지 않는다.
     * @param failedProblemId 오류로 기록되지 않은 문항. 없으면 {@code null}.
     * @param dwell           이전 문제지에서 머물러 있던 난이도. 승급 <b>전</b> 값이다.
     */
    static ReissueMode mode(LearningState state, String failedProblemId,
                            QuestionDifficulty dwell) {
        if (failedProblemId != null) {
            return ReissueMode.SAME;
        }
        boolean noErrorLeft = state.status() == LearningStatus.CLEAR
                || state.status() == LearningStatus.IMPROVED;
        return noErrorLeft && dwell == QuestionDifficulty.HIGH
                ? ReissueMode.APPLIED : ReissueMode.SIMILAR;
    }

    /**
     * 이전 문제지에서 학생이 <b>머물러 있던</b> 난이도. 다음에 낼 난이도가 아니다.
     *
     * <p>따로 저장하지 않는다. 이전 문제지의 문항 난이도가 곧 그 값이고, 재출제는 그 한 회분만
     * 참조하므로 상태를 들고 다닐 이유가 없다.
     *
     * <p>난이도를 읽을 수 없는 행({@code UNKNOWN})은 뺀다. 모르는 것을 중간값으로 채우면
     * 난이도별 정답률이 조용히 흐려진다.
     *
     * <p>재출제로 만든 문제지는 난이도가 하나뿐이라 그 값을 그대로 읽는다. <b>성적은 보지
     * 않는다.</b> 상 문제지를 다 틀렸어도 체류 난이도는 상이고, 내려가는 것은
     * 상태({@code NEEDS_SUPPORT})가 시킨다. 둘을 섞으면 다음 회차의 출발점이 흐려진다.
     *
     * <p>난이도가 섞인 문제지는 교사가 낸 첫 진단평가뿐이다. 그때는 <b>하부터 차례로 통과하는지</b>
     * 보고 처음 막히는 칸을 체류 난이도로 삼는다. 맞힌 것 중 가장 높은 난이도를 쓰면 "상은 맞고
     * 하는 틀린" 학생을 상 근처에 놓게 되는데, 그건 찍었을 수도 있고 라벨이 그 학생에게 안 맞는
     * 것일 수도 있어 근거가 얇다. 낮은 쪽 실패를 무겁게 보는 편이 싸다 — 낮게 시작하면 회차
     * 하나를 손해 보지만, 높게 시작하면 학생이 계속 틀리며 내려온다.
     */
    static QuestionDifficulty dwellDifficulty(List<AnalysisAttempt> group) {
        Map<QuestionDifficulty, Set<String>> asked = new EnumMap<>(QuestionDifficulty.class);
        Map<QuestionDifficulty, Set<String>> wrong = new EnumMap<>(QuestionDifficulty.class);
        for (AnalysisAttempt row : group) {
            QuestionDifficulty band = QuestionDifficulty.fromBand(row.getDifficultyBand());
            if (band == null) {
                continue;
            }
            asked.computeIfAbsent(band, ignored -> new LinkedHashSet<>()).add(row.getProblemId());
            if (!row.isCorrect()) {
                wrong.computeIfAbsent(band, ignored -> new LinkedHashSet<>())
                        .add(row.getProblemId());
            }
        }
        if (asked.isEmpty()) {
            return QuestionDifficulty.MEDIUM;
        }
        if (asked.size() == 1) {
            return asked.keySet().iterator().next();
        }
        for (QuestionDifficulty band : List.of(QuestionDifficulty.LOW,
                QuestionDifficulty.MEDIUM, QuestionDifficulty.HIGH)) {
            Set<String> problems = asked.get(band);
            if (problems == null || problems.isEmpty()) {
                continue;
            }
            if (blocked(wrong.getOrDefault(band, Set.of()).size(), problems.size())) {
                return band;
            }
        }
        return QuestionDifficulty.HIGH;
    }

    /**
     * 그 칸에서 막혔는지. 틀린 문항이 절반 이상이면 막힌 것이다.
     *
     * <p>지원이 필요한지 보는 기준과 같은 식이다. 한 곳에서는 "이 단계가 취약하다"를, 여기서는
     * "이 난이도를 아직 못 넘었다"를 뜻하지만 읽는 방식은 같아야 한다.
     */
    private static boolean blocked(int wrongProblems, int totalProblems) {
        return wrongProblems * 2 >= totalProblems;
    }

    /**
     * 다음에 낼 난이도. 체류 난이도에서 상태가 시키는 만큼 움직인다.
     *
     * <p>{@code CLEAR}·{@code IMPROVED}가 상에 닿으면 이 값은 쓰이지 않는다. 그때는 모드 3으로
     * 가기 때문이다.
     */
    static QuestionDifficulty nextDifficulty(LearningStatus status, QuestionDifficulty dwell) {
        return switch (status) {
            case NEEDS_SUPPORT -> dwell.easier();
            case WATCH -> dwell;
            case CLEAR, IMPROVED -> dwell.harder();
        };
    }

    /** 집중해서 볼 (개념, 단계) 하나와 그때 관찰된 값들. */
    private Focus focus(List<AnalysisAttempt> rows, String studentId) {
        Map<String, List<AnalysisAttempt>> byStep = new LinkedHashMap<>();
        for (AnalysisAttempt row : rows) {
            byStep.computeIfAbsent(row.getConceptId() + "::" + row.getStepId(),
                    ignored -> new ArrayList<>()).add(row);
        }

        Focus best = null;
        for (List<AnalysisAttempt> group : byStep.values()) {
            List<AttemptResult> results = group.stream()
                    .map(row -> toAttemptResult(row, studentId))
                    .toList();
            LearningState state = WeaknessAnalyzer.analyze(results);
            Focus candidate = new Focus(state,
                    mostCommonArea(group), firstWrongStage(group), dwellDifficulty(group));
            if (best == null || rank(candidate.state()) > rank(best.state())) {
                best = candidate;
            }
        }
        return best;
    }

    private static AttemptResult toAttemptResult(AnalysisAttempt row, String studentId) {
        return new AttemptResult(row.getEventId(), studentId, row.getProblemId(),
                row.getConceptId(), row.getStepId(), row.isCorrect(), row.isHintUsed(),
                row.getOccurredAt(), row.getPurpose());
    }

    /**
     * 어느 단계를 집중해서 볼지 정하는 순위.
     *
     * <p>CLEAR가 가장 낮다. 오류가 없던 단계를 겨냥해도 확인할 것이 없다. 모든 단계가 CLEAR면
     * 그때 응용 문항으로 간다.
     */
    private static int rank(LearningState state) {
        int base = switch (state.status()) {
            case NEEDS_SUPPORT -> 3_000_000;
            case WATCH -> 2_000_000;
            case IMPROVED -> 1_000_000;
            case CLEAR -> 0;
        };
        return base + state.errorCount();
    }

    /** 오답 문항에서 가장 많이 본 평가 영역. 관찰된 것만 센다. */
    private static String mostCommonArea(List<AnalysisAttempt> group) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AnalysisAttempt row : group) {
            if (!row.isCorrect() && row.getEvaluationArea() != null
                    && !row.getEvaluationArea().isBlank()) {
                counts.merge(row.getEvaluationArea(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 처음 틀린 구간의 라벨. 없으면 {@code null}이고 추정하지 않는다.
     *
     * <p>뒤 구간은 보지 않는다. 학생이 거기서 멈췄으므로 관찰되지 않은 것이다.
     */
    private static String firstWrongStage(List<AnalysisAttempt> group) {
        for (AnalysisAttempt row : group) {
            if (row.isCorrect()) {
                continue;
            }
            for (JsonNode step : readSteps(row.getStepResponsesJson())) {
                if (!step.path("correct").asBoolean(true)) {
                    String category = step.path("category").asText(null);
                    return category == null || category.isBlank() ? null : category;
                }
            }
        }
        return null;
    }

    /**
     * 시스템 오류로 답이 기록되지 않은 첫 문항. 없으면 {@code null}.
     *
     * <p>여러 문항이 실패했어도 하나만 돌려준다. 다시 낼 문항은 한 번에 하나이고, 나머지는 다음
     * 회차에서 같은 방식으로 다시 걸린다.
     */
    private static String failedProblemId(List<AnalysisAttempt> rows) {
        return rows.stream().filter(AnalysisAttempt::isSubmissionFailed)
                .map(AnalysisAttempt::getProblemId).findFirst().orElse(null);
    }

    private static JsonNode readSteps(String raw) {
        try {
            return JSON.readTree(raw == null || raw.isBlank() ? "[]" : raw);
        } catch (Exception e) {
            throw new IllegalStateException("풀이 단계 JSON을 읽을 수 없습니다.", e);
        }
    }

    /** 이미 낸 문항은 다시 내지 않는다. 뱅크 ID와 저장된 문항 ID를 같은 값으로 본다. */
    private static Set<String> servedIds(List<AnalysisAttempt> rows) {
        Set<String> served = new LinkedHashSet<>();
        for (AnalysisAttempt row : rows) {
            if (row.getProblemId() != null) {
                served.add(row.getProblemId());
            }
        }
        return served;
    }

    private record Focus(LearningState state, String area, String stage,
                         QuestionDifficulty dwell) {
    }

    /**
     * @param difficulty      다음에 낼 난이도. 승급·강등이 반영된 값이다.
     * @param dwellDifficulty 이전 문제지에서 머물러 있던 난이도. 승급 전 값이다. 교사가 사다리의
     *                        어디쯤인지 보려면 이 둘을 함께 본다.
     * @param candidateCount  하드 필터를 통과한 문항 수. 재고가 마르는지 보려면 이 값을 본다.
     * @param note            문항을 고르지 못한 모드에서 이유를 담는다.
     */
    public record Plan(
            String studentId,
            ReissueMode mode,
            String conceptId,
            String bankUnit,
            QuestionDifficulty difficulty,
            QuestionDifficulty dwellDifficulty,
            String evaluationArea,
            String targetStage,
            LearningState state,
            List<Candidate> questions,
            int candidateCount,
            String note
    ) {
    }

    /**
     * 뽑힌 문항 하나.
     *
     * <p>평가 영역과 겨냥 구간 위치는 <b>교사에게 보여주는 관찰 정보</b>다. 선정에는 쓰지 않는다.
     * 화면이 이 값을 근거처럼 읽지 않도록 {@code reason}에 실제 기준만 적는다.
     *
     * @param stagePosition 겨냥 구간이 몇 번째 빈칸인지. 없으면 -1. 표시 전용.
     * @param reason        왜 이 문항이 뽑혔는지. 화면이 문장을 짓지 않도록 서버가 만든다.
     */
    public record Candidate(
            String questionId, String prompt, String unitName,
            String difficultyBand, String evaluationArea,
            int blankCount, int stagePosition, String reason) {

        /**
         * @param stage 겨냥 구간. 선정에는 쓰지 않고 교사에게 보여줄 때만 쓴다.
         */
        static Candidate from(BankQuestion question, String stage) {
            int blankCount = question.stages().size();
            return new Candidate(
                    question.id(), question.promptText(), question.unitName(),
                    question.difficulty().band(), question.evaluationArea(),
                    blankCount, question.stagePosition(stage),
                    "같은 소단원·난이도에서 아직 내지 않은 문항 · 빈칸 "
                            + blankCount + "개라 먼저 골랐습니다");
        }
    }
}
