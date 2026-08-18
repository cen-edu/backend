package com.cenedu.backend.domain.analysis.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.request.AnalysisAssignmentListRequest;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisAssignmentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisStudentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.ClassAnalysisOverviewResponse;
import com.cenedu.backend.domain.analysis.repository.AnalysisClassQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.ClassAnalysisOverviewRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 취약점 분석 화면 공통 진입부의 조회와 접근 권한을 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisClassQueryService {

    private final AnalysisClassQueryRepository repository;
    private final AnalysisStatusClassifier statusClassifier;

    /** 교사가 담당하는 반의 분석 대상 학습지 선택 목록을 반환한다. */
    public AnalysisAssignmentListResponse getAssignments(
            long teacherId,
            AnalysisAssignmentListRequest request
    ) {
        validateClassAccess(teacherId, request.classId());
        List<AnalysisAssignmentListResponse.AssignmentOption> assignments = repository
                .findAssignments(
                        teacherId,
                        request.classId(),
                        request.semester(),
                        request.worksheetType())
                .stream()
                .map(row -> new AnalysisAssignmentListResponse.AssignmentOption(
                        row.assignmentId(),
                        row.worksheetTitle(),
                        row.worksheetType(),
                        row.analysisAvailable()))
                .toList();
        return new AnalysisAssignmentListResponse(assignments);
    }

    /** 선택한 학습지의 학급 상단 분석 문맥과 집계 카드를 반환한다. */
    public ClassAnalysisOverviewResponse getOverview(long teacherId, long assignmentId) {
        AnalysisAssignmentAccessRow access = getAuthorizedAssignment(teacherId, assignmentId);
        ClassAnalysisOverviewRow row = repository.findOverview(
                assignmentId, access.worksheetType());
        boolean comprehensive = access.worksheetType()
                == WorksheetType.COMPREHENSIVE_ASSESSMENT;

        return new ClassAnalysisOverviewResponse(
                new ClassAnalysisOverviewResponse.AnalysisContext(
                        access.worksheetTitle(),
                        access.worksheetType(),
                        access.className(),
                        OffsetDateTime.now()),
                new ClassAnalysisOverviewResponse.ClassSummary(
                        row.participantCount(),
                        row.gradingPendingStudentCount(),
                        row.gradingPendingAnswerCount(),
                        row.classPerformanceRate(),
                        comprehensive ? row.averageSolvingDurationMs() : null,
                        comprehensive ? null : row.weaknessSubcategoryCount(),
                        row.weaknessStudentCount()));
    }

    /** 선택한 학습지의 학생별 성취율과 분석 상태를 이름순으로 반환한다. */
    public AnalysisStudentListResponse getStudents(long teacherId, long assignmentId) {
        AnalysisAssignmentAccessRow access = getAuthorizedAssignment(
                teacherId, assignmentId);
        List<AnalysisStudentListResponse.StudentItem> students = repository
                .findStudents(assignmentId, access.worksheetType())
                .stream()
                .map(row -> new AnalysisStudentListResponse.StudentItem(
                        row.studentId(),
                        row.studentName(),
                        statusClassifier.classify(
                                row.gradedItemCount(), row.performanceRate()),
                        row.performanceRate()))
                .toList();
        return new AnalysisStudentListResponse(students);
    }

    private void validateClassAccess(long teacherId, long classId) {
        long ownerTeacherId = repository.findClassOwnerTeacherId(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_CLASS_NOT_FOUND));
        if (ownerTeacherId != teacherId) {
            throw new BusinessException(ErrorCode.ANALYSIS_CLASS_ACCESS_DENIED);
        }
    }

    /** 배정이 존재하고 로그인 교사가 학습지와 반을 모두 소유하는지 확인한다. */
    AnalysisAssignmentAccessRow getAuthorizedAssignment(
            long teacherId,
            long assignmentId
    ) {
        AnalysisAssignmentAccessRow access = repository.findAssignmentAccess(assignmentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ANALYSIS_ASSIGNMENT_NOT_FOUND));
        if (access.worksheetOwnerTeacherId() != teacherId
                || access.homeroomTeacherId() != teacherId) {
            throw new BusinessException(ErrorCode.ANALYSIS_ASSIGNMENT_ACCESS_DENIED);
        }
        return access;
    }
}
