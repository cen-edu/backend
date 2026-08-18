package com.cenedu.backend.domain.analysis.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.dto.response.StudentAnalysisSummaryResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentItemResultListResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.repository.StudentDetailQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnswerUnitRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습평가와 종합평가 학생 상세 화면의 공통 응답을 구성한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentDetailQueryService {

    private final AnalysisClassQueryService classQueryService;
    private final StudentDetailQueryRepository repository;
    private final AnalysisStatusClassifier statusClassifier;

    /** 선택 학생의 수행 요약과 취약 소분류를 반환한다. */
    public StudentAnalysisSummaryResponse getSummary(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        StudentAccess studentAccess = requireStudentAccess(
                teacherId, assignmentId, studentId);
        AnalysisAssignmentAccessRow access = studentAccess.assignment();
        var row = repository.findSummary(assignmentId, studentId);
        boolean usesScoreRate = access.worksheetType()
                == WorksheetType.COMPREHENSIVE_ASSESSMENT;
        List<StudentAnalysisSummaryResponse.WeakSubcategory> weaknesses = repository
                .findWeakSubcategories(assignmentId, studentId).stream()
                .map(weakness -> new StudentAnalysisSummaryResponse.WeakSubcategory(
                        weakness.subcategoryId(),
                        weakness.subcategoryName(),
                        weakness.incorrectCount(),
                        weakness.gradedCount(),
                        weakness.accuracyRate()))
                .toList();

        return new StudentAnalysisSummaryResponse(
                studentId,
                row.studentName(),
                access.className(),
                access.worksheetTitle(),
                access.worksheetType(),
                statusClassifier.classify(
                        row.gradedItemCount(),
                        usesScoreRate ? row.scoreRate() : row.accuracyRate()),
                row.totalItemCount(),
                row.gradedItemCount(),
                row.correctItemCount(),
                usesScoreRate ? row.scoreRate() : row.accuracyRate(),
                usesScoreRate ? row.classScoreRate() : row.classAccuracyRate(),
                usesScoreRate
                        ? row.totalSolvingDurationMs()
                        : null,
                usesScoreRate
                        ? row.classAverageSolvingDurationMs()
                        : null,
                weaknesses);
    }

    /** 선택 학생의 문항별 채점 결과와 답안 단위 응답을 반환한다. */
    public StudentItemResultListResponse getItems(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        StudentAccess studentAccess = requireStudentAccess(
                teacherId, assignmentId, studentId);
        AnalysisAssignmentAccessRow access = studentAccess.assignment();
        boolean includesSolvingDuration = access.worksheetType()
                == WorksheetType.COMPREHENSIVE_ASSESSMENT;
        Map<Long, List<StudentAnswerUnitRow>> answerUnitsByItem = repository
                .findAnswerUnits(assignmentId, studentId).stream()
                .collect(Collectors.groupingBy(StudentAnswerUnitRow::worksheetItemId));

        List<StudentItemResultListResponse.StudentItemResult> items = repository
                .findItems(assignmentId, studentId).stream()
                .map(row -> new StudentItemResultListResponse.StudentItemResult(
                        row.worksheetItemId(),
                        row.questionId(),
                        row.itemNumber(),
                        row.questionTitle(),
                        row.evaluationArea() == null
                                ? null
                                : EvaluationArea.valueOf(row.evaluationArea()),
                        includesSolvingDuration
                                ? AssessmentQuestionTypeGroup.from(
                                        QuestionType.valueOf(row.questionType()))
                                : null,
                        DifficultyBand.from(row.sourceDifficulty()),
                        row.gradingStatus(),
                        row.resultType(),
                        row.score(),
                        row.maxScore(),
                        includesSolvingDuration ? row.solvingDurationMs() : null,
                        includesSolvingDuration ? row.classMedianSolvingDurationMs() : null,
                        row.correctStudentCount(),
                        row.gradedStudentCount(),
                        row.classAccuracyRate(),
                        answerUnitsByItem.getOrDefault(
                                row.worksheetItemId(), List.of()).stream()
                                .map(this::toAnswerUnit)
                                .toList()))
                .toList();
        return new StudentItemResultListResponse(
                studentAccess.assignmentStudentId(), items);
    }

    private StudentAccess requireStudentAccess(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        AnalysisAssignmentAccessRow access = classQueryService.getAuthorizedAssignment(
                teacherId, assignmentId);
        long assignmentStudentId = repository.findAssignmentStudentId(
                        assignmentId, studentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED));
        return new StudentAccess(access, assignmentStudentId);
    }

    private StudentItemResultListResponse.AnswerUnitResult toAnswerUnit(
            StudentAnswerUnitRow row
    ) {
        return new StudentItemResultListResponse.AnswerUnitResult(
                row.answerUnitId(),
                row.displayOrder(),
                row.label(),
                row.diagnosticType() == null
                        ? null
                        : DiagnosticStage.valueOf(row.diagnosticType()),
                row.gradingStatus(),
                row.studentAnswer(),
                row.correctAnswer(),
                row.score(),
                row.resultType());
    }

    private record StudentAccess(
            AnalysisAssignmentAccessRow assignment,
            long assignmentStudentId
    ) {
    }
}
