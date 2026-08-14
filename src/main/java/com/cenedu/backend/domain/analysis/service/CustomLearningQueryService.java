package com.cenedu.backend.domain.analysis.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.dto.response.CustomLearningSessionListResponse;
import com.cenedu.backend.domain.analysis.entity.enums.CustomResolutionStatus;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.repository.CustomLearningQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.CustomLearningSessionRow;
import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학생 상세 화면에 맞춤 학습 회차와 단계별 성취를 제공한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomLearningQueryService {

    private final AnalysisClassQueryService classQueryService;
    private final CustomLearningQueryRepository repository;

    /** 원본 학습지에서 파생된 학생의 모든 맞춤 학습 회차를 반환한다. */
    public CustomLearningSessionListResponse getSessions(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        classQueryService.getAuthorizedAssignment(teacherId, assignmentId);
        if (!repository.existsSourceAssignmentStudent(assignmentId, studentId)) {
            throw new BusinessException(ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED);
        }

        Map<Long, List<CustomLearningSessionRow>> rowsBySession = repository
                .findSessions(assignmentId, studentId)
                .stream()
                .collect(Collectors.groupingBy(
                        CustomLearningSessionRow::customAssignmentId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<CustomLearningSessionListResponse.CustomLearningSession> sessions =
                rowsBySession.values().stream()
                        .map(this::toSession)
                        .toList();
        return new CustomLearningSessionListResponse(sessions);
    }

    private CustomLearningSessionListResponse.CustomLearningSession toSession(
            List<CustomLearningSessionRow> rows
    ) {
        CustomLearningSessionRow session = rows.getFirst();
        Map<Long, List<CustomLearningSessionRow>> rowsBySubcategory = rows.stream()
                .collect(Collectors.groupingBy(
                        CustomLearningSessionRow::subcategoryId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<CustomLearningSessionListResponse.CustomSubcategoryResult> subcategories =
                rowsBySubcategory.values().stream()
                        .map(this::toSubcategory)
                        .toList();

        return new CustomLearningSessionListResponse.CustomLearningSession(
                session.customAssignmentId(),
                overallResolutionStatus(subcategories),
                session.assignedAt(),
                session.completedAt(),
                session.sessionCompletedItemCount(),
                session.sessionTotalItemCount(),
                session.sessionAccuracyRate(),
                subcategories);
    }

    private CustomLearningSessionListResponse.CustomSubcategoryResult toSubcategory(
            List<CustomLearningSessionRow> rows
    ) {
        CustomLearningSessionRow subcategory = rows.getFirst();
        Map<CustomStage, CustomLearningSessionRow> rowsByStage = rows.stream()
                .filter(row -> row.customStage() != null)
                .collect(Collectors.toMap(
                        CustomLearningSessionRow::customStage,
                        Function.identity()));
        List<CustomLearningSessionListResponse.CustomStageResult> stages =
                Arrays.stream(CustomStage.values())
                        .map(stage -> toStage(stage, rowsByStage.get(stage)))
                        .toList();

        return new CustomLearningSessionListResponse.CustomSubcategoryResult(
                subcategory.subcategoryId(),
                subcategory.subcategoryName(),
                resolutionStatus(rows),
                difficultyBand(subcategory.currentDifficulty()),
                subcategory.subcategoryCompletedItemCount(),
                subcategory.subcategoryTotalItemCount(),
                subcategory.sourceAccuracyRate(),
                subcategory.accuracyRate(),
                stages);
    }

    private CustomLearningSessionListResponse.CustomStageResult toStage(
            CustomStage stage,
            CustomLearningSessionRow row
    ) {
        return new CustomLearningSessionListResponse.CustomStageResult(
                stage,
                row == null ? 0 : row.stageCorrectCount(),
                row == null ? 0 : row.stageTotalCount());
    }

    private CustomResolutionStatus resolutionStatus(List<CustomLearningSessionRow> rows) {
        CustomLearningSessionRow subcategory = rows.getFirst();
        boolean receivedAppliedItem = rows.stream()
                .anyMatch(row -> row.customStage() == CustomStage.ADVANCED
                        && row.stageTotalCount() > 0);
        boolean clearedHighDifficulty = Integer.valueOf(3).equals(
                subcategory.currentDifficulty())
                && subcategory.diagnosticTotalItemCount() > 0
                && subcategory.diagnosticCompletedItemCount()
                    == subcategory.diagnosticTotalItemCount()
                && subcategory.diagnosticCorrectItemCount()
                    == subcategory.diagnosticTotalItemCount();
        if (receivedAppliedItem || clearedHighDifficulty) {
            return CustomResolutionStatus.RESOLVED;
        }
        if (subcategory.diagnosticTotalItemCount() == 0
                || subcategory.diagnosticCompletedItemCount()
                    < subcategory.diagnosticTotalItemCount()) {
            return CustomResolutionStatus.IN_PROGRESS;
        }
        return CustomResolutionStatus.UNRESOLVED;
    }

    private CustomResolutionStatus overallResolutionStatus(
            List<CustomLearningSessionListResponse.CustomSubcategoryResult> subcategories
    ) {
        if (subcategories.stream().allMatch(subcategory ->
                subcategory.resolutionStatus() == CustomResolutionStatus.RESOLVED)) {
            return CustomResolutionStatus.RESOLVED;
        }
        if (subcategories.stream().anyMatch(subcategory ->
                subcategory.resolutionStatus() == CustomResolutionStatus.IN_PROGRESS)) {
            return CustomResolutionStatus.IN_PROGRESS;
        }
        return CustomResolutionStatus.UNRESOLVED;
    }

    private DifficultyBand difficultyBand(Integer sourceDifficulty) {
        return sourceDifficulty == null ? null : DifficultyBand.from(sourceDifficulty);
    }
}
