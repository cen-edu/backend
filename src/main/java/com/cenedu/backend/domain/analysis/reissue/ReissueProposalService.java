package com.cenedu.backend.domain.analysis.reissue;

import java.util.ArrayList;
import java.util.Comparator;
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
import com.cenedu.backend.global.common.enums.DisplayLabels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 맞춤 문제 화면이 쓰는 두 단계.
 *
 * <p>먼저 {@link #propose}가 개념마다 세 칸의 문항 수를 <b>서버가 정해서</b> 돌려준다. 교사는
 * 그 숫자만 조정한다. 조정된 값으로 {@link #generate}를 부르면 실제 문항이 나온다.
 *
 * <p>{@link ReissueService}는 "이 학생에게 지금 무엇을 내야 하는가" 하나를 고르는 자동 경로다.
 * 여기는 같은 판단을 개념별로 펼쳐 교사가 손댈 수 있게 만든 경로이고, 판정 규칙(체류 난이도,
 * 사다리, 기록되지 않은 문항 제외)은 그쪽과 같은 것을 쓴다.
 */
@Service
public class ReissueProposalService {

    /** 유사·응용 한 세트의 문항 수. 판정 규칙(서로 다른 문항 2개)과 맞춘 값이다. */
    private static final int SET_SIZE = ReissueSelector.SET_SIZE;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AnalysisAttemptRepository attempts;
    private final QuestionBank bank;
    private final BankUnitCrosswalk crosswalk;

    public ReissueProposalService(AnalysisAttemptRepository attempts,
                                  QuestionBank bank, BankUnitCrosswalk crosswalk) {
        this.attempts = attempts;
        this.bank = bank;
        this.crosswalk = crosswalk;
    }

    /** 개념별 세 칸의 문항 수 제안과, 세 칸을 아우르는 선정 이유. */
    @Transactional(readOnly = true)
    public Proposal propose(String assessmentId, String studentId) {
        List<ConceptFocus> focuses = analyze(assessmentId, studentId);
        List<Config> configs = new ArrayList<>();
        for (ConceptFocus focus : focuses) {
            Map<ReissueStage, Integer> counts = new LinkedHashMap<>();
            Map<ReissueStage, Integer> available = new LinkedHashMap<>();
            for (ReissueStage stage : ReissueStage.values()) {
                counts.put(stage, focus.proposedCount(stage, SET_SIZE));
                available.put(stage, available(focus, stage));
            }
            configs.add(new Config(focus, counts, available));
        }
        return new Proposal(studentId, configs, ReissueReason.of(configs));
    }

    /**
     * 조정된 문항 수로 실제 문항을 고른다.
     *
     * <p>복습은 뱅크를 보지 않는다. 기록되지 않은 원본 문항을 그대로 다시 낸다. 응용은 생성이
     * 필요해 아직 문항이 나가지 않고, 요청된 수만 그대로 알린다.
     */
    @Transactional(readOnly = true)
    public Generated generate(String assessmentId, String studentId, List<Request> requests) {
        if (bank.isEmpty()) {
            throw new IllegalStateException(bank.missingMessage());
        }
        Map<String, ConceptFocus> byConcept = new LinkedHashMap<>();
        for (ConceptFocus focus : analyze(assessmentId, studentId)) {
            byConcept.put(focus.conceptId(), focus);
        }
        Set<String> served = servedIds(load(assessmentId, studentId));

        List<Picked> picked = new ArrayList<>();
        int pendingApplied = 0;
        for (Request request : requests) {
            ConceptFocus focus = byConcept.get(request.conceptId());
            if (focus == null) {
                throw new NoSuchElementException(
                        "이 회차에서 관찰되지 않은 개념입니다: " + request.conceptId());
            }
            pendingApplied += request.count(ReissueStage.INDEPENDENT);

            int retrace = Math.min(request.count(ReissueStage.RETRACE),
                    focus.lostProblemIds().size());
            for (int i = 0; i < retrace; i++) {
                picked.add(Picked.retrace(focus, focus.lostProblemIds().get(i)));
            }

            int basic = request.count(ReissueStage.BASIC);
            if (basic > 0) {
                var target = new ReissueSelector.ReissueTarget(
                        focus.bankUnit(), focus.nextDifficulty());
                var result = ReissueSelector.select(
                        bank.questions(), target, served, studentId, basic);
                for (BankQuestion question : result.questions()) {
                    // 같은 요청 안에서 개념이 둘이어도 같은 문항이 두 번 나가지 않게 한다.
                    served.add(question.id());
                    picked.add(Picked.basic(focus, question));
                }
            }
        }
        return new Generated(studentId, picked, pendingApplied);
    }

    /**
     * 회차를 개념·단계로 묶어 각각의 상태와 체류 난이도를 읽는다.
     *
     * <p>기록되지 않은 문항은 판정에서 뺀다. 학생이 틀린 것이 아니라 재지 못한 것이라, 오답으로
     * 세면 없는 취약점을 만들어 낸다. 대신 그 문항 목록은 복습 칸의 근거로 따로 들고 간다.
     */
    private List<ConceptFocus> analyze(String assessmentId, String studentId) {
        List<AnalysisAttempt> rows = load(assessmentId, studentId);
        if (rows.isEmpty()) {
            throw new NoSuchElementException(
                    "저장된 응답이 없습니다: " + assessmentId + " / " + studentId);
        }

        Map<String, List<AnalysisAttempt>> byStep = new LinkedHashMap<>();
        for (AnalysisAttempt row : rows) {
            byStep.computeIfAbsent(row.getConceptId() + "::" + row.getStepId(),
                    ignored -> new ArrayList<>()).add(row);
        }

        List<ConceptFocus> focuses = new ArrayList<>();
        for (List<AnalysisAttempt> group : byStep.values()) {
            List<String> lost = group.stream()
                    .filter(AnalysisAttempt::isSubmissionFailed)
                    .map(AnalysisAttempt::getProblemId)
                    .distinct().toList();
            List<AnalysisAttempt> answered = group.stream()
                    .filter(row -> !row.isSubmissionFailed())
                    .toList();

            AnalysisAttempt first = group.get(0);
            String bankUnit = crosswalk.bankUnit(first.getConceptId());
            if (bankUnit == null) {
                // 대응이 없는 개념은 뱅크에서 문항을 고를 수 없다. 넘겨짚지 않고 건너뛴다.
                continue;
            }

            LearningState state = answered.isEmpty() ? null
                    : WeaknessAnalyzer.analyze(answered.stream()
                            .map(row -> toAttemptResult(row, studentId)).toList());
            QuestionDifficulty dwell = answered.isEmpty()
                    ? QuestionDifficulty.MEDIUM : ReissueService.dwellDifficulty(answered);
            QuestionDifficulty next = state == null
                    ? dwell : ReissueService.nextDifficulty(state.status(), dwell);

            focuses.add(new ConceptFocus(
                    first.getConceptId(), first.getStepId(), bankUnit, state, dwell, next,
                    lost,
                    answered.stream().filter(row -> !row.isCorrect())
                            .map(AnalysisAttempt::getProblemId).distinct().toList(),
                    mostCommonArea(answered), firstWrongStage(answered)));
        }

        // 나쁜 상태를 위로 올린다. 교사가 표를 위에서부터 읽는다.
        focuses.sort(Comparator.comparingInt(ReissueProposalService::rank).reversed());
        return focuses;
    }

    /** 그 칸에서 실제로 고를 수 있는 문항 수. 재고가 마르는지 화면에서 바로 보인다. */
    private int available(ConceptFocus focus, ReissueStage stage) {
        return switch (stage) {
            case RETRACE -> focus.lostProblemIds().size();
            case BASIC -> bank.isEmpty() ? 0 : ReissueSelector.select(
                    bank.questions(),
                    new ReissueSelector.ReissueTarget(focus.bankUnit(), focus.nextDifficulty()),
                    Set.of(), null, 0).candidateCount();
            // 응용은 생성이라 재고라는 개념이 없다. 미구현임을 0 으로 알린다.
            case INDEPENDENT -> 0;
        };
    }

    private List<AnalysisAttempt> load(String assessmentId, String studentId) {
        return attempts
                .findByAssessmentIdAndStudentIdOrderByProblemNumberAscOccurredAtAscEventIdAsc(
                        assessmentId, studentId);
    }

    private static AttemptResult toAttemptResult(AnalysisAttempt row, String studentId) {
        return new AttemptResult(row.getEventId(), studentId, row.getProblemId(),
                row.getConceptId(), row.getStepId(), row.isCorrect(), row.isHintUsed(),
                row.getOccurredAt(), row.getPurpose());
    }

    private static int rank(ConceptFocus focus) {
        if (focus.state() == null) {
            return 4_000_000;
        }
        int base = switch (focus.state().status()) {
            case NEEDS_SUPPORT -> 3_000_000;
            case WATCH -> 2_000_000;
            case IMPROVED -> 1_000_000;
            case CLEAR -> 0;
        };
        return base + focus.state().errorCount();
    }

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
                .map(Map.Entry::getKey).orElse(null);
    }

    private static String firstWrongStage(List<AnalysisAttempt> group) {
        for (AnalysisAttempt row : group) {
            if (row.isCorrect()) {
                continue;
            }
            try {
                String raw = row.getStepResponsesJson();
                for (JsonNode step : JSON.readTree(raw == null || raw.isBlank() ? "[]" : raw)) {
                    if (!step.path("correct").asBoolean(true)) {
                        String category = step.path("category").asText(null);
                        return category == null || category.isBlank() ? null : category;
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("풀이 단계 JSON을 읽을 수 없습니다.", e);
            }
        }
        return null;
    }

    private static Set<String> servedIds(List<AnalysisAttempt> rows) {
        Set<String> served = new LinkedHashSet<>();
        for (AnalysisAttempt row : rows) {
            if (row.getProblemId() != null) {
                served.add(row.getProblemId());
            }
        }
        return served;
    }

    /** 교사가 조정해 보내는 값. 개념 하나와 칸별 문항 수다. */
    public record Request(String conceptId, Map<ReissueStage, Integer> counts) {
        public Request {
            counts = counts == null ? Map.of() : Map.copyOf(counts);
        }

        int count(ReissueStage stage) {
            return Math.max(0, counts.getOrDefault(stage, 0));
        }
    }

    /**
     * @param available 그 칸에서 실제로 고를 수 있는 문항 수. 제안값보다 적으면 재고가 모자란다.
     */
    public record Config(ConceptFocus focus,
                         Map<ReissueStage, Integer> counts,
                         Map<ReissueStage, Integer> available) {
    }

    public record Proposal(String studentId, List<Config> configs, String reason) {
        public int totalCount() {
            return configs.stream()
                    .flatMap(config -> config.counts().values().stream())
                    .mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * @param pendingAppliedCount 요청됐지만 생성이 미구현이라 나가지 못한 응용 문항 수.
     */
    public record Generated(String studentId, List<Picked> questions, int pendingAppliedCount) {
    }

    /** 뽑힌 문항 하나. 복습은 원본 문항이라 뱅크 정보가 없다. */
    public record Picked(ReissueStage stage, String conceptId, String questionId,
                         String prompt, String unitName, String difficultyBand,
                         int blankCount, String reason) {

        static Picked retrace(ConceptFocus focus, String problemId) {
            return new Picked(ReissueStage.RETRACE, focus.conceptId(), problemId,
                    null, focus.bankUnit(), focus.dwell().band(), 0,
                    "시스템 오류로 답이 기록되지 않아 원본 문항을 그대로 다시 냅니다.");
        }

        static Picked basic(ConceptFocus focus, BankQuestion question) {
            return new Picked(ReissueStage.BASIC, focus.conceptId(), question.id(),
                    question.promptText(), question.unitName(),
                    question.difficulty().band(), question.stages().size(),
                    "같은 소단원 " + question.unitName() + "의 "
                            + DisplayLabels.difficulty(question.difficulty().band())
                            + " 난이도에서 아직 내지 않은 문항입니다. 빈칸 "
                            + question.stages().size() + "개라 먼저 골랐습니다.");
        }
    }
}
