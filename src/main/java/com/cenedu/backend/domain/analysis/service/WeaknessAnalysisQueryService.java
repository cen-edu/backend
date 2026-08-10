package com.cenedu.backend.domain.analysis.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.analysis.dto.WorksheetDetail;
import com.cenedu.backend.domain.analysis.dto.WorksheetSummary;
import com.cenedu.backend.domain.analysis.entity.AnalysisAssessment;
import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;
import com.cenedu.backend.domain.analysis.repository.AnalysisAssessmentRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisAttemptRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 저장된 풀이를 취약점 분석 화면의 worksheet 구조로 바꾼다. */
@Service
@Transactional(readOnly = true)
public class WeaknessAnalysisQueryService {

    private static final int STABLE_RATE = 80;
    private static final int REVIEW_RATE = 60;

    /** 학년·반은 아직 백엔드에 없다. member 도메인이 생기면 실제 값으로 바꾼다. */
    private static final String GRADE_ID = "middle-1";
    private static final String CLASS_ID = "middle-1-1";
    private static final String CLASS_NAME = "중학교 1학년 1반";

    private final AnalysisAssessmentRepository assessments;
    private final AnalysisAttemptRepository attempts;
    private final ObjectMapper json;

    public WeaknessAnalysisQueryService(AnalysisAssessmentRepository assessments,
                                        AnalysisAttemptRepository attempts,
                                        ObjectMapper json) {
        this.assessments = assessments;
        this.attempts = attempts;
        this.json = json;
    }

    public List<WorksheetSummary> worksheets() {
        Map<String, AnalysisAssessment> byAssessment = new LinkedHashMap<>();
        for (AnalysisAssessment row : assessments.findAllByOrderByAssessmentDateDescAssessmentIdAsc()) {
            byAssessment.putIfAbsent(row.getAssessmentId(), row);
        }
        return byAssessment.values().stream()
                .map(row -> new WorksheetSummary(
                        row.getAssessmentId(), row.getAssessmentTitle(), row.getAssessmentDate(),
                        worksheetType(row.getAssessmentType()),
                        GRADE_ID, CLASS_ID, term(row.getAssessmentDate()), CLASS_NAME))
                .toList();
    }

    /**
     * 화면 상단 요약 카드가 쓰는 값.
     *
     * <p>계산 규칙은 프론트 {@code getWorksheetMetrics} 와 같다. 보고서가 화면과 다른 숫자를
     * 말하지 않게 하려는 것이므로, 규칙을 여기서 "개선"하지 않는다.
     */
    public WorksheetMetrics metrics(String assessmentId) {
        return metricsOf(worksheet(assessmentId));
    }

    static WorksheetMetrics metricsOf(WorksheetDetail worksheet) {
        // 자료가 부족한 학생은 평균과 취약 판정에서 뺀다. 낸 문항을 다 풀지 않은 학생의
        // 정답률을 그대로 섞으면 학급 평균이 실제보다 낮아 보인다.
        List<WorksheetDetail.Student> reliable = worksheet.students().stream()
                .filter(student -> !"insufficient".equals(student.status()))
                .toList();

        int weakConcepts = (int) worksheet.concepts().stream()
                .map(concept -> conceptMastery(worksheet, reliable, concept.id()))
                .filter(mastery -> mastery.total() > 0 && mastery.masteryRate() < REVIEW_RATE)
                .count();

        int priority = (int) reliable.stream()
                .filter(student -> scoreRate(student) < REVIEW_RATE)
                .count();

        int average = reliable.isEmpty() ? 0
                : (int) Math.round(reliable.stream()
                        .mapToInt(WeaknessAnalysisQueryService::scoreRate).average().orElse(0));

        return new WorksheetMetrics(
                worksheet.students().size(), reliable.size(), average, weakConcepts, priority);
    }

