package com.cenedu.backend.domain.worksheet.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.grading.entity.GradingRubricResult;
import com.cenedu.backend.domain.grading.repository.GradingRubricResultRepository;
import com.cenedu.backend.domain.problem.dto.response.ProblemAssetResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemContentBlockResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemStepSegmentResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import com.cenedu.backend.domain.problem.entity.ProblemStep;
import com.cenedu.backend.domain.problem.repository.ProblemAnswerUnitRepository;
import com.cenedu.backend.domain.problem.repository.ProblemChoiceRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemStepRepository;
import com.cenedu.backend.domain.problem.service.ProblemQuestionDetailService;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.submission.repository.SubmissionAnswerRepository;
import com.cenedu.backend.domain.worksheet.dto.response.StudentContentBlockResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultAnswerUnitResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultChatContextResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultConceptResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultExplanationResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultItemResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultStepResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentRubricItemResponse;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import com.cenedu.backend.global.common.enums.QuestionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 학생 채점 결과 조회. 판정 코드 산출은 명세 2.6절을 문항 단위로 접어 계산한다(§7-3).
 *
 * <p>task_05(채점)가 아직 없어 실 데이터로 전 경로를 검증할 수 없다 — 수동 삽입 데이터로만
 * 확인했다(§7-1). {@code totalScore}/{@code maxTotalScore}는 저장된 비정규화 값
 * ({@code worksheet_assignment_student.total_score})을 쓰지 않고 문항 점수 합으로 직접
 * 계산한다 — 그 컬럼은 "종합평가 전용"이라 일반학습에선 비어 있을 수 있어, 응답에 실제로
 * 나가는 문항별 점수와 항상 같은 축이 되도록 여기서 다시 더한다.
 *
 * <p><b>TODO(배세빈, 도메인 경계 정리):</b> Problem·Submission·Grading Repository와 Entity를
 * 직접 참조하는 현재 조회는 AGENTS.md 3절 1·2번과 맞지 않는다. 각 소유 도메인의 공개 배치
 * Response/Service로 교체하고, S2로 최종화된 문항은
 * {@link com.cenedu.backend.domain.problem.service.ProblemSnapshotQueryService#getFinalized(long)}를
 * 통해 저장 당시 S1을 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentResultQueryService {

    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final ProblemQuestionRepository problemQuestionRepository;
    private final ProblemChoiceRepository problemChoiceRepository;
    private final ProblemAnswerUnitRepository problemAnswerUnitRepository;
    private final ProblemStepRepository problemStepRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final GradingRubricResultRepository gradingRubricResultRepository;
    private final ProblemQuestionDetailService problemQuestionDetailService;
    private final ObjectMapper objectMapper;

    public StudentResultResponse getResult(long studentId, long assignmentStudentId) {
        WorksheetAssignmentStudent was = worksheetAssignmentStudentRepository.findDetailById(assignmentStudentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND));
        if (was.getStudentId() != studentId) {
            throw new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND);
        }
        // 점수·정답·해설·루브릭 어느 것도 담기 전에 검사한다(명세 7-2/8절).
        boolean notSubmitted = isNotSubmitted(was);
        if (!notSubmitted && was.getReleasedAt() == null) {
            throw new BusinessException(ErrorCode.WORKSHEET_RESULT_NOT_RELEASED);
        }
        // 미제출자 행에는 확정이 released_at을 채우지 않아(교사 채점 10절) 형제 행으로 반 확정을 본다.
        boolean disclose = !notSubmitted
                || worksheetAssignmentStudentRepository
                        .existsByAssignment_IdAndReleasedAtIsNotNull(was.getAssignment().getId());

        long worksheetId = was.getAssignment().getWorksheet().getId();
        List<WorksheetItem> items = worksheetItemRepository
                .findAllByWorksheetIdOrderByDisplayOrderAsc(worksheetId);
        if (items.isEmpty()) {
            return StudentResultResponse.from(was, List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();
        Map<Long, ProblemQuestion> questionsById = problemQuestionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(ProblemQuestion::getId, question -> question));
        Map<Long, List<ProblemChoice>> choicesByQuestionId = problemChoiceRepository
                .findAllByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(choice -> choice.getQuestion().getId()));
        Map<Long, List<ProblemAnswerUnit>> unitsByQuestionId = problemAnswerUnitRepository
                .findAllByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(unit -> unit.getQuestion().getId()));
        Map<Long, List<ProblemRubricItem>> rubricItemsByQuestionId = gradingRubricResultRepository
                .findRubricItemsByQuestionIdIn(questionIds).stream()
                .collect(Collectors.groupingBy(item -> item.getQuestion().getId()));
        // 모범 풀이는 공개 대상일 때만 필요하다. 가릴 응답이면 조회 자체를 하지 않는다.
        Map<Long, List<ProblemStep>> stepsByQuestionId = disclose
                ? problemStepRepository.findAllByQuestionIds(questionIds).stream()
                        .collect(Collectors.groupingBy(step -> step.getQuestion().getId()))
                : Map.of();

        Map<Long, SubmissionAnswer> savedByAnswerUnitId = submissionAnswerRepository
                .findByAssignmentStudentId(assignmentStudentId).stream()
                .collect(Collectors.toMap(SubmissionAnswer::getAnswerUnitId, answer -> answer));

        Map<Long, List<ProblemAssetResponse>> assetsByQuestionId = problemQuestionDetailService
                .getAssetsByQuestionIds(questionIds);

        List<Long> submissionAnswerIds = savedByAnswerUnitId.values().stream()
                .map(SubmissionAnswer::getId)
                .toList();
        Map<Long, List<GradingRubricResult>> rubricResultsByAnswerId = submissionAnswerIds.isEmpty()
                ? Map.of()
                : gradingRubricResultRepository.findByStudentAnswerIdIn(submissionAnswerIds).stream()
                        .collect(Collectors.groupingBy(GradingRubricResult::getStudentAnswerId));

        List<StudentResultItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxTotalScore = BigDecimal.ZERO;

        for (WorksheetItem item : items) {
            ProblemQuestion question = questionsById.get(item.getQuestionId());
            List<ProblemChoice> choices = choicesByQuestionId.getOrDefault(item.getQuestionId(), List.of());
            List<ProblemAnswerUnit> units = unitsByQuestionId.getOrDefault(item.getQuestionId(), List.of());
            BigDecimal maxScorePerUnit = item.getMaxScore() != null ? item.getMaxScore() : BigDecimal.ONE;

            List<StudentResultAnswerUnitResponse> unitResponses = new ArrayList<>();
            List<String> unitResults = new ArrayList<>();
            BigDecimal itemScore = BigDecimal.ZERO;

            for (ProblemAnswerUnit unit : units) {
                SubmissionAnswer answer = savedByAnswerUnitId.get(unit.getId());
                String unitResult = classify(answer, maxScorePerUnit);
                unitResults.add(unitResult);

                BigDecimal score = (answer != null
                        && answer.getGradingStatus() == GradingStatus.GRADED
                        && answer.getFinalScore() != null)
                        ? answer.getFinalScore()
                        : BigDecimal.ZERO;
                itemScore = itemScore.add(score);

                unitResponses.add(new StudentResultAnswerUnitResponse(
                        unit.getId(),
                        unit.getDisplayOrder(),
                        resolveMyAnswer(question.getQuestionType(), answer, choices),
                        disclose ? resolveCorrectAnswer(question.getQuestionType(), unit, choices) : null,
                        unitResult,
                        score,
                        answer != null && answer.getAnswerImageRef() != null));
            }

            String itemResult = aggregateItemResult(unitResults);
            BigDecimal itemMaxScore = item.getMaxScore() != null
                    ? item.getMaxScore()
                    : maxScorePerUnit.multiply(BigDecimal.valueOf(units.size()));

            List<StudentRubricItemResponse> rubric = question.getQuestionType() == QuestionType.ESSAY
                    ? buildRubric(question.getId(), units, savedByAnswerUnitId, rubricItemsByQuestionId,
                            rubricResultsByAnswerId)
                    : null;

            totalScore = totalScore.add(itemScore);
            maxTotalScore = maxTotalScore.add(itemMaxScore);

            StudentResultConceptResponse concept = disclose ? parseConcept(question) : null;
            StudentResultExplanationResponse explanation = disclose
                    ? buildExplanation(question, units, choices, unitResponses,
                            stepsByQuestionId.getOrDefault(item.getQuestionId(), List.of()), concept)
                    : null;
            StudentResultChatContextResponse chatContext = disclose
                    ? new StudentResultChatContextResponse(
                            question.getSubUnitId(), concept == null ? null : concept.title())
                    : null;

            itemResponses.add(StudentResultItemResponse.from(
                    item, question, itemResult, itemScore, itemMaxScore,
                    parseContentBlocks(question.getContentBlocks()),
                    explanation, chatContext, unitResponses, rubric,
                    assetsByQuestionId.getOrDefault(question.getId(), List.of())));
        }

        return StudentResultResponse.from(was, itemResponses, totalScore, maxTotalScore);
    }

    /**
     * 복습 화면의 해설 묶음(명세 8.4). 프론트가 필드 유무로 섹션을 켜고 끄므로 없는 것은
     * {@code null}로 둔다.
     *
     * <p>{@code answerText}는 이미 만들어 둔 칸별 정답을 이어 붙인다 — 정답 해석 로직을 두 벌
     * 만들면 객관식 1-based 순번 처리가 한쪽에만 들어가 화면마다 다른 값이 나온다.
     *
     * <p>{@code steps}는 <b>문항 형식</b>이 가른다. 학습지 유형이 아니다 —
     * {@code problem_step} 행을 가지는 형식이 빈칸형뿐이고(실측), 종합평가에도 빈칸형 문항이
     * 들어간다. 유형으로 가르면 그 문항들이 모범 풀이를 잃는다.
     *
     * <p><b>명세({@code api_student.md} 8.4)는 "종합평가 → steps null"로 유형을 축으로 삼는다.
     * 여기서 다르게 둔 것은 의도이며 팀이 확정했다</b> — 배포된 종합평가 빈칸형 문항이 단계별
     * 서술을 잃기 때문이다. 명세와 맞추려고 되돌리지 않는다.
     */
    private StudentResultExplanationResponse buildExplanation(
            ProblemQuestion question,
            List<ProblemAnswerUnit> units,
            List<ProblemChoice> choices,
            List<StudentResultAnswerUnitResponse> unitResponses,
            List<ProblemStep> steps,
            StudentResultConceptResponse concept
    ) {
        String answerText = unitResponses.stream()
                .map(StudentResultAnswerUnitResponse::correctAnswer)
                .filter(answer -> answer != null && !answer.isBlank())
                .collect(Collectors.joining(" · "));

        Map<String, String> answerByUnitKey = new HashMap<>();
        for (ProblemAnswerUnit unit : units) {
            String answer = resolveCorrectAnswer(question.getQuestionType(), unit, choices);
            if (answer != null) {
                answerByUnitKey.put(unit.getUnitKey(), answer);
            }
        }
        List<StudentResultStepResponse> stepResponses = steps.isEmpty()
                ? null
                : steps.stream()
                        .map(step -> new StudentResultStepResponse(
                                step.getLabel(), assembleFormula(step, answerByUnitKey)))
                        .toList();

        return new StudentResultExplanationResponse(
                answerText.isEmpty() ? null : answerText,
                question.getExplanation(),
                stepResponses,
                concept);
    }

    /**
     * 모범 풀이 수식. 빈칸을 <b>정답</b>으로 채운다 — 복습 화면은 "이렇게 푸는 것이었다"를
     * 보여주는 자리라, 학생 답을 끼우면 틀린 풀이가 모범 풀이 자리에 앉는다.
     *
     * <p>{@code ANSWER_REF}도 정답으로 채운다. 풀이 화면에서 같은 세그먼트는 "앞 단계에서 학생이
     * 쓴 현재 값"을 가리키지만({@link com.cenedu.backend.domain.worksheet.dto.response
     * .StudentSegmentResponse}), 여기서는 참조 대상 칸의 정답이 들어간다. 같은 데이터가 화면에
     * 따라 다르게 해석되는 지점이다.
     */
    private String assembleFormula(ProblemStep step, Map<String, String> answerByUnitKey) {
        StringBuilder formula = new StringBuilder();
        for (ProblemStepSegmentResponse segment : parseSegments(step.getSegments())) {
            if ("TEXT".equals(segment.type())) {
                formula.append(segment.value() == null ? "" : segment.value());
            } else {
                formula.append(answerByUnitKey.getOrDefault(segment.unitKey(), ""));
            }
        }
        return formula.toString();
    }

    /**
     * 개념 정리(명세 8.5). {@code learning_guide} jsonb에서 <b>세 키만 골라</b> 읽는다 —
     * 통째로 역직렬화하면 내부 출처({@code source.datasets})와 품질 등급({@code status})이
     * 함께 실린다.
     */
    private StudentResultConceptResponse parseConcept(ProblemQuestion question) {
        String learningGuide = question.getLearningGuide();
        if (learningGuide == null || learningGuide.isBlank()) {
            return null;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(learningGuide);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
        List<String> points = new ArrayList<>();
        for (JsonNode point : node.path("keyPoints")) {
            points.add(point.asString());
        }
        return new StudentResultConceptResponse(
                node.path("conceptTitle").asString(null),
                node.path("summary").asString(null),
                List.copyOf(points));
    }

    private List<ProblemStepSegmentResponse> parseSegments(String segments) {
        try {
            return objectMapper.readValue(segments, new TypeReference<List<ProblemStepSegmentResponse>>() {
            });
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
    }

    /**
     * 미제출 판정(명세 2.4 파생 규칙). {@code NOT_STARTED}인데 마감이 지났으면 DB가 아직
     * {@code NOT_SUBMITTED}로 확정하기 전이라 조회 시각으로 파생해야 한다 — 목록 응답의
     * {@code StudentResponseFormatter.toApiStatus}가 쓰는 규칙과 같은 축이다.
     *
     * <p>마감 전 {@code NOT_STARTED}는 미제출로 보지 않는다. 아직 낼 수 있는 상태라 결과를
     * 열어 줄 이유가 없고, 게이트가 그대로 409로 막는다.
     */
    private boolean isNotSubmitted(WorksheetAssignmentStudent was) {
        AssignmentStatus status = was.getStatus();
        if (status == AssignmentStatus.NOT_SUBMITTED) {
            return true;
        }
        return status == AssignmentStatus.NOT_STARTED
                && was.getAssignment().getDueAt().isBefore(OffsetDateTime.now());
    }

    /**
     * 칸 하나의 판정. {@code FAILED}는 학생에게 {@code pending}으로 보인다 — 시스템 오류를
     * 구분할 이유가 없다(명세 8절).
     */
    private String classify(SubmissionAnswer answer, BigDecimal maxScore) {
        if (answer != null
                && (answer.getGradingStatus() == GradingStatus.NOT_GRADED
                        || answer.getGradingStatus() == GradingStatus.FAILED)) {
            return "pending";
        }
        boolean hasValue = answer != null && (
                answer.getSelectedChoiceId() != null
                        || (answer.getRawLatex() != null && !answer.getRawLatex().isBlank())
                        || answer.getAnswerImageRef() != null);
        if (!hasValue) {
            return "empty";
        }
        BigDecimal finalScore = answer.getFinalScore() != null ? answer.getFinalScore() : BigDecimal.ZERO;
        if (finalScore.compareTo(maxScore) == 0) {
            return "correct";
        }
        if (finalScore.compareTo(BigDecimal.ZERO) == 0) {
            return "wrong";
        }
        return "partial";
    }

    /** 문항 판정 = 칸 판정들의 접기(명세 2.6). */
    private String aggregateItemResult(List<String> unitResults) {
        if (unitResults.stream().anyMatch("pending"::equals)) {
            return "pending";
        }
        if (unitResults.stream().allMatch("empty"::equals)) {
            return "empty";
        }
        if (unitResults.stream().allMatch("correct"::equals)) {
            return "correct";
        }
        if (unitResults.stream().allMatch("wrong"::equals)) {
            return "wrong";
        }
        return "partial";
    }

    /** 객관식은 고른 보기 텍스트, 서술형은 항상 null, 그 외는 raw_latex 그대로(명세 8절). */
    private String resolveMyAnswer(QuestionType questionType, SubmissionAnswer answer, List<ProblemChoice> choices) {
        if (answer == null || questionType == QuestionType.ESSAY) {
            return null;
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE) {
            if (answer.getSelectedChoiceId() == null) {
                return null;
            }
            return choices.stream()
                    .filter(choice -> choice.getId().equals(answer.getSelectedChoiceId()))
                    .findFirst()
                    .map(ProblemChoice::getContent)
                    .orElse(null);
        }
        return answer.getRawLatex();
    }

    /**
     * 정답. {@code answer_raw}는 서술형이면 null(모범답안 없음, 실측 확인). 객관식은
     * 1-based 표시 순서를 담고 있어(실측 확인) 보기 텍스트로 풀어야 한다 — 컬럼값을 그대로
     * 내보내면 학생 화면에 원시 순번이 나간다.
     */
    private String resolveCorrectAnswer(QuestionType questionType, ProblemAnswerUnit unit, List<ProblemChoice> choices) {
        String answerRaw = unit.getAnswerRaw();
        if (answerRaw == null) {
            return null;
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE) {
            int oneBasedOrder = Integer.parseInt(answerRaw);
            return choices.stream()
                    .filter(choice -> choice.getDisplayOrder() + 1 == oneBasedOrder)
                    .findFirst()
                    .map(ProblemChoice::getContent)
                    .orElse(null);
        }
        return answerRaw;
    }

    /**
     * 서술형 루브릭. 채점 실패(행 없음)면 {@code satisfied=false}로 채우지 않고 그대로 빈 배열을
     * 돌려준다 — 행 부재는 "판정 안 함"이지 "미충족"이 아니다(명세 8절, {@code GradingRubricResult}
     * 자바독).
     */
    private List<StudentRubricItemResponse> buildRubric(
            long questionId,
            List<ProblemAnswerUnit> units,
            Map<Long, SubmissionAnswer> savedByAnswerUnitId,
            Map<Long, List<ProblemRubricItem>> rubricItemsByQuestionId,
            Map<Long, List<GradingRubricResult>> rubricResultsByAnswerId
    ) {
        List<ProblemRubricItem> rubricItems = rubricItemsByQuestionId.getOrDefault(questionId, List.of());
        if (rubricItems.isEmpty()) {
            return List.of();
        }

        Long essayAnswerId = units.stream()
                .findFirst()
                .map(unit -> savedByAnswerUnitId.get(unit.getId()))
                .map(SubmissionAnswer::getId)
                .orElse(null);
        List<GradingRubricResult> results = essayAnswerId == null
                ? List.of()
                : rubricResultsByAnswerId.getOrDefault(essayAnswerId, List.of());
        // 행 부재 = 판정 안 함(채점 실패 또는 미채점) — satisfied=false로 채우지 않고 빈 배열을 낸다.
        if (results.isEmpty()) {
            return List.of();
        }

        Map<Long, Boolean> satisfiedByRubricItemId = results.stream()
                .collect(Collectors.toMap(GradingRubricResult::getRubricItemId, GradingRubricResult::isSatisfied));
        return rubricItems.stream()
                .sorted(Comparator.comparingInt(ProblemRubricItem::getDisplayOrder))
                .map(rubricItem -> StudentRubricItemResponse.from(
                        rubricItem, satisfiedByRubricItemId.getOrDefault(rubricItem.getId(), false)))
                .toList();
    }

    private List<StudentContentBlockResponse> parseContentBlocks(String contentBlocks) {
        try {
            List<ProblemContentBlockResponse> blocks = objectMapper.readValue(
                    contentBlocks, new TypeReference<List<ProblemContentBlockResponse>>() {
                    });
            return blocks.stream().map(StudentContentBlockResponse::from).toList();
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
    }
}
