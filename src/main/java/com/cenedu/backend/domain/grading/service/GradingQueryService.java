package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.grading.dto.request.GradingListRequest;
import com.cenedu.backend.domain.grading.dto.response.GradingAnswerUnitResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingCellResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingChoiceResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingContentBlockResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingSegmentResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingStepResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingDetailItemResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingQuestionResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingResponseFormatter;
import com.cenedu.backend.domain.grading.dto.response.GradingScoreTableResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingStudentDetailResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingStudentRowResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingWorksheetItemResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingWorksheetListResponse;
import com.cenedu.backend.domain.grading.entity.GradingRubricResult;
import com.cenedu.backend.domain.grading.repository.GradingRubricResultRepository;
import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.domain.member.service.StudentListQueryService;
import com.cenedu.backend.domain.problem.dto.response.ProblemAssetResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemContentBlockResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemStepSegmentResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import com.cenedu.backend.domain.problem.entity.ProblemStep;
import com.cenedu.backend.domain.problem.service.ProblemQuestionDetailService;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.domain.submission.entity.SubmissionQuestionTime;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.submission.repository.SubmissionAnswerRepository;
import com.cenedu.backend.domain.submission.repository.SubmissionQuestionTimeRepository;
import com.cenedu.backend.domain.submission.service.SubmissionImageService;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.global.common.enums.UserRole;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 교사 채점 화면의 조회 3종. 읽기만 하며 채점은 하지 않는다.
 *
 * <p>세 응답 모두 <b>배포 단위로 한 번에 읽고 메모리에서 접는다.</b> 점수표는 학생 25명 ×
 * 문항 20개면 셀이 500개라 엔티티를 순회하며 조회하면 그 자리에서 N+1이 난다(명세 5절).
 *
 * <p>problem 도메인 데이터는 {@link GradingRubricResultRepository}의 읽기 전용 쿼리로 읽는다 —
 * 남의 repository 를 직접 부르지 않는다(AGENTS.md 3절).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradingQueryService {

    private static final Set<AssignmentStatus> SUBMITTED_STATUSES =
            Set.of(AssignmentStatus.SUBMITTED, AssignmentStatus.GRADED);

    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final WorksheetAssignmentStudentRepository assignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final SubmissionQuestionTimeRepository submissionQuestionTimeRepository;
    private final GradingRubricResultRepository gradingRubricResultRepository;
    private final SchoolClassService schoolClassService;
    private final StudentListQueryService studentListQueryService;
    private final ProblemQuestionDetailService problemQuestionDetailService;
    private final ObjectMapper objectMapper;

    /**
     * S3가 꺼진 환경(로컬 기본값 {@code S3_ENABLED=false})에서는 이 빈이 아예 없다. 생성자 주입으로
     * 받으면 앱이 기동조차 못 하므로 {@link ObjectProvider}로 받고, 없으면 필기 URL 을
     * {@code null}로 내린다.
     */
    private final ObjectProvider<SubmissionImageService> submissionImageServiceProvider;

    // ===== 1. 평가 결과 목록 =====

    public GradingWorksheetListResponse getWorksheets(long teacherId, GradingListRequest request) {
        List<WorksheetAssignment> assignments = worksheetAssignmentRepository.findAllForGrading(
                teacherId, request.gradeAsShort(), request.semester(), request.classId());
        if (assignments.isEmpty()) {
            return new GradingWorksheetListResponse(List.of());
        }

        List<Long> assignmentIds = assignments.stream().map(WorksheetAssignment::getId).toList();
        Map<Long, List<WorksheetAssignmentStudent>> studentsByAssignmentId =
                assignmentStudentRepository.findByAssignment_IdIn(assignmentIds).stream()
                        .collect(Collectors.groupingBy(student -> student.getAssignment().getId()));
        Set<Long> modifiedAssignmentIds = new HashSet<>(
                assignmentStudentRepository.findAssignmentIdsModifiedAfterRelease(assignmentIds));
        Map<Long, String> classNamesById = getClassNames(teacherId, assignments);

        List<GradingWorksheetItemResponse> worksheets = new ArrayList<>();
        for (WorksheetAssignment assignment : assignments) {
            List<WorksheetAssignmentStudent> students =
                    studentsByAssignmentId.getOrDefault(assignment.getId(), List.of());
            int submittedCount = (int) students.stream()
                    .filter(student -> SUBMITTED_STATUSES.contains(student.getStatus()))
                    .count();
            int gradedCount = (int) students.stream()
                    .filter(student -> student.getStatus() == AssignmentStatus.GRADED)
                    .count();
            String status = deriveStatus(students, submittedCount, gradedCount);

            if (request.status() != null && !request.status().equals(status)) {
                continue;
            }
            worksheets.add(GradingWorksheetItemResponse.of(
                    assignment,
                    assignment.getClassId() == null ? null : classNamesById.get(assignment.getClassId()),
                    status,
                    modifiedAssignmentIds.contains(assignment.getId()),
                    students.size(),
                    submittedCount,
                    gradedCount,
                    submittedCount - gradedCount));
        }
        return new GradingWorksheetListResponse(worksheets);
    }

    /**
     * 학습지 채점 상태(명세 2.4).
     *
     * <p>제출자가 0명이면 "전원 채점됨"이 공허하게 참이 되므로 명시적으로 막는다 — 아무도 안 낸
     * 학습지가 {@code graded}로 보이면 안 된다.
     */
    private String deriveStatus(List<WorksheetAssignmentStudent> students,
                                int submittedCount, int gradedCount) {
        boolean released = students.stream()
                .filter(student -> SUBMITTED_STATUSES.contains(student.getStatus()))
                .anyMatch(student -> student.getReleasedAt() != null);
        if (released) {
            return "confirmed";
        }
        if (submittedCount > 0 && submittedCount == gradedCount) {
            return "graded";
        }
        return "grading";
    }

    private Map<Long, String> getClassNames(long teacherId, List<WorksheetAssignment> assignments) {
        Set<Long> classIds = assignments.stream()
                .map(WorksheetAssignment::getClassId)
                .filter(classId -> classId != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return schoolClassService.getClassNamesByIds(teacherId, classIds);
    }

    // ===== 2. 점수표 =====

    public GradingScoreTableResponse getScoreTable(long teacherId, long assignmentId) {
        WorksheetAssignment assignment = getOwnedAssignment(teacherId, assignmentId);
        boolean assessment =
                assignment.getWorksheet().getType() == WorksheetType.COMPREHENSIVE_ASSESSMENT;

        List<WorksheetItem> items = worksheetItemRepository
                .findAllByWorksheetIdOrderByDisplayOrderAsc(assignment.getWorksheet().getId());
        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();
        Map<Long, ProblemQuestion> questionsById = findQuestionsById(questionIds);
        Map<Long, List<ProblemAnswerUnit>> unitsByQuestionId = findAnswerUnitsByQuestionId(questionIds);

        List<WorksheetAssignmentStudent> allStudents =
                assignmentStudentRepository.findByAssignment_Id(assignmentId);
        // 미제출 학생은 점수표에 넣지 않는다(명세 2.5). 몇 명이 안 냈는지는 인원 수로만 알린다.
        List<WorksheetAssignmentStudent> submitted = allStudents.stream()
                .filter(student -> SUBMITTED_STATUSES.contains(student.getStatus()))
                .sorted(Comparator.comparing(WorksheetAssignmentStudent::getId))
                .toList();

        Map<Long, Map<Long, SubmissionAnswer>> answersByStudentId = findAnswersByStudentId(
                submitted.stream().map(WorksheetAssignmentStudent::getId).toList());
        Map<Long, String> studentNamesById = studentListQueryService.getStudentNamesByIds(
                teacherId, submitted.stream().map(WorksheetAssignmentStudent::getStudentId).toList());

        List<GradingQuestionResponse> questions = items.stream()
                .map(item -> GradingQuestionResponse.of(item, questionsById.get(item.getQuestionId())))
                .toList();

        List<GradingStudentRowResponse> rows = new ArrayList<>();
        for (WorksheetAssignmentStudent student : submitted) {
            Map<Long, SubmissionAnswer> answers =
                    answersByStudentId.getOrDefault(student.getId(), Map.of());
            List<GradingCellResponse> cells = new ArrayList<>();
            BigDecimal totalScore = BigDecimal.ZERO;
            boolean complete = true;

            for (WorksheetItem item : items) {
                List<ProblemAnswerUnit> units =
                        unitsByQuestionId.getOrDefault(item.getQuestionId(), List.of());
                CellFold fold = foldCell(item, units, answers);
                cells.add(new GradingCellResponse(
                        item.getId(), fold.result(), fold.score(), fold.gradingStatus(), fold.gradedBy()));
                totalScore = totalScore.add(fold.score());
                complete = complete && fold.allGraded();
            }
            rows.add(new GradingStudentRowResponse(
                    student.getId(),
                    student.getStudentId(),
                    null,
                    studentNamesById.get(student.getStudentId()),
                    complete,
                    complete ? totalScore : null,
                    cells));
        }

        int gradedCount = (int) allStudents.stream()
                .filter(student -> student.getStatus() == AssignmentStatus.GRADED)
                .count();
        String status = deriveStatus(allStudents, submitted.size(), gradedCount);
        boolean modified = !assignmentStudentRepository
                .findAssignmentIdsModifiedAfterRelease(List.of(assignmentId)).isEmpty();

        return new GradingScoreTableResponse(
                assignmentId,
                assignment.getWorksheet().getTitle(),
                GradingResponseFormatter.toApiType(assignment.getWorksheet().getType()),
                assignment.getClassId() == null
                        ? null
                        : getClassNames(teacherId, List.of(assignment)).get(assignment.getClassId()),
                status,
                modified,
                assessment ? sumMaxScore(items) : null,
                allStudents.size(),
                submitted.size(),
                buildMetrics(rows, submitted.size()),
                questions,
                rows);
    }

    /** 셀 하나는 그 문항의 칸들을 접은 결과다. 문항당 칸이 평균 1.9개다(명세 5절). */
    private CellFold foldCell(WorksheetItem item, List<ProblemAnswerUnit> units,
                              Map<Long, SubmissionAnswer> answers) {
        BigDecimal maxScorePerUnit = GradingResponseFormatter.resolveMaxScore(item.getMaxScore());
        List<String> unitResults = new ArrayList<>();
        BigDecimal score = BigDecimal.ZERO;
        boolean allGraded = !units.isEmpty();
        boolean anyFailed = false;
        boolean anyOverridden = false;

        for (ProblemAnswerUnit unit : units) {
            SubmissionAnswer answer = answers.get(unit.getId());
            GradingStatus gradingStatus =
                    answer == null ? GradingStatus.NOT_GRADED : answer.getGradingStatus();
            unitResults.add(GradingResponseFormatter.classifyUnit(
                    gradingStatus,
                    answer == null ? null : answer.getFinalScore(),
                    hasAnswer(answer),
                    maxScorePerUnit));
            if (gradingStatus == GradingStatus.GRADED && answer.getFinalScore() != null) {
                score = score.add(answer.getFinalScore());
            }
            allGraded = allGraded && gradingStatus == GradingStatus.GRADED;
            anyFailed = anyFailed || gradingStatus == GradingStatus.FAILED;
            anyOverridden = anyOverridden || (answer != null && answer.getOverriddenBy() != null);
        }

        String cellStatus = anyFailed
                ? GradingStatus.FAILED.name()
                : (allGraded ? GradingStatus.GRADED.name() : GradingStatus.NOT_GRADED.name());
        String gradedBy = allGraded ? (anyOverridden ? "teacher" : "auto") : null;
        return new CellFold(GradingResponseFormatter.aggregateItemResult(unitResults),
                score, cellStatus, gradedBy, allGraded);
    }

    private record CellFold(String result, BigDecimal score, String gradingStatus,
                            String gradedBy, boolean allGraded) {
    }

    /**
     * 평균·최고·최저는 <b>채점이 끝난 학생만</b> 대상으로 한다 — 미채점 학생을 0점으로 세면
     * 채점이 진행될수록 평균이 올라가는 지표가 된다.
     */
    private GradingScoreTableResponse.Metrics buildMetrics(
            List<GradingStudentRowResponse> rows, int submittedCount) {
        List<BigDecimal> scores = rows.stream()
                .filter(GradingStudentRowResponse::gradingComplete)
                .map(GradingStudentRowResponse::totalScore)
                .toList();
        int pendingCount = submittedCount - scores.size();
        if (scores.isEmpty()) {
            return new GradingScoreTableResponse.Metrics(null, null, null, pendingCount);
        }
        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GradingScoreTableResponse.Metrics(
                sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP),
                scores.stream().max(BigDecimal::compareTo).orElseThrow(),
                scores.stream().min(BigDecimal::compareTo).orElseThrow(),
                pendingCount);
    }

    private BigDecimal sumMaxScore(List<WorksheetItem> items) {
        return items.stream()
                .map(WorksheetItem::getMaxScore)
                .filter(maxScore -> maxScore != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== 3. 학생 채점 화면 =====

    public GradingStudentDetailResponse getStudentDetail(
            long teacherId, long assignmentId, long assignmentStudentId) {
        WorksheetAssignmentStudent student = assignmentStudentRepository
                .findDetailById(assignmentStudentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND));
        // 경로의 배포와 실제 배포가 다르면 남의 자원을 들여다보는 것이다.
        if (!student.getAssignment().getId().equals(assignmentId)
                || !student.getAssignment().getWorksheet().getOwnerTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND);
        }

        List<WorksheetItem> items = worksheetItemRepository.findAllByWorksheetIdOrderByDisplayOrderAsc(
                student.getAssignment().getWorksheet().getId());
        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();
        Map<Long, ProblemQuestion> questionsById = findQuestionsById(questionIds);
        Map<Long, List<ProblemAnswerUnit>> unitsByQuestionId = findAnswerUnitsByQuestionId(questionIds);
        Map<Long, List<ProblemChoice>> choicesByQuestionId = questionIds.isEmpty()
                ? Map.of()
                : gradingRubricResultRepository.findChoicesByQuestionIdIn(questionIds).stream()
                        .collect(Collectors.groupingBy(choice -> choice.getQuestion().getId()));

        Map<Long, SubmissionAnswer> answersByUnitId = submissionAnswerRepository
                .findByAssignmentStudentId(assignmentStudentId).stream()
                .collect(Collectors.toMap(SubmissionAnswer::getAnswerUnitId, answer -> answer));
        Map<Long, Integer> timesByItemId = submissionQuestionTimeRepository
                .findByAssignmentStudentId(assignmentStudentId).stream()
                .collect(Collectors.toMap(SubmissionQuestionTime::getWorksheetItemId,
                        SubmissionQuestionTime::getTimeSpentSeconds));

        Map<Long, List<ProblemRubricItem>> rubricItemsByQuestionId = questionIds.isEmpty()
                ? Map.of()
                : gradingRubricResultRepository.findRubricItemsByQuestionIdIn(questionIds).stream()
                        .collect(Collectors.groupingBy(item -> item.getQuestion().getId()));
        List<Long> answerIds = answersByUnitId.values().stream().map(SubmissionAnswer::getId).toList();
        Map<Long, List<GradingRubricResult>> rubricResultsByAnswerId = answerIds.isEmpty()
                ? Map.of()
                : gradingRubricResultRepository.findByStudentAnswerIdIn(answerIds).stream()
                        .collect(Collectors.groupingBy(GradingRubricResult::getStudentAnswerId));

        // 빈칸형 문항이 없으면 조회하지 않는다 — 객관식·서술형만 있는 학습지가 흔하다.
        Map<Long, List<ProblemStep>> stepsByQuestionId = questionsById.values().stream()
                .anyMatch(question -> question.getQuestionType() == QuestionType.STEP_FILL)
                ? gradingRubricResultRepository.findStepsByQuestionIdIn(questionIds).stream()
                        .collect(Collectors.groupingBy(step -> step.getQuestion().getId()))
                : Map.of();

        Map<Long, String> handwritingUrls = createHandwritingUrls(
                teacherId, assignmentStudentId, answersByUnitId);

        Map<Long, List<ProblemAssetResponse>> assetsByQuestionId = problemQuestionDetailService
                .getAssetsByQuestionIds(questionIds);

        List<GradingDetailItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        boolean complete = true;

        for (WorksheetItem item : items) {
            ProblemQuestion question = questionsById.get(item.getQuestionId());
            List<ProblemAnswerUnit> units = unitsByQuestionId.getOrDefault(item.getQuestionId(), List.of());
            List<ProblemChoice> choices = choicesByQuestionId.getOrDefault(item.getQuestionId(), List.of());
            BigDecimal maxScorePerUnit = GradingResponseFormatter.resolveMaxScore(item.getMaxScore());

            List<GradingAnswerUnitResponse> unitResponses = new ArrayList<>();
            List<String> unitResults = new ArrayList<>();
            for (ProblemAnswerUnit unit : units) {
                SubmissionAnswer answer = answersByUnitId.get(unit.getId());
                GradingStatus gradingStatus =
                        answer == null ? GradingStatus.NOT_GRADED : answer.getGradingStatus();
                unitResults.add(GradingResponseFormatter.classifyUnit(
                        gradingStatus,
                        answer == null ? null : answer.getFinalScore(),
                        hasAnswer(answer),
                        maxScorePerUnit));
                if (gradingStatus == GradingStatus.GRADED && answer.getFinalScore() != null) {
                    totalScore = totalScore.add(answer.getFinalScore());
                }
                complete = complete && gradingStatus == GradingStatus.GRADED;

                unitResponses.add(new GradingAnswerUnitResponse(
                        answer == null ? null : answer.getId(),
                        unit.getId(),
                        unit.getDisplayOrder(),
                        resolveCorrectAnswer(question.getQuestionType(), unit, choices),
                        resolveStudentAnswer(question.getQuestionType(), answer, choices),
                        resolveCorrectChoiceId(question.getQuestionType(), unit, choices),
                        question.getQuestionType() == QuestionType.MULTIPLE_CHOICE && answer != null
                                ? answer.getSelectedChoiceId()
                                : null,
                        answer == null ? null : handwritingUrls.get(unit.getId()),
                        answer == null ? unit.getCompareMethod().name() : answer.getCompareMethod().name(),
                        gradingStatus.name(),
                        GradingResponseFormatter.toApiGradedBy(
                                answer == null ? null : answer.getOverriddenBy(), gradingStatus),
                        answer == null ? null : answer.getAutoScore(),
                        answer == null ? null : answer.getFinalScore(),
                        answer == null ? null : answer.getFailureReason(),
                        buildRubric(question, answer, rubricItemsByQuestionId, rubricResultsByAnswerId)));
            }

            itemResponses.add(new GradingDetailItemResponse(
                    item.getId(),
                    item.getDisplayOrder(),
                    question.getId(),
                    GradingResponseFormatter.toApiQuestionFormat(question.getQuestionType()),
                    GradingResponseFormatter.toApiDifficulty(question.getDifficulty()),
                    item.getMaxScore(),
                    parseContentBlocks(question),
                    question.getExplanation(),
                    timesByItemId.get(item.getId()),
                    GradingResponseFormatter.aggregateItemResult(unitResults),
                    unitResponses,
                    buildChoices(question.getQuestionType(), choices),
                    buildSteps(question.getQuestionType(), units,
                            stepsByQuestionId.getOrDefault(question.getId(), List.of())),
                    assetsByQuestionId.getOrDefault(question.getId(), List.of())));
        }

        return new GradingStudentDetailResponse(
                assignmentStudentId,
                studentListQueryService
                        .getStudentNamesByIds(teacherId, List.of(student.getStudentId()))
                        .get(student.getStudentId()),
                null,
                student.getSubmittedAt(),
                complete ? totalScore : null,
                itemResponses);
    }

    /**
     * 필기 이미지 URL. 이미지를 실제로 올린 칸만 요청한다 — 없는 칸까지 URL 을 만들면 만료 10분짜리
     * 서명이 헛돌고 권한 검증만 늘어난다.
     */
    private Map<Long, String> createHandwritingUrls(
            long teacherId, long assignmentStudentId, Map<Long, SubmissionAnswer> answersByUnitId) {
        SubmissionImageService imageService = submissionImageServiceProvider.getIfAvailable();
        if (imageService == null) {
            return Map.of();
        }
        List<Long> unitIds = answersByUnitId.entrySet().stream()
                .filter(entry -> entry.getValue().getAnswerImageRef() != null)
                .map(Map.Entry::getKey)
                .toList();
        return imageService.createGetUrls(teacherId, UserRole.TEACHER, assignmentStudentId, unitIds);
    }

    /**
     * 서술형 채점 기준과 판정. <b>판정 전에도 기준 목록은 내려보낸다</b> — 교사가 손으로 체크하려면
     * 기준이 화면에 있어야 한다.
     *
     * <p>판정 행이 없는 항목은 {@code satisfied}가 {@code null}이다. 행 부재는 "판정 안 함"이지
     * "미충족"이 아니라서 {@code false}로 채우지 않는다({@code GradingRubricResult} 자바독).
     * 기준 자체가 없는 문항만 빈 배열이다.
     */
    private List<GradingAnswerUnitResponse.RubricItem> buildRubric(
            ProblemQuestion question, SubmissionAnswer answer,
            Map<Long, List<ProblemRubricItem>> rubricItemsByQuestionId,
            Map<Long, List<GradingRubricResult>> rubricResultsByAnswerId) {
        if (question.getQuestionType() != QuestionType.ESSAY) {
            return null;
        }
        List<ProblemRubricItem> rubricItems =
                rubricItemsByQuestionId.getOrDefault(question.getId(), List.of());
        if (rubricItems.isEmpty()) {
            return List.of();
        }
        List<GradingRubricResult> results = answer == null
                ? List.of()
                : rubricResultsByAnswerId.getOrDefault(answer.getId(), List.of());
        Map<Long, GradingRubricResult> resultByRubricItemId = results.stream()
                .collect(Collectors.toMap(GradingRubricResult::getRubricItemId, result -> result));
        return rubricItems.stream()
                .sorted(Comparator.comparingInt(ProblemRubricItem::getDisplayOrder))
                .map(rubricItem -> {
                    GradingRubricResult result = resultByRubricItemId.get(rubricItem.getId());
                    return new GradingAnswerUnitResponse.RubricItem(
                            rubricItem.getId(),
                            // 컬럼 이름은 label 이고 명세의 필드 이름은 description 이다.
                            rubricItem.getLabel(),
                            BigDecimal.valueOf(rubricItem.getWeight()),
                            result == null ? null : result.isSatisfied(),
                            result == null ? null : result.getEvidence());
                })
                .toList();
    }

    /** 객관식 보기 전체. 다른 형식이면 {@code null}이다 — 빈 배열은 "보기가 비어 있는 객관식"과 섞인다. */
    private List<GradingChoiceResponse> buildChoices(QuestionType questionType,
                                                     List<ProblemChoice> choices) {
        if (questionType != QuestionType.MULTIPLE_CHOICE) {
            return null;
        }
        return choices.stream().map(GradingChoiceResponse::from).toList();
    }

    /**
     * 빈칸형 풀이 단계. 다른 형식이면 {@code null}이다.
     *
     * <p>{@code segments} JSON의 빈칸은 {@code answerUnitId}가 아니라 {@code unitKey}(예: "B1")로
     * 표시된다. 문항 안에서 {@code unitKey}가 유일하므로 이걸로 채점 칸 ID를 찾아 붙인다.
     */
    private List<GradingStepResponse> buildSteps(QuestionType questionType,
                                                 List<ProblemAnswerUnit> units,
                                                 List<ProblemStep> steps) {
        if (questionType != QuestionType.STEP_FILL) {
            return null;
        }
        Map<String, Long> answerUnitIdByUnitKey = units.stream()
                .collect(Collectors.toMap(ProblemAnswerUnit::getUnitKey, ProblemAnswerUnit::getId));
        return steps.stream()
                .map(step -> GradingStepResponse.from(
                        step, parseSegments(step.getSegments(), answerUnitIdByUnitKey)))
                .toList();
    }

    /** 정답 보기 ID. {@code answer_raw}가 1-based 보기 순번이라 그 순번의 보기를 찾는다. */
    private Long resolveCorrectChoiceId(QuestionType questionType, ProblemAnswerUnit unit,
                                        List<ProblemChoice> choices) {
        if (questionType != QuestionType.MULTIPLE_CHOICE || unit.getAnswerRaw() == null) {
            return null;
        }
        return findChoiceByOneBasedOrder(choices, Integer.parseInt(unit.getAnswerRaw()))
                .map(ProblemChoice::getId)
                .orElse(null);
    }

    /**
     * 정답. 객관식의 {@code answer_raw}는 1-based 보기 순번이라 텍스트로 풀어야 한다.
     * 서술형은 모범답안을 저장하지 않아 {@code null}이다.
     */
    private String resolveCorrectAnswer(QuestionType questionType, ProblemAnswerUnit unit,
                                        List<ProblemChoice> choices) {
        String answerRaw = unit.getAnswerRaw();
        if (answerRaw == null) {
            return null;
        }
        if (questionType != QuestionType.MULTIPLE_CHOICE) {
            return answerRaw;
        }
        return findChoiceByOneBasedOrder(choices, Integer.parseInt(answerRaw))
                .map(ProblemChoice::getContent)
                .orElse(null);
    }

    /**
     * 학생 답. 학생 API 와 달리 <b>서술형도 그대로 내보낸다</b> — 교사는 답안 본문을 봐야 채점한다.
     */
    private String resolveStudentAnswer(QuestionType questionType, SubmissionAnswer answer,
                                        List<ProblemChoice> choices) {
        if (answer == null) {
            return null;
        }
        if (questionType != QuestionType.MULTIPLE_CHOICE) {
            return answer.getRawLatex();
        }
        if (answer.getSelectedChoiceId() == null) {
            return null;
        }
        return choices.stream()
                .filter(choice -> choice.getId().equals(answer.getSelectedChoiceId()))
                .findFirst()
                .map(ProblemChoice::getContent)
                .orElse(null);
    }

    private Optional<ProblemChoice> findChoiceByOneBasedOrder(List<ProblemChoice> choices,
                                                              int oneBasedOrder) {
        return choices.stream()
                .filter(choice -> choice.getDisplayOrder() + 1 == oneBasedOrder)
                .findFirst();
    }

    private List<GradingSegmentResponse> parseSegments(String segments,
                                                       Map<String, Long> answerUnitIdByUnitKey) {
        try {
            List<ProblemStepSegmentResponse> parsed = objectMapper.readValue(
                    segments, new TypeReference<List<ProblemStepSegmentResponse>>() {
                    });
            return parsed.stream()
                    .map(segment -> GradingSegmentResponse.from(segment, answerUnitIdByUnitKey))
                    .toList();
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
    }

    private List<GradingContentBlockResponse> parseContentBlocks(ProblemQuestion question) {
        try {
            List<ProblemContentBlockResponse> blocks = objectMapper.readValue(
                    question.getContentBlocks(), new TypeReference<List<ProblemContentBlockResponse>>() {
                    });
            return blocks.stream()
                    .map(GradingContentBlockResponse::from)
                    .toList();
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
    }

    // ===== 공통 =====

    private WorksheetAssignment getOwnedAssignment(long teacherId, long assignmentId) {
        WorksheetAssignment assignment = worksheetAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND));
        // 남의 학습지는 "권한 없음"이 아니라 "없음"으로 답한다 — 존재 여부를 흘리지 않는다(명세 2.1).
        if (!assignment.getWorksheet().getOwnerTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND);
        }
        return assignment;
    }

    private Map<Long, ProblemQuestion> findQuestionsById(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return gradingRubricResultRepository.findQuestionsByIdIn(questionIds).stream()
                .collect(Collectors.toMap(ProblemQuestion::getId, question -> question));
    }

    private Map<Long, List<ProblemAnswerUnit>> findAnswerUnitsByQuestionId(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return gradingRubricResultRepository.findAnswerUnitsByQuestionIdIn(questionIds).stream()
                .collect(Collectors.groupingBy(unit -> unit.getQuestion().getId()));
    }

    private Map<Long, Map<Long, SubmissionAnswer>> findAnswersByStudentId(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, SubmissionAnswer>> answers = new LinkedHashMap<>();
        for (SubmissionAnswer answer : submissionAnswerRepository.findByAssignmentStudentIdIn(studentIds)) {
            answers.computeIfAbsent(answer.getAssignmentStudentId(), key -> new LinkedHashMap<>())
                    .put(answer.getAnswerUnitId(), answer);
        }
        return answers;
    }

    private boolean hasAnswer(SubmissionAnswer answer) {
        return answer != null && (answer.getSelectedChoiceId() != null
                || (answer.getRawLatex() != null && !answer.getRawLatex().isBlank())
                || answer.getAnswerImageRef() != null);
    }
}
