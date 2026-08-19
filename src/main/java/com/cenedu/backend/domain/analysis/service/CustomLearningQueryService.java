package com.cenedu.backend.domain.analysis.service;

import java.util.Arrays;
import java.util.Comparator;
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
import com.cenedu.backend.domain.worksheet.service.CustomSessionNumbering;
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

        List<CustomLearningSessionRow> rows = repository.findSessions(assignmentId, studentId);
        if (rows.isEmpty()) {
            return new CustomLearningSessionListResponse(List.of());
        }

        Map<Long, List<CustomLearningSessionRow>> rowsBySession = rows.stream()
                .collect(Collectors.groupingBy(
                        CustomLearningSessionRow::customAssignmentId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, Integer> sessionNumbers = sessionNumbers(rows);

        List<CustomLearningSessionListResponse.CustomLearningSession> sessions =
                rowsBySession.values().stream()
                        .map(sessionRows -> toSession(sessionRows, sessionNumbers))
                        .sorted(Comparator
                                .comparingInt(CustomLearningQueryService::sortKey)
                                .thenComparingLong(
                                        CustomLearningSessionListResponse
                                                .CustomLearningSession::customAssignmentId))
                        .toList();
        return new CustomLearningSessionListResponse(sessions);
    }

    /**
     * 학습지 ID 로 차수를 매긴다.
     *
     * <p>차수 규칙은 worksheet 도메인의 {@code CustomSessionNumbering} 이 정본이다. 여기서 다시
     * 구현하면 같은 학습지가 학습 현황·평가 결과 화면과 다른 차수로 보인다.
     *
     * <p>한 학습지가 여러 배정으로 나뉘어 오므로 학습지 축으로 접어서 넘긴다.
     */
    private Map<Long, Integer> sessionNumbers(List<CustomLearningSessionRow> rows) {
        return CustomSessionNumbering.depthByWorksheetId(
                rows.getFirst().rootWorksheetId(),
                rows.stream()
                        .collect(Collectors.toMap(
                                CustomLearningSessionRow::worksheetId,
                                row -> new CustomSessionNumbering.Node(
                                        row.worksheetId(), row.parentWorksheetId()),
                                (left, right) -> left))
                        .values());
    }

    /** 차수 미상(계보가 끊긴 데이터)은 맨 뒤로 보낸다. 1차인 척 앞에 세우면 화면이 거짓말을 한다. */
    private static int sortKey(
            CustomLearningSessionListResponse.CustomLearningSession session
    ) {
        return session.sessionNumber() == 0 ? Integer.MAX_VALUE : session.sessionNumber();
    }

    private CustomLearningSessionListResponse.CustomLearningSession toSession(
            List<CustomLearningSessionRow> rows,
            Map<Long, Integer> sessionNumbers
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
                sessionNumbers.getOrDefault(session.worksheetId(), 0),
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
