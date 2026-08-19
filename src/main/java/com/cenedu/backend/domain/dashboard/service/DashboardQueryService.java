package com.cenedu.backend.domain.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.dashboard.dto.request.DashboardAssignmentListRequest;
import com.cenedu.backend.domain.dashboard.dto.request.DashboardClassRequest;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardAssignmentListResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardStudentProgressResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.cenedu.backend.domain.dashboard.entity.enums.AssignmentProgressStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardStudentStatus;
import com.cenedu.backend.domain.dashboard.repository.DashboardQueryRepository;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentProgressRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentStatusRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardSummaryRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardWorksheetColumnRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 교사의 반·학기 대시보드 요약·학생 현황·학습지 목록을 구성한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardQueryService {

    private static final BigDecimal WEAKNESS_THRESHOLD_RATE = BigDecimal.valueOf(60);

    private final DashboardQueryRepository repository;
    private final DashboardStatusClassifier statusClassifier;

    /** 반의 학기 누적 요약과 학생 상태별 인원수를 반환한다. */
    public DashboardSummaryResponse getSummary(
            long teacherId,
            DashboardClassRequest request
    ) {
        validateClassAccess(teacherId, request.classId());
        DashboardSummaryRow summary = repository.findSummary(
                request.classId(), request.semester());
        Map<DashboardStudentStatus, Long> statusCounts = repository
                .findStudentStatuses(request.classId(), request.semester())
                .stream()
                .map(this::classifyStudent)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return new DashboardSummaryResponse(
                OffsetDateTime.now(),
                new DashboardSummaryResponse.LearningSummary(
                        summary.assignmentCount(),
                        summary.inProgressAssignmentCount(),
                        summary.classAccuracyRate(),
                        summary.aggregatedStudentCount(),
                        summary.incompleteSubmissionCount(),
                        summary.overdueSubmissionCount(),
                        summary.weaknessStudentCount(),
                        WEAKNESS_THRESHOLD_RATE),
                new DashboardSummaryResponse.StudentStatusCounts(
                        count(statusCounts, DashboardStudentStatus.DELAYED),
                        count(statusCounts, DashboardStudentStatus.NEEDS_SUPPORT),
                        count(statusCounts, DashboardStudentStatus.GOOD),
                        count(statusCounts, DashboardStudentStatus.INSUFFICIENT_DATA)));
    }

    /** 학습지 열과 현재 반의 학생별 진행·정답률·최근 학습을 반환한다. */
    public DashboardStudentProgressResponse getStudentProgress(
            long teacherId,
            DashboardClassRequest request
    ) {
        validateClassAccess(teacherId, request.classId());
        List<DashboardWorksheetColumnRow> columnRows = repository.findWorksheetColumns(
                request.classId(), request.semester());
        List<DashboardStudentProgressResponse.WorksheetColumn> columns = columnRows.stream()
                .map(row -> new DashboardStudentProgressResponse.WorksheetColumn(
                        row.assignmentId(),
                        row.worksheetTitle(),
                        row.worksheetType(),
                        row.worksheetOrigin(),
                        row.sourceAssignmentId()))
                .toList();
        Map<Long, WorksheetType> worksheetTypes = columnRows.stream()
                .collect(Collectors.toMap(
                        DashboardWorksheetColumnRow::assignmentId,
                        DashboardWorksheetColumnRow::worksheetType));
        Map<Long, List<DashboardStudentProgressRow>> rowsByStudent = repository
                .findStudentProgress(request.classId(), request.semester())
                .stream()
                .collect(Collectors.groupingBy(
                        DashboardStudentProgressRow::studentId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        OffsetDateTime now = OffsetDateTime.now();
        List<DashboardStudentProgressResponse.StudentProgress> students = rowsByStudent
                .values()
                .stream()
                .map(rows -> toStudentProgress(rows, worksheetTypes, columns.size(), now))
                .toList();
        return new DashboardStudentProgressResponse(columns, students);
    }

    /** 대시보드 하단의 학습지 배정 목록과 페이지 정보를 반환한다. */
    public DashboardAssignmentListResponse getAssignments(
            long teacherId,
            DashboardAssignmentListRequest request
    ) {
        validateClassAccess(teacherId, request.classId());
        long totalElements = repository.countAssignments(
                request.classId(), request.semester());
        OffsetDateTime now = OffsetDateTime.now();
        List<DashboardAssignmentListResponse.AssignmentItem> assignments = repository
                .findAssignments(
                        request.classId(),
                        request.semester(),
                        request.page(),
                        request.size())
                .stream()
                .map(row -> new DashboardAssignmentListResponse.AssignmentItem(
                        row.assignmentId(),
                        row.worksheetTitle(),
                        row.worksheetType(),
                        row.worksheetOrigin(),
                        row.sourceAssignmentId(),
                        row.assignedAt(),
                        row.dueAt(),
                        row.studentCount(),
                        row.submittedStudentCount(),
                        row.gradedStudentCount(),
                        statusClassifier.classifyAssignment(
                                row.studentCount(),
                                row.gradedStudentCount(),
                                row.dueAt(),
                                now),
                        statusClassifier.classifyResult(
                                row.submittedStudentCount(),
                                row.gradedStudentCount(),
                                row.releasedStudentCount())))
                .toList();
        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + request.size() - 1) / request.size());
        return new DashboardAssignmentListResponse(
                assignments,
                new DashboardAssignmentListResponse.PageInfo(
                        request.page(), totalElements, totalPages));
    }

    private DashboardStudentProgressResponse.StudentProgress toStudentProgress(
            List<DashboardStudentProgressRow> rows,
            Map<Long, WorksheetType> worksheetTypes,
            int totalAssignmentCount,
            OffsetDateTime now
    ) {
        DashboardStudentProgressRow student = rows.getFirst();
        List<DashboardStudentProgressResponse.WorksheetResult> results = rows.stream()
                .filter(row -> row.assignmentId() != null)
                .map(row -> toWorksheetResult(row, worksheetTypes.get(row.assignmentId()), now))
                .toList();
        int completedCount = (int) rows.stream()
                .filter(row -> row.assignmentStatus() == AssignmentStatus.SUBMITTED
                        || row.assignmentStatus() == AssignmentStatus.GRADED)
                .count();
        int gradedItemCount = rows.stream()
                .mapToInt(DashboardStudentProgressRow::gradedItemCount)
                .sum();
        int correctItemCount = rows.stream()
                .mapToInt(DashboardStudentProgressRow::correctItemCount)
                .sum();
        BigDecimal accuracyRate = percentage(correctItemCount, gradedItemCount);
        boolean delayed = results.stream()
                .anyMatch(result -> result.status() == AssignmentProgressStatus.OVERDUE);
        OffsetDateTime latestLearningAt = rows.stream()
                .map(DashboardStudentProgressRow::latestLearningAt)
                .filter(value -> value != null)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        return new DashboardStudentProgressResponse.StudentProgress(
                student.studentId(),
                student.studentName(),
                statusClassifier.classifyStudent(delayed, gradedItemCount, accuracyRate),
                completedCount,
                totalAssignmentCount,
                accuracyRate,
                latestLearningAt,
                results);
    }

    private DashboardStudentProgressResponse.WorksheetResult toWorksheetResult(
            DashboardStudentProgressRow row,
            WorksheetType worksheetType,
            OffsetDateTime now
    ) {
        AssignmentProgressStatus status = statusClassifier.classifyProgress(
                row.assignmentStatus(), row.progressCount(), row.dueAt(), now);
        BigDecimal resultValue = null;
        if (status == AssignmentProgressStatus.COMPLETED) {
            resultValue = worksheetType == WorksheetType.COMPREHENSIVE_ASSESSMENT
                    ? row.totalScore()
                    : percentage(row.correctItemCount(), row.gradedItemCount());
        }
        return new DashboardStudentProgressResponse.WorksheetResult(
                row.assignmentId(), status, resultValue);
    }

    private DashboardStudentStatus classifyStudent(DashboardStudentStatusRow row) {
        return statusClassifier.classifyStudent(
                row.delayed(), row.gradedItemCount(), row.accuracyRate());
    }

    private int count(Map<DashboardStudentStatus, Long> counts, DashboardStudentStatus status) {
        return counts.getOrDefault(status, 0L).intValue();
    }

    private BigDecimal percentage(int numerator, int denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator * 100L)
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private void validateClassAccess(long teacherId, long classId) {
        long ownerTeacherId = repository.findClassOwnerTeacherId(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DASHBOARD_CLASS_NOT_FOUND));
        if (ownerTeacherId != teacherId) {
            throw new BusinessException(ErrorCode.DASHBOARD_CLASS_ACCESS_DENIED);
        }
    }
}
