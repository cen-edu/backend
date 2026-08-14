package com.cenedu.backend.domain.analysis.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentItemAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.ScoreTimeDistributionResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.repository.ComprehensiveAssessmentQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentStudentItemRow;
import com.cenedu.backend.domain.analysis.repository.row.ScoreTimeStudentRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종합평가 학급 분석의 지표·문항 행렬·점수 시간 분포를 구성한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComprehensiveAssessmentQueryService {

    private final AnalysisClassQueryService classQueryService;
    private final ComprehensiveAssessmentQueryRepository repository;
    private final AnalysisStatusClassifier statusClassifier;
    private final AnalysisMedianCalculator medianCalculator;

    /** 종합평가의 문항 유형·난이도별 결과와 우선 확인 문항을 반환한다. */
    public ComprehensiveAssessmentInsightsResponse getInsights(
            long teacherId,
            long assignmentId
    ) {
        requireComprehensiveAssessment(teacherId, assignmentId);
        List<AssessmentGroupAggregateRow> aggregates = repository
                .findGroupAggregates(assignmentId);

        Map<String, AssessmentGroupAggregateRow> questionTypeRows = rowsByCode(
                aggregates, AssessmentGroupAggregateRow.GroupDimension.QUESTION_TYPE);
        List<ComprehensiveAssessmentInsightsResponse.QuestionTypeGroupResult>
                questionTypeGroups = Arrays.stream(AssessmentQuestionTypeGroup.values())
                .map(group -> toQuestionTypeResult(group, questionTypeRows.get(group.name())))
                .toList();

        Map<String, AssessmentGroupAggregateRow> difficultyRows = rowsByCode(
                aggregates, AssessmentGroupAggregateRow.GroupDimension.DIFFICULTY);
        List<ComprehensiveAssessmentInsightsResponse.DifficultyBandResult>
                difficultyBands = Arrays.stream(DifficultyBand.values())
                .map(band -> toDifficultyResult(band, difficultyRows.get(band.name())))
                .toList();

        List<ComprehensiveAssessmentInsightsResponse.ComprehensiveAssessmentPriorityItem>
                priorityItems = repository
                .findPriorityItems(assignmentId)
                .stream()
                .map(row -> new ComprehensiveAssessmentInsightsResponse
                        .ComprehensiveAssessmentPriorityItem(
                        row.worksheetItemId(),
                        row.itemNumber(),
                        row.questionTitle(),
                        DifficultyBand.from(row.sourceDifficulty()),
                        row.correctStudentCount(),
                        row.gradedStudentCount()))
                .toList();

        return new ComprehensiveAssessmentInsightsResponse(
                questionTypeGroups, difficultyBands, priorityItems);
    }

    /** 종합평가의 문항 열과 학생별 점수·채점 상태·풀이시간 행렬을 반환한다. */
    public ComprehensiveAssessmentItemAchievementResponse getItemAchievement(
            long teacherId,
            long assignmentId
    ) {
        requireComprehensiveAssessment(teacherId, assignmentId);
        List<ComprehensiveAssessmentItemAchievementResponse.AssessmentItemColumn> items =
                repository
                .findItemColumns(assignmentId)
                .stream()
                .map(row -> new ComprehensiveAssessmentItemAchievementResponse
                        .AssessmentItemColumn(
                        row.worksheetItemId(), row.itemNumber(), row.maxScore()))
                .toList();

        Map<Long, List<AssessmentStudentItemRow>> rowsByStudent = repository
                .findStudentItemResults(assignmentId)
                .stream()
                .collect(Collectors.groupingBy(
                        AssessmentStudentItemRow::studentId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<ComprehensiveAssessmentItemAchievementResponse.AssessmentStudentAchievement>
                students =
                rowsByStudent.values().stream()
                        .map(this::toStudentAchievement)
                        .toList();

        return new ComprehensiveAssessmentItemAchievementResponse(items, students);
    }

    /** 학생별 득점률·총시간과 두 값의 학급 중앙값을 반환한다. */
    public ScoreTimeDistributionResponse getScoreTimeDistribution(
            long teacherId,
            long assignmentId
    ) {
        requireComprehensiveAssessment(teacherId, assignmentId);
        List<ScoreTimeStudentRow> rows = repository.findScoreTimeStudents(assignmentId);
        List<ScoreTimeDistributionResponse.StudentDistribution> students = rows.stream()
                .map(row -> new ScoreTimeDistributionResponse.StudentDistribution(
                        row.studentId(),
                        row.studentName(),
                        statusClassifier.classify(row.gradedItemCount(), row.scoreRate()),
                        row.scoreRate(),
                        row.totalSolvingDurationMs()))
                .toList();
        return new ScoreTimeDistributionResponse(
                students,
                medianCalculator.scoreMedian(rows.stream()
                        .map(ScoreTimeStudentRow::scoreRate)
                        .toList()),
                medianCalculator.durationMedian(rows.stream()
                        .map(ScoreTimeStudentRow::totalSolvingDurationMs)
                        .toList()));
    }

    private void requireComprehensiveAssessment(long teacherId, long assignmentId) {
        AnalysisAssignmentAccessRow access = classQueryService.getAuthorizedAssignment(
                teacherId, assignmentId);
        if (access.worksheetType() != WorksheetType.COMPREHENSIVE_ASSESSMENT) {
            throw new BusinessException(ErrorCode.ANALYSIS_ASSIGNMENT_TYPE_MISMATCH);
        }
    }

    private Map<String, AssessmentGroupAggregateRow> rowsByCode(
            List<AssessmentGroupAggregateRow> rows,
            AssessmentGroupAggregateRow.GroupDimension dimension
    ) {
        return rows.stream()
                .filter(row -> row.dimension() == dimension)
                .collect(Collectors.toMap(
                        AssessmentGroupAggregateRow::groupCode,
                        Function.identity()));
    }

    private ComprehensiveAssessmentInsightsResponse.QuestionTypeGroupResult
            toQuestionTypeResult(
                    AssessmentQuestionTypeGroup group,
                    AssessmentGroupAggregateRow row
            ) {
        return new ComprehensiveAssessmentInsightsResponse.QuestionTypeGroupResult(
                group,
                row == null ? 0 : row.itemCount(),
                row == null ? null : row.accuracyRate(),
                row == null || row.gradedResultCount() == 0);
    }

    private ComprehensiveAssessmentInsightsResponse.DifficultyBandResult toDifficultyResult(
            DifficultyBand band,
            AssessmentGroupAggregateRow row
    ) {
        return new ComprehensiveAssessmentInsightsResponse.DifficultyBandResult(
                band,
                row == null ? 0 : row.itemCount(),
                row == null ? null : row.accuracyRate(),
                row == null || row.gradedResultCount() == 0);
    }

    private ComprehensiveAssessmentItemAchievementResponse.AssessmentStudentAchievement
            toStudentAchievement(List<AssessmentStudentItemRow> rows) {
        AssessmentStudentItemRow student = rows.getFirst();
        List<ComprehensiveAssessmentItemAchievementResponse.AssessmentItemResult> results =
                rows.stream()
                .map(row -> new ComprehensiveAssessmentItemAchievementResponse
                        .AssessmentItemResult(
                        row.worksheetItemId(),
                        row.gradingStatus(),
                        row.score(),
                        row.solvingDurationMs()))
                .toList();
        return new ComprehensiveAssessmentItemAchievementResponse
                .AssessmentStudentAchievement(
                student.studentId(), student.studentName(), results);
    }
}
