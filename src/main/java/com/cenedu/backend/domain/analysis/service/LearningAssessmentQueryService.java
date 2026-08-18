package com.cenedu.backend.domain.analysis.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentLearningAssessmentPerformanceResponse;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.repository.LearningAssessmentQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningAssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningStudentSubcategoryRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentLearningGroupComparisonRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습평가 학급 분석의 영역·난이도 지표와 소분류 성취를 구성한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningAssessmentQueryService {

    private static final List<DifficultyBand> DIFFICULTY_DISPLAY_ORDER = List.of(
            DifficultyBand.LOW,
            DifficultyBand.MID,
            DifficultyBand.HIGH);

    private final AnalysisClassQueryService classQueryService;
    private final LearningAssessmentQueryRepository repository;

    /** 학습평가 학생의 평가 영역·난이도별 정답률을 학급과 비교해 반환한다. */
    public StudentLearningAssessmentPerformanceResponse getStudentPerformance(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        requireLearningAssessment(teacherId, assignmentId);
        if (!repository.existsAssignmentStudent(assignmentId, studentId)) {
            throw new BusinessException(ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED);
        }

        List<StudentLearningGroupComparisonRow> comparisons = repository
                .findStudentGroupComparisons(assignmentId, studentId);
        Map<String, StudentLearningGroupComparisonRow> evaluationAreaRows =
                comparisonRowsByCode(
                        comparisons,
                        StudentLearningGroupComparisonRow.GroupDimension.EVALUATION_AREA);
        List<StudentLearningAssessmentPerformanceResponse.EvaluationAreaComparison>
                evaluationAreas = Arrays.stream(EvaluationArea.values())
                .map(area -> toEvaluationAreaComparison(
                        area, evaluationAreaRows.get(area.name())))
                .toList();

        Map<String, StudentLearningGroupComparisonRow> difficultyRows =
                comparisonRowsByCode(
                        comparisons,
                        StudentLearningGroupComparisonRow.GroupDimension.DIFFICULTY);
        List<StudentLearningAssessmentPerformanceResponse.DifficultyBandComparison>
                difficultyBands = DIFFICULTY_DISPLAY_ORDER.stream()
                .map(band -> toDifficultyBandComparison(band, difficultyRows.get(band.name())))
                .toList();

        List<StudentLearningAssessmentPerformanceResponse.StudentSubcategoryResult>
                subcategoryResults = repository
                .findStudentSubcategoryDetails(assignmentId, studentId).stream()
                .map(row -> new StudentLearningAssessmentPerformanceResponse
                        .StudentSubcategoryResult(
                        row.subcategoryId(),
                        row.subcategoryName(),
                        row.correctCount(),
                        row.gradedCount()))
                .toList();

        return new StudentLearningAssessmentPerformanceResponse(
                evaluationAreas, difficultyBands, subcategoryResults);
    }

    /** 학습평가의 평가 영역·난이도별 결과와 우선 확인 문항을 반환한다. */
    public LearningAssessmentInsightsResponse getInsights(
            long teacherId,
            long assignmentId
    ) {
        requireLearningAssessment(teacherId, assignmentId);
        List<LearningAssessmentGroupAggregateRow> aggregates = repository
                .findGroupAggregates(assignmentId);

        Map<String, LearningAssessmentGroupAggregateRow> evaluationAreaRows = rowsByCode(
                aggregates,
                LearningAssessmentGroupAggregateRow.GroupDimension.EVALUATION_AREA);
        List<LearningAssessmentInsightsResponse.EvaluationAreaResult> evaluationAreas =
                Arrays.stream(EvaluationArea.values())
                        .map(area -> toEvaluationAreaResult(
                                area, evaluationAreaRows.get(area.name())))
                        .toList();

        Map<String, LearningAssessmentGroupAggregateRow> difficultyRows = rowsByCode(
                aggregates,
                LearningAssessmentGroupAggregateRow.GroupDimension.DIFFICULTY);
        List<LearningAssessmentInsightsResponse.DifficultyBandResult> difficultyBands =
                DIFFICULTY_DISPLAY_ORDER.stream()
                        .map(band -> toDifficultyBandResult(
                                band, difficultyRows.get(band.name())))
                        .toList();

        List<LearningAssessmentInsightsResponse.LearningAssessmentPriorityItem>
                priorityItems = repository.findPriorityItems(assignmentId).stream()
                .map(row -> new LearningAssessmentInsightsResponse
                        .LearningAssessmentPriorityItem(
                        row.worksheetItemId(),
                        row.itemNumber(),
                        row.questionTitle(),
                        row.evaluationArea() == null
                                ? null
                                : EvaluationArea.valueOf(row.evaluationArea()),
                        DifficultyBand.from(row.sourceDifficulty()),
                        row.correctStudentCount(),
                        row.gradedStudentCount()))
                .toList();

        return new LearningAssessmentInsightsResponse(
                evaluationAreas, difficultyBands, priorityItems);
    }

    /** 학습평가의 소분류 열, 학생별 정답 수 행렬과 취약 학생 순위를 반환한다. */
    public LearningAssessmentAchievementResponse getAchievement(
            long teacherId,
            long assignmentId
    ) {
        requireLearningAssessment(teacherId, assignmentId);
        List<LearningAssessmentAchievementResponse.SubcategoryColumn> subcategories =
                repository.findSubcategoryColumns(assignmentId).stream()
                        .map(row -> new LearningAssessmentAchievementResponse
                                .SubcategoryColumn(
                                row.subcategoryId(), row.subcategoryName()))
                        .toList();

        Map<Long, List<LearningStudentSubcategoryRow>> rowsByStudent = repository
                .findStudentSubcategoryResults(assignmentId).stream()
                .collect(Collectors.groupingBy(
                        LearningStudentSubcategoryRow::studentId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<LearningAssessmentAchievementResponse.LearningAssessmentStudentAchievement>
                students = rowsByStudent.values().stream()
                .map(this::toStudentAchievement)
                .toList();

        List<LearningAssessmentAchievementResponse.SubcategoryWeakness>
                subcategoryRanking = repository
                .findSubcategoryWeaknesses(assignmentId).stream()
                .map(row -> new LearningAssessmentAchievementResponse.SubcategoryWeakness(
                        row.subcategoryId(),
                        row.subcategoryName(),
                        row.weakStudentCount()))
                .toList();

        return new LearningAssessmentAchievementResponse(
                subcategories, students, subcategoryRanking);
    }

    private void requireLearningAssessment(long teacherId, long assignmentId) {
        AnalysisAssignmentAccessRow access = classQueryService.getAuthorizedAssignment(
                teacherId, assignmentId);
        if (access.worksheetType() != WorksheetType.GENERAL_LEARNING) {
            throw new BusinessException(
                    ErrorCode.ANALYSIS_LEARNING_ASSESSMENT_TYPE_MISMATCH);
        }
    }

    private Map<String, LearningAssessmentGroupAggregateRow> rowsByCode(
            List<LearningAssessmentGroupAggregateRow> rows,
            LearningAssessmentGroupAggregateRow.GroupDimension dimension
    ) {
        return rows.stream()
                .filter(row -> row.dimension() == dimension)
                .collect(Collectors.toMap(
                        LearningAssessmentGroupAggregateRow::groupCode,
                        Function.identity()));
    }

    private LearningAssessmentInsightsResponse.EvaluationAreaResult
            toEvaluationAreaResult(
                    EvaluationArea area,
                    LearningAssessmentGroupAggregateRow row
            ) {
        return new LearningAssessmentInsightsResponse.EvaluationAreaResult(
                area,
                row == null ? 0 : row.itemCount(),
                row == null ? null : row.accuracyRate(),
                isReferenceOnly(row));
    }

    private LearningAssessmentInsightsResponse.DifficultyBandResult
            toDifficultyBandResult(
                    DifficultyBand band,
                    LearningAssessmentGroupAggregateRow row
            ) {
        return new LearningAssessmentInsightsResponse.DifficultyBandResult(
                band,
                row == null ? 0 : row.itemCount(),
                row == null ? null : row.accuracyRate(),
                isReferenceOnly(row));
    }

    private boolean isReferenceOnly(LearningAssessmentGroupAggregateRow row) {
        return row == null || row.itemCount() < 2 || row.gradedResultCount() == 0;
    }

    private Map<String, StudentLearningGroupComparisonRow> comparisonRowsByCode(
            List<StudentLearningGroupComparisonRow> rows,
            StudentLearningGroupComparisonRow.GroupDimension dimension
    ) {
        return rows.stream()
                .filter(row -> row.dimension() == dimension)
                .collect(Collectors.toMap(
                        StudentLearningGroupComparisonRow::groupCode,
                        Function.identity()));
    }

    private StudentLearningAssessmentPerformanceResponse.EvaluationAreaComparison
            toEvaluationAreaComparison(
                    EvaluationArea area,
                    StudentLearningGroupComparisonRow row
            ) {
        return new StudentLearningAssessmentPerformanceResponse.EvaluationAreaComparison(
                area,
                row == null ? 0 : row.itemCount(),
                row == null ? null : row.studentAccuracyRate(),
                row == null ? null : row.classAccuracyRate(),
                isStudentReferenceOnly(row));
    }

    private StudentLearningAssessmentPerformanceResponse.DifficultyBandComparison
            toDifficultyBandComparison(
                    DifficultyBand band,
                    StudentLearningGroupComparisonRow row
            ) {
        return new StudentLearningAssessmentPerformanceResponse.DifficultyBandComparison(
                band,
                row == null ? 0 : row.itemCount(),
                row == null ? null : row.studentAccuracyRate(),
                row == null ? null : row.classAccuracyRate(),
                isStudentReferenceOnly(row));
    }

    /** 학생과 학급 중 한쪽이라도 채점 완료 결과가 없으면 비교값을 참고용으로 표시한다. */
    private boolean isStudentReferenceOnly(StudentLearningGroupComparisonRow row) {
        return row == null
                || row.studentGradedResultCount() == 0
                || row.classGradedResultCount() == 0;
    }

    private LearningAssessmentAchievementResponse.LearningAssessmentStudentAchievement
            toStudentAchievement(List<LearningStudentSubcategoryRow> rows) {
        LearningStudentSubcategoryRow student = rows.getFirst();
        List<LearningAssessmentAchievementResponse.SubcategoryResult> results = rows.stream()
                .map(row -> new LearningAssessmentAchievementResponse.SubcategoryResult(
                        row.subcategoryId(), row.correctCount(), row.gradedCount()))
                .toList();
        return new LearningAssessmentAchievementResponse
                .LearningAssessmentStudentAchievement(
                student.studentId(), student.studentName(), results);
    }
}
