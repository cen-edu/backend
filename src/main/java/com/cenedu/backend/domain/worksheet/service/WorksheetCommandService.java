package com.cenedu.backend.domain.worksheet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cenedu.backend.domain.problem.service.ProblemQuestionDetailService;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetCreateRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetGenSpecRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetItemRequest;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetCreateResponse;
import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.entity.WorksheetGenSpec;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;
import com.cenedu.backend.domain.worksheet.entity.enums.SupportMode;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
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
    private final ProblemQuestionDetailService problemQuestionDetailService;

    /** 학습지 저장 - 검증을 마친 뒤 학습지·출제조건·문항을 한 트랜잭션으로 저장한다. */
    @Transactional
    public WorksheetCreateResponse createWorksheet(long teacherId, WorksheetCreateRequest request) {
        WorksheetType type = toWorksheetType(request.type());
        WorksheetOrigin origin = toWorksheetOrigin(request.origin());
        String semester = toSemester(request.semester());

        List<WorksheetItemRequest> items = request.items();
        List<Long> questionIds = items.stream().map(WorksheetItemRequest::questionId).toList();
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
        validateDisplayOrdersAndSupportMapping(items);

        WorksheetAssignment sourceAssignment = origin == WorksheetOrigin.CUSTOM
                ? worksheetAssignmentRepository.getReferenceById(request.sourceAssignmentId())
                : null;

        Short totalScore = type == WorksheetType.COMPREHENSIVE_ASSESSMENT ? (short) 100 : null;

        Worksheet worksheet = Worksheet.create(
                request.title(), type, origin, teacherId,
                request.grade().shortValue(), semester, totalScore, sourceAssignment);
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

    /** origin=custom이면 sourceAssignmentId가 있어야 하고, 아니면 없어야 한다. */
    private void validateSourceAssignment(WorksheetOrigin origin, Long sourceAssignmentId) {
        boolean isCustom = origin == WorksheetOrigin.CUSTOM;
        if (isCustom != (sourceAssignmentId != null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "origin이 custom이면 sourceAssignmentId가 필수이고, 아니면 없어야 합니다.");
        }
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
}
