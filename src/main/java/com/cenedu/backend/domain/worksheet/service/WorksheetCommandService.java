package com.cenedu.backend.domain.worksheet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cenedu.backend.domain.member.dto.response.SchoolClassDetailResponse;
import com.cenedu.backend.domain.member.dto.response.StudentDetailResponse;
import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.domain.member.service.StudentListQueryService;
import com.cenedu.backend.domain.problem.dto.response.FinalizedProblemReferenceResponse;
import com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationService;
import com.cenedu.backend.domain.problem.service.ProblemQuestionDetailService;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetAssignmentCreateRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetCreateRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetGenSpecRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetItemRequest;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetAssignmentCreateResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetCreateResponse;
import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.WorksheetGenSpec;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.domain.worksheet.entity.enums.SupportMode;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetGenSpecRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문제 생성 결과를 문제 보관함에 확정 저장한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetCommandService {

    private static final BigDecimal ASSESSMENT_TOTAL_SCORE_CENTS = BigDecimal.valueOf(10_000);
    private static final BigDecimal ASSESSMENT_TOTAL_SCORE = BigDecimal.valueOf(100);
    private static final int SCORE_SCALE = 2;

    private final WorksheetRepository worksheetRepository;
    private final WorksheetGenSpecRepository worksheetGenSpecRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final ProblemQuestionDetailService problemQuestionDetailService;
    private final ProblemAuthoringFinalizationService problemAuthoringFinalizationService;
    private final SchoolClassService schoolClassService;
    private final StudentListQueryService studentListQueryService;

    /** 학습지 저장 - 검증을 마친 뒤 학습지·출제조건·문항을 한 트랜잭션으로 저장한다. */
    @Transactional
    public WorksheetCreateResponse createWorksheet(long teacherId, WorksheetCreateRequest request) {
        WorksheetType type = toWorksheetType(request.type());
        WorksheetOrigin origin = toWorksheetOrigin(request.origin());
        String semester = toSemester(request.semester());

        List<ResolvedWorksheetItem> items = resolveProblemReferences(teacherId, request.items());
        List<Long> questionIds = items.stream().map(ResolvedWorksheetItem::questionId).toList();
        Set<Long> distinctQuestionIds = new LinkedHashSet<>(questionIds);

        Map<Long, QuestionType> questionTypesById =
                problemQuestionDetailService.getQuestionTypes(questionIds);
        if (questionTypesById.size() != distinctQuestionIds.size()) {
            throw new BusinessException(ErrorCode.WORKSHEET_QUESTION_NOT_FOUND);
        }
        if (distinctQuestionIds.size() != questionIds.size()) {
            throw new BusinessException(ErrorCode.WORKSHEET_QUESTION_DUPLICATED);
        }
        if (type == WorksheetType.GENERAL_LEARNING) {
            boolean allStepFill = questionIds.stream()
                    .map(questionTypesById::get)
                    .allMatch(questionType -> questionType == QuestionType.STEP_FILL);
            if (!allStepFill) {
                throw new BusinessException(ErrorCode.WORKSHEET_TYPE_MISMATCH);
            }
        }

        int genSpecCount = request.genSpec().stream()
                .mapToInt(WorksheetGenSpecRequest::count)
                .sum();
        if (genSpecCount != items.size()) {
            throw new BusinessException(ErrorCode.WORKSHEET_SPEC_MISMATCH);
        }

        validateSourceAssignment(origin, request.sourceAssignmentId());
        validateDisplayOrdersAndSupportMapping(request.items());

        WorksheetAssignment sourceAssignment = origin == WorksheetOrigin.CUSTOM
                ? worksheetAssignmentRepository.getReferenceById(request.sourceAssignmentId())
                : null;
        Worksheet parentWorksheet = resolveParentWorksheet(
                teacherId, origin, request.parentWorksheetId(), request.sourceAssignmentId());

        Short totalScore = type == WorksheetType.COMPREHENSIVE_ASSESSMENT ? (short) 100 : null;

        Worksheet worksheet = Worksheet.create(
                request.title(), type, origin, teacherId,
                request.grade().shortValue(), semester, totalScore, sourceAssignment,
                parentWorksheet);
        worksheetRepository.save(worksheet);

        List<WorksheetGenSpec> genSpecs = request.genSpec().stream()
                .map(spec -> WorksheetGenSpec.create(
                        worksheet, spec.subUnitId(), toQuestionType(spec.questionType()),
                        toDifficulty(spec.difficulty()), spec.count().shortValue()))
                .toList();
        worksheetGenSpecRepository.saveAll(genSpecs);

        Map<Integer, BigDecimal> maxScoresByDisplayOrder =
                computeMaxScoresByDisplayOrder(type, items.size());
        List<WorksheetItem> worksheetItems = items.stream()
                .map(item -> WorksheetItem.create(
                        worksheet, item.questionId(), item.displayOrder(),
                        maxScoresByDisplayOrder.get(item.displayOrder()),
                        toCustomStage(item.customStage()), toSupportMode(item.supportMode())))
                .toList();
        worksheetItemRepository.saveAll(worksheetItems);

        return WorksheetCreateResponse.from(worksheet, worksheetItems.size());
    }

    /** 작성 Session을 원자적으로 최종화하고 모든 문항 참조를 questionId로 통일한다. */
    private List<ResolvedWorksheetItem> resolveProblemReferences(
            long teacherId, List<WorksheetItemRequest> items
    ) {
        List<Long> sessionIds = items.stream()
                .map(WorksheetItemRequest::sessionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, FinalizedProblemReferenceResponse> finalizedBySession =
                problemAuthoringFinalizationService.finalizeForWorksheet(teacherId, sessionIds).stream()
                        .collect(Collectors.toMap(
                                FinalizedProblemReferenceResponse::sessionId,
                                response -> response));

        return items.stream().map(item -> {
            Long questionId = item.questionId();
            if (item.sessionId() != null) {
                FinalizedProblemReferenceResponse finalized = finalizedBySession.get(item.sessionId());
                if (finalized == null) {
                    throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND);
                }
                questionId = finalized.questionId();
            }
            return new ResolvedWorksheetItem(questionId, item.displayOrder(),
                    item.supportMode(), item.customStage());
        }).toList();
    }

    /** 학습지 배포 - 반 소유권·기한·중복을 검증한 뒤 배포와 학생별 배정을 함께 저장한다. */
    @Transactional
    public WorksheetAssignmentCreateResponse assignWorksheet(
            long teacherId, long worksheetId, WorksheetAssignmentCreateRequest request
    ) {
        Worksheet worksheet = getOwnedWorksheet(teacherId, worksheetId);

        OffsetDateTime now = OffsetDateTime.now();
        if (!request.dueAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.WORKSHEET_DUE_IN_PAST);
        }

        return request.isStudentTarget()
                ? assignToStudent(teacherId, worksheet, request, now)
                : assignToClass(teacherId, worksheet, request, now);
    }

    /** 반 전원에게 배포한다. */
    private WorksheetAssignmentCreateResponse assignToClass(
            long teacherId, Worksheet worksheet, WorksheetAssignmentCreateRequest request,
            OffsetDateTime now
    ) {
        SchoolClassDetailResponse classDetail = getOwnedClassDetail(teacherId, request.classId());
        if (worksheetAssignmentRepository.existsByWorksheetIdAndClassId(
                worksheet.getId(), request.classId())) {
            throw new BusinessException(ErrorCode.WORKSHEET_DUPLICATE_ASSIGNMENT);
        }

        WorksheetAssignment assignment = WorksheetAssignment.create(
                worksheet, request.classId(), null, now, request.dueAt());
        worksheetAssignmentRepository.save(assignment);

        List<WorksheetAssignmentStudent> assignmentStudents = classDetail.students().stream()
                .map(student -> WorksheetAssignmentStudent.create(assignment, student.id()))
                .toList();
        if (!assignmentStudents.isEmpty()) {
            worksheetAssignmentStudentRepository.saveAll(assignmentStudents);
        }

        return WorksheetAssignmentCreateResponse.from(
                assignment, classDetail.name(), assignmentStudents.size());
    }

    /**
     * 학생 한 명에게 배포한다.
     *
     * <p>맞춤 학습지가 이 경로를 쓴다. 취약점은 학생마다 달라 반 전체에 같은 보강을 내보내는 것이
     * 의미가 없고, 맞춤 학습 분석도 학생 배정만 세션으로 인식한다.
     */
    private WorksheetAssignmentCreateResponse assignToStudent(
            long teacherId, Worksheet worksheet, WorksheetAssignmentCreateRequest request,
            OffsetDateTime now
    ) {
        // 소유 검증까지 함께 한다. 남의 학생에게 배포하면 여기서 막힌다.
        StudentDetailResponse student =
                studentListQueryService.getStudentDetail(teacherId, request.studentId());
        if (worksheetAssignmentRepository.existsByWorksheetIdAndStudentId(
                worksheet.getId(), request.studentId())) {
            throw new BusinessException(ErrorCode.WORKSHEET_DUPLICATE_ASSIGNMENT);
        }

        WorksheetAssignment assignment = WorksheetAssignment.create(
                worksheet, null, request.studentId(), now, request.dueAt());
        worksheetAssignmentRepository.save(assignment);

        worksheetAssignmentStudentRepository.save(
                WorksheetAssignmentStudent.create(assignment, request.studentId()));

        return WorksheetAssignmentCreateResponse.from(assignment, student.name(), 1);
    }

    /** 학습지 삭제 - 배포된 학습지는 소프트 삭제를 막는다. */
    @Transactional
    public void deleteWorksheet(long teacherId, long worksheetId) {
        Worksheet worksheet = getOwnedWorksheet(teacherId, worksheetId);
        if (worksheetAssignmentRepository.existsByWorksheetId(worksheetId)) {
            throw new BusinessException(ErrorCode.WORKSHEET_ALREADY_ASSIGNED);
        }
        worksheet.delete();
    }

    /** 소프트 삭제되지 않은 교사 소유 학습지를 조회한다. */
    private Worksheet getOwnedWorksheet(long teacherId, long worksheetId) {
        return worksheetRepository
                .findByIdAndOwnerTeacherIdAndDeletedAtIsNull(worksheetId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_NOT_FOUND));
    }

    /**
     * 반 상세를 조회한다. 없는 반과 남의 반을 같은 404로 묶어 반 존재 여부를 노출하지 않는다.
     * {@link SchoolClassService#getClassDetail}이 던지는 404·403을 여기서 변환한다.
     */
    private SchoolClassDetailResponse getOwnedClassDetail(long teacherId, long classId) {
        try {
            return schoolClassService.getClassDetail(teacherId, classId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.MEMBER_SCHOOL_CLASS_NOT_FOUND
                    || e.getErrorCode() == ErrorCode.MEMBER_SCHOOL_CLASS_NOT_OWNED) {
                throw new BusinessException(ErrorCode.WORKSHEET_CLASS_NOT_OWNED);
            }
            throw e;
        }
    }

    /** origin=custom이면 sourceAssignmentId가 있어야 하고, 아니면 없어야 한다. */
    private void validateSourceAssignment(WorksheetOrigin origin, Long sourceAssignmentId) {
        boolean isCustom = origin == WorksheetOrigin.CUSTOM;
        if (isCustom != (sourceAssignmentId != null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "origin이 custom이면 sourceAssignmentId가 필수이고, 아니면 없어야 합니다.");
        }
    }

    /**
     * 직전 차수 학습지를 확인해 돌려준다. 맞춤이 아니면 {@code null}이다.
     *
     * <p>차수는 저장 컬럼이 없고 이 체인의 깊이로 파생하므로, 부모가 어긋나면 화면의 차수가 통째로
     * 어긋난다. 그래서 프록시가 아니라 실제로 읽어 확인한다.
     *
     * <p>1차 맞춤은 부모가 출처 배정의 학습지 자신이어야 하고, 2차 이상은 부모가 같은 묶음
     * (같은 {@code sourceAssignmentId})에 속해야 한다. 후자가 두 컬럼의 정합성을 지키는 지점이다.
     */
    private Worksheet resolveParentWorksheet(long teacherId, WorksheetOrigin origin,
                                             Long parentWorksheetId, Long sourceAssignmentId) {
        boolean isCustom = origin == WorksheetOrigin.CUSTOM;
        if (isCustom != (parentWorksheetId != null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "origin이 custom이면 parentWorksheetId가 필수이고, 아니면 없어야 합니다.");
        }
        if (!isCustom) {
            return null;
        }

        Worksheet parent = worksheetRepository
                .findByIdAndOwnerTeacherIdAndDeletedAtIsNull(parentWorksheetId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_NOT_FOUND));

        boolean sameGroup = parent.getOrigin() == WorksheetOrigin.CUSTOM
                ? sourceAssignmentId.equals(parent.getSourceAssignment().getId())
                : worksheetAssignmentRepository.existsByIdAndWorksheetId(
                        sourceAssignmentId, parent.getId());
        if (!sameGroup) {
            throw new BusinessException(ErrorCode.WORKSHEET_PARENT_MISMATCH);
        }
        return parent;
    }

    /** displayOrder 집합이 {1..N}과 정확히 일치하는지, supportMode·customStage가 매핑 표의 값인지 확인한다. */
    private void validateDisplayOrdersAndSupportMapping(List<WorksheetItemRequest> items) {
        Set<Integer> displayOrders = items.stream()
                .map(WorksheetItemRequest::displayOrder)
                .collect(Collectors.toSet());
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, items.size())
                .boxed()
                .collect(Collectors.toSet());
        if (!displayOrders.equals(expectedOrders)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "displayOrder는 1부터 " + items.size() + "까지 중복·누락 없이 지정해야 합니다.");
        }
        for (WorksheetItemRequest item : items) {
            toSupportMode(item.supportMode());
            toCustomStage(item.customStage());
        }
    }

    /** 종합평가만 배점을 배분한다. 앞 N-1개는 내림, 가장 큰 displayOrder가 나머지를 받는다. */
    private Map<Integer, BigDecimal> computeMaxScoresByDisplayOrder(WorksheetType type, int itemCount) {
        Map<Integer, BigDecimal> maxScoresByDisplayOrder = new HashMap<>();
        if (type != WorksheetType.COMPREHENSIVE_ASSESSMENT) {
            for (int order = 1; order <= itemCount; order++) {
                maxScoresByDisplayOrder.put(order, null);
            }
            return maxScoresByDisplayOrder;
        }

        BigDecimal perItemScore = ASSESSMENT_TOTAL_SCORE_CENTS
                .divide(BigDecimal.valueOf(itemCount), 0, RoundingMode.DOWN)
                .movePointLeft(SCORE_SCALE);
        BigDecimal lastItemScore = ASSESSMENT_TOTAL_SCORE
                .subtract(perItemScore.multiply(BigDecimal.valueOf(itemCount - 1)))
                .setScale(SCORE_SCALE, RoundingMode.DOWN);

        for (int order = 1; order < itemCount; order++) {
            maxScoresByDisplayOrder.put(order, perItemScore);
        }
        maxScoresByDisplayOrder.put(itemCount, lastItemScore);
        return maxScoresByDisplayOrder;
    }

    private WorksheetType toWorksheetType(String value) {
        return switch (value) {
            case "practice" -> WorksheetType.GENERAL_LEARNING;
            case "assessment" -> WorksheetType.COMPREHENSIVE_ASSESSMENT;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "type 값이 올바르지 않습니다.");
        };
    }

    private WorksheetOrigin toWorksheetOrigin(String value) {
        return switch (value) {
            case "manual" -> WorksheetOrigin.STANDARD;
            case "custom" -> WorksheetOrigin.CUSTOM;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "origin 값이 올바르지 않습니다.");
        };
    }

    private String toSemester(String value) {
        return switch (value) {
            case "first" -> "1";
            case "second" -> "2";
            case "common" -> "COMMON";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "semester 값이 올바르지 않습니다.");
        };
    }

    private QuestionType toQuestionType(String value) {
        return switch (value) {
            case "choice" -> QuestionType.MULTIPLE_CHOICE;
            case "short" -> QuestionType.SHORT_INPUT;
            case "step" -> QuestionType.STEP_FILL;
            case "essay" -> QuestionType.ESSAY;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "questionType 값이 올바르지 않습니다.");
        };
    }

    private short toDifficulty(String value) {
        return switch (value) {
            case "low" -> (short) 1;
            case "mid" -> (short) 2;
            case "high" -> (short) 3;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "difficulty 값이 올바르지 않습니다.");
        };
    }

    private SupportMode toSupportMode(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "concept" -> SupportMode.CONCEPT_GUIDE;
            case "chat" -> SupportMode.CHATBOT;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "supportMode 값이 올바르지 않습니다.");
        };
    }

    private CustomStage toCustomStage(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "retrace" -> CustomStage.REVIEW;
            case "basic" -> CustomStage.SIMILAR;
            case "independent" -> CustomStage.ADVANCED;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "customStage 값이 올바르지 않습니다.");
        };
    }

    /** Worksheet 저장에서 사용하는 최종 questionId 기준 문항이다. */
    private record ResolvedWorksheetItem(
            Long questionId,
            Integer displayOrder,
            String supportMode,
            String customStage
    ) {
    }
}