    /**
     * 한 개념에 걸린 구간의 달성도.
     *
     * <p>분모는 <b>도달한 구간이 아니라 문항 구성 전체</b>다. 앞에서 막혀 못 간 구간도 해내지
     * 못한 것으로 센다. 도달한 구간만 세면 앞에서 막힌 학생일수록 달성률이 높게 나와, 정작
     * 취약한 개념이 판정선을 넘어 목록에서 빠진다.
     */
    private static ConceptMastery conceptMastery(WorksheetDetail worksheet,
                                                 List<WorksheetDetail.Student> students,
                                                 String conceptId) {
        int total = 0;
        int correct = 0;
        for (WorksheetDetail.Student student : students) {
            for (WorksheetDetail.Question question : worksheet.questions()) {
                for (WorksheetDetail.QuestionStep step : question.steps()) {
                    if (conceptId == null || !conceptId.equals(step.conceptId())) {
                        continue;
                    }
                    total++;
                    WorksheetDetail.ResponseStep answered = findStep(student, question, step);
                    if (answered != null && answered.attempted() && answered.correct()) {
                        correct++;
                    }
                }
            }
        }
        int rate = total == 0 ? 0 : (int) Math.round(correct * 100.0 / total);
        return new ConceptMastery(total, rate);
    }

    private static WorksheetDetail.ResponseStep findStep(WorksheetDetail.Student student,
                                                         WorksheetDetail.Question question,
                                                         WorksheetDetail.QuestionStep step) {
        return student.responses().stream()
                .filter(response -> response.no() == question.no())
                .flatMap(response -> response.steps().stream())
                .filter(item -> item.order() == step.order())
                .findFirst()
                .orElse(null);
    }

    /** 채점된 응답만 분모로 센다. 채점 대기 문항을 틀린 것으로 세지 않는다. */
    private static int scoreRate(WorksheetDetail.Student student) {
        List<WorksheetDetail.Response> graded = student.responses().stream()
                .filter(response -> response.gradedBy() != null)
                .toList();
        if (graded.isEmpty()) {
            return 0;
        }
        long correct = graded.stream()
                .filter(response -> response.score() == response.maxScore())
                .count();
        return (int) Math.round(correct * 100.0 / graded.size());
    }

    /** 화면 상단 요약 카드 값. */
    public record WorksheetMetrics(int responseCount, int reliableCount, int average,
                                   int weakConceptCount, int priorityCount) {
    }

    private record ConceptMastery(int total, int masteryRate) {
    }

    public WorksheetDetail worksheet(String assessmentId) {
        List<AnalysisAssessment> rows = assessments.findByAssessmentIdOrderByStudentName(assessmentId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND,
                    "취약점 분석 데이터를 찾을 수 없습니다: " + assessmentId);
        }

