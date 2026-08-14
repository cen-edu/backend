package com.cenedu.backend.domain.worksheet.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.dto.response.ProblemContentBlockResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemStepSegmentResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemStep;
import com.cenedu.backend.domain.problem.repository.ProblemAnswerUnitRepository;
import com.cenedu.backend.domain.problem.repository.ProblemChoiceRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemStepRepository;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.domain.submission.repository.SubmissionAnswerRepository;
import com.cenedu.backend.domain.worksheet.dto.response.StudentAnswerUnitResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentAssignmentListResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentAssignmentResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentChoiceResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentContentBlockResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentSegmentResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentStepResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentWorksheetDetailResponse;
import com.cenedu.backend.domain.worksheet.dto.response.StudentWorksheetItemResponse;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.row.StudentAssignmentRow;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 학생 학습지 조회(목록·상세). 엔티티 그래프를 그대로 순회하지 않고 배치 조회로 읽는다.
 *
 * <p>{@code domain/problem}의 기존 배치 리포지토리({@link ProblemChoiceRepository} 등)를 그대로
 * 재사용한다 — {@code domain/problem} 파일은 하나도 만들거나 고치지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentWorksheetQueryService {

    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final ProblemQuestionRepository problemQuestionRepository;
    private final ProblemChoiceRepository problemChoiceRepository;
    private final ProblemStepRepository problemStepRepository;
    private final ProblemAnswerUnitRepository problemAnswerUnitRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final ObjectMapper objectMapper;

    public StudentAssignmentListResponse getAssignments(long studentId) {
        List<StudentAssignmentRow> rows = worksheetAssignmentStudentRepository.findAllForStudent(studentId);
        if (rows.isEmpty()) {
            return StudentAssignmentListResponse.from(List.of());
        }

        List<Long> worksheetIds = rows.stream().map(StudentAssignmentRow::worksheetId).distinct().toList();
        Map<Long, List<WorksheetItem>> itemsByWorksheetId = worksheetItemRepository
                .findByWorksheetIdInOrderByWorksheetIdAscDisplayOrderAsc(worksheetIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getWorksheet().getId()));

        List<Long> questionIds = itemsByWorksheetId.values().stream()
                .flatMap(List::stream)
                .map(WorksheetItem::getQuestionId)
                .distinct()
                .toList();
        Map<Long, Long> answerUnitCountByQuestionId = problemAnswerUnitRepository.findAllByQuestionIds(questionIds)
                .stream()
                .collect(Collectors.groupingBy(unit -> unit.getQuestion().getId(), Collectors.counting()));

        List<Long> sourceAssignmentIds = rows.stream()
                .map(StudentAssignmentRow::sourceAssignmentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, Long> assignmentStudentIdBySourceAssignmentId = sourceAssignmentIds.isEmpty()
                ? Map.of()
                : worksheetAssignmentStudentRepository
                        .findByStudentIdAndAssignment_IdIn(studentId, sourceAssignmentIds)
                        .stream()
                        .collect(Collectors.toMap(
                                was -> was.getAssignment().getId(), WorksheetAssignmentStudent::getId));

        List<StudentAssignmentResponse> assignments = rows.stream()
                .map(row -> {
                    List<WorksheetItem> items = itemsByWorksheetId.getOrDefault(row.worksheetId(), List.of());
                    int totalUnits = totalUnits(row.type(), items, answerUnitCountByQuestionId);
                    Long sourceAssignmentStudentId = row.sourceAssignmentId() == null
                            ? null
                            : assignmentStudentIdBySourceAssignmentId.get(row.sourceAssignmentId());
                    List<CustomStage> stages = row.origin() == WorksheetOrigin.CUSTOM ? stages(items) : null;
                    return StudentAssignmentResponse.from(row, totalUnits, sourceAssignmentStudentId, stages);
                })
                .toList();

        return StudentAssignmentListResponse.from(assignments);
    }

    public StudentWorksheetDetailResponse getAssignmentDetail(long studentId, long assignmentStudentId) {
        WorksheetAssignmentStudent was = worksheetAssignmentStudentRepository.findDetailById(assignmentStudentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND));
        // 불일치는 404 — 존재 여부를 노출하지 않는다(명세 2.1).
        if (was.getStudentId() != studentId) {
            throw new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND);
        }

        List<WorksheetItem> items = worksheetItemRepository.findAllByWorksheetIdOrderByDisplayOrderAsc(
                was.getAssignment().getWorksheet().getId());
        if (items.isEmpty()) {
            return StudentWorksheetDetailResponse.from(was, List.of());
        }

        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();

        Map<Long, ProblemQuestion> questionsById = problemQuestionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(ProblemQuestion::getId, question -> question));

        Map<Long, List<ProblemChoice>> choicesByQuestionId = problemChoiceRepository
                .findAllByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(choice -> choice.getQuestion().getId()));

        Map<Long, List<ProblemStep>> stepsByQuestionId = problemStepRepository
                .findAllByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(step -> step.getQuestion().getId()));

        Map<Long, List<ProblemAnswerUnit>> answerUnitsByQuestionId = problemAnswerUnitRepository
                .findAllByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(unit -> unit.getQuestion().getId()));

        Map<Long, SubmissionAnswer> savedByAnswerUnitId = submissionAnswerRepository
                .findByAssignmentStudentId(assignmentStudentId).stream()
                .collect(Collectors.toMap(SubmissionAnswer::getAnswerUnitId, answer -> answer));

        List<StudentWorksheetItemResponse> itemResponses = items.stream()
                .map(item -> buildItemResponse(
                        item, questionsById.get(item.getQuestionId()),
                        choicesByQuestionId, stepsByQuestionId, answerUnitsByQuestionId, savedByAnswerUnitId))
                .toList();

        return StudentWorksheetDetailResponse.from(was, itemResponses);
    }

    private StudentWorksheetItemResponse buildItemResponse(
            WorksheetItem item,
            ProblemQuestion question,
            Map<Long, List<ProblemChoice>> choicesByQuestionId,
            Map<Long, List<ProblemStep>> stepsByQuestionId,
            Map<Long, List<ProblemAnswerUnit>> answerUnitsByQuestionId,
            Map<Long, SubmissionAnswer> savedByAnswerUnitId
    ) {
        long questionId = question.getId();
        List<ProblemAnswerUnit> units = answerUnitsByQuestionId.getOrDefault(questionId, List.of());

        List<StudentContentBlockResponse> contentBlocks =
                parseContentBlocks(question.getContentBlocks(), questionId);

        List<StudentChoiceResponse> choices = choicesByQuestionId.getOrDefault(questionId, List.of()).stream()
                .map(StudentChoiceResponse::from)
                .toList();

        Map<String, Long> answerUnitIdByUnitKey = units.stream()
                .collect(Collectors.toMap(ProblemAnswerUnit::getUnitKey, ProblemAnswerUnit::getId));
        List<StudentStepResponse> steps = stepsByQuestionId.getOrDefault(questionId, List.of()).stream()
                .map(step -> StudentStepResponse.from(step, parseSegments(step.getSegments(), answerUnitIdByUnitKey)))
                .toList();

        List<StudentAnswerUnitResponse> answerUnits = units.stream()
                .map(unit -> StudentAnswerUnitResponse.from(
                        unit, question.getQuestionType(), savedByAnswerUnitId.get(unit.getId())))
                .toList();

        return StudentWorksheetItemResponse.from(item, question, contentBlocks, choices, steps, answerUnits);
    }

    private int totalUnits(WorksheetType type, List<WorksheetItem> items, Map<Long, Long> answerUnitCountByQuestionId) {
        if (type == WorksheetType.COMPREHENSIVE_ASSESSMENT) {
            return items.size();
        }
        return items.stream()
                .mapToInt(item -> Math.toIntExact(answerUnitCountByQuestionId.getOrDefault(item.getQuestionId(), 0L)))
                .sum();
    }

    /** custom_stage의 distinct 집합을 표시 순서대로. items가 이미 displayOrder로 정렬돼 있다. */
    private List<CustomStage> stages(List<WorksheetItem> items) {
        return items.stream()
                .map(WorksheetItem::getCustomStage)
                .filter(stage -> stage != null)
                .distinct()
                .toList();
    }

    private List<StudentContentBlockResponse> parseContentBlocks(String contentBlocks, long questionId) {
        try {
            List<ProblemContentBlockResponse> blocks = objectMapper.readValue(
                    contentBlocks, new TypeReference<List<ProblemContentBlockResponse>>() {
                    });
            return blocks.stream().map(block -> StudentContentBlockResponse.from(block, questionId)).toList();
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
    }

    private List<StudentSegmentResponse> parseSegments(String segments, Map<String, Long> answerUnitIdByUnitKey) {
        try {
            List<ProblemStepSegmentResponse> parsed = objectMapper.readValue(
                    segments, new TypeReference<List<ProblemStepSegmentResponse>>() {
                    });
            return parsed.stream().map(segment -> StudentSegmentResponse.from(segment, answerUnitIdByUnitKey)).toList();
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
    }
}