        // 문항 순서를 먼저 정하고, 학생별 응답을 그 순서에 맞춘다. 학생마다 푼 문항이 달라도
        // 화면의 매트릭스가 같은 열 수를 갖게 해야 한다.
        Map<Integer, AnalysisAttempt> questionRows = new LinkedHashMap<>();
        Map<String, String> conceptLabels = new LinkedHashMap<>();
        Map<String, List<AnalysisAttempt>> studentRows = new LinkedHashMap<>();
        for (AnalysisAssessment row : rows) {
            List<AnalysisAttempt> studentAttempts = attempts
                    .findByAssessmentIdAndStudentIdOrderByProblemNumberAscOccurredAtAscEventIdAsc(
                            assessmentId, row.getStudentId());
            if (studentAttempts.isEmpty()) {
                continue;
            }
            studentRows.put(row.getStudentId(), studentAttempts);
            for (AnalysisAttempt attempt : studentAttempts) {
                questionRows.putIfAbsent(attempt.getProblemNumber(), attempt);
                conceptLabels.putIfAbsent(attempt.getConceptId(), displayConcept(attempt));
            }
        }
        if (studentRows.isEmpty()) {
            throw new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND,
                    "취약점 분석 데이터를 찾을 수 없습니다: " + assessmentId);
        }

        List<WorksheetDetail.Question> questions = questionRows.values().stream()
                .sorted((a, b) -> Integer.compare(a.getProblemNumber(), b.getProblemNumber()))
                .map(this::toQuestion)
                .toList();
        List<WorksheetDetail.Concept> concepts = conceptLabels.entrySet().stream()
                .map(entry -> new WorksheetDetail.Concept(entry.getKey(), entry.getValue()))
                .toList();

        Map<String, String> namesById = new LinkedHashMap<>();
        rows.forEach(row -> namesById.putIfAbsent(row.getStudentId(), row.getStudentName()));
        List<WorksheetDetail.Student> students = studentRows.entrySet().stream()
                .map(entry -> toStudent(entry.getKey(), namesById.get(entry.getKey()),
                        entry.getValue(), questions))
                .toList();

        AnalysisAssessment header = rows.get(0);
        LocalDate date = header.getAssessmentDate();
        return new WorksheetDetail(
                assessmentId, GRADE_ID, CLASS_ID, term(date),
                worksheetType(header.getAssessmentType()), "manual",
                header.getAssessmentTitle(), CLASS_NAME, date,
                concepts, questions, students);
    }

    private WorksheetDetail.Question toQuestion(AnalysisAttempt attempt) {
        List<StoredStep> stored = readSteps(attempt.getStepResponsesJson());
        List<WorksheetDetail.QuestionStep> steps = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            steps.add(new WorksheetDetail.QuestionStep(
                    stored.get(index).stepId(), index + 1,
                    attempt.getConceptId(), stored.get(index).stepName()));
        }
        return new WorksheetDetail.Question(
                attempt.getProblemId(), attempt.getProblemNumber(), attempt.getConceptId(),
                safeDifficulty(attempt.getDifficultyBand()),
                normalizeArea(attempt.getEvaluationArea()),
                nonBlank(attempt.getProblemText(), attempt.getProblemTitle()),
                nonBlank(attempt.getCorrectAnswer(), "정답 정보 없음"), 1,
                responseFormat(attempt.getResponseType()), "complete", steps);
    }

    private WorksheetDetail.Student toStudent(String studentId, String studentName,
                                              List<AnalysisAttempt> studentAttempts,
                                              List<WorksheetDetail.Question> questions) {
        Map<Integer, AnalysisAttempt> byNumber = new LinkedHashMap<>();
        studentAttempts.forEach(attempt -> byNumber.put(attempt.getProblemNumber(), attempt));

        List<WorksheetDetail.Response> responses = questions.stream()
                .map(question -> {
                    AnalysisAttempt attempt = byNumber.get(question.no());
                    return attempt == null
                            ? new WorksheetDetail.Response(
                                    question.no(), 0, 1, false, 0, null, "", List.of())
                            : toResponse(attempt);
                })
                .toList();

        int correct = (int) studentAttempts.stream().filter(AnalysisAttempt::isCorrect).count();
        int rate = studentAttempts.isEmpty()
                ? 0 : (int) Math.round(correct * 100.0 / studentAttempts.size());
        // 낸 문항을 다 풀지 않았으면 정답률로 상태를 정하지 않는다. 3문항 중 1개만 맞힌 것과
        // 10문항 중 3개를 맞힌 것을 같은 33%로 읽으면 안 된다.
        String status = studentAttempts.size() < questions.size()
                ? "insufficient" : rate >= STABLE_RATE ? "stable"
                : rate >= REVIEW_RATE ? "review" : "priority";
        String nextAction = switch (status) {
            case "stable" -> "다음 학습 진행";
            case "insufficient" -> "추가 응답 확인";
            default -> "오답 문항 확인";
        };
        return new WorksheetDetail.Student(
                studentId, studentName, status, nextAction, List.of(), responses);
    }

    private WorksheetDetail.Response toResponse(AnalysisAttempt attempt) {
        List<StoredStep> stored = readSteps(attempt.getStepResponsesJson());
        List<WorksheetDetail.ResponseStep> steps = new ArrayList<>();
        int reached = reachedThrough(stored);
        for (int index = 0; index < stored.size(); index++) {
            steps.add(new WorksheetDetail.ResponseStep(
                    index + 1, stored.get(index).correct(),
                    nonBlank(stored.get(index).studentAnswer(), ""), index <= reached));
        }
        return new WorksheetDetail.Response(
                attempt.getProblemNumber(), attempt.isCorrect() ? 1 : 0, 1,
                attempt.isHintUsed(), 0, "system",
                nonBlank(attempt.getStudentAnswer(), ""), steps);
    }

    /**
     * 학생이 몇 번째 구간까지 손을 댔는지 돌려준다. 반환값은 0부터 세는 마지막 도달 위치다.
     *
     * <p>구간별 답이 비어 있다는 것과 그 구간을 못 했다는 것은 다르다. 빈칸을 안 푼 것으로 보고
     * 모수에서 빼면 분모가 "얼마나 멀리 갔는가"가 되어, 앞에서 막힌 학생일수록 달성률이 높게
     * 나온다. 실제로 그 계산에서는 최하위 학생이 23%p 부풀려졌다.
     *
     * <p>도달 지점은 두 가지로 판단한다. 답을 쓴 구간은 당연히 도달했고, 처음 틀린 구간도
     * 도달했다. 그 구간에서 막혀 뒤를 비운 것이므로 빈칸이어도 시도한 것으로 센다.
     */
    private static int reachedThrough(List<StoredStep> steps) {
        int lastAnswered = -1;
        int firstWrong = -1;
        for (int index = 0; index < steps.size(); index++) {
            if (!nonBlank(steps.get(index).studentAnswer(), "").isBlank()) {
                lastAnswered = index;
            }
            if (firstWrong < 0 && !steps.get(index).correct()) {
                firstWrong = index;
            }
        }
        return Math.max(lastAnswered, firstWrong);
    }

    private List<StoredStep> readSteps(String raw) {
        try {
            JsonNode root = json.readTree(raw == null || raw.isBlank() ? "[]" : raw);
            List<StoredStep> steps = new ArrayList<>();
            for (JsonNode node : root) {
                steps.add(new StoredStep(
                        node.path("stepId").asString(""), node.path("stepName").asString(""),
                        node.path("studentAnswer").asString(""),
                        node.path("correct").asBoolean(false)));
            }
            return steps;
        } catch (Exception e) {
            throw new IllegalStateException("풀이 단계 JSON을 읽을 수 없습니다.", e);
        }
    }

    private static String worksheetType(String assessmentType) {
        String value = assessmentType == null ? "" : assessmentType.toUpperCase();
        return value.contains("SUMMATIVE") || value.contains("ASSESSMENT")
                ? "assessment" : "practice";
    }

    private static String term(LocalDate date) {
        return date != null && date.getMonthValue() >= 7 ? "second" : "first";
    }

    private static String normalizeArea(String area) {
        return area != null
                && List.of("concept", "calculation", "reasoning", "problemSolving").contains(area)
                ? area : "concept";
    }

    private static String safeDifficulty(String difficulty) {
        return difficulty != null && List.of("low", "mid", "high").contains(difficulty)
                ? difficulty : "mid";
    }

    private static String responseFormat(String responseType) {
        return switch (responseType == null ? "" : responseType.toUpperCase()) {
            case "MULTIPLE_CHOICE", "CHOICE" -> "choice";
            case "ESSAY" -> "essay";
            default -> "short";
        };
    }

    private static String displayConcept(AnalysisAttempt attempt) {
        return nonBlank(attempt.getTopic(), attempt.getConceptId());
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record StoredStep(String stepId, String stepName,
                              String studentAnswer, boolean correct) {
    }
}
