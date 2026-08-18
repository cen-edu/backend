package com.cenedu.backend.domain.analysis.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.analysis.report.AnalysisReportRequest;
import com.cenedu.backend.domain.analysis.repository.StudentDetailQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnalysisSummaryRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnswerUnitRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentItemDetailRow;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채점 결과를 AI에 넘길 모양으로 정리한다.
 *
 * <p><b>학생이 쓴 텍스트를 자르는 곳이 여기다.</b> 답안 원문이 그대로 프롬프트에 들어가므로,
 * 길이를 제한해 한 학생이 컨텍스트를 통째로 차지하거나 긴 지시문을 밀어 넣지 못하게 한다.
 * 내용 자체를 검사하지는 않는다 — 구현체가 이 값을 데이터로만 다루는 것이 방어의 본체다.
 */
@Component
@RequiredArgsConstructor
public class AnalysisReportRequestAssembler {

    /** 답안 한 칸에서 프롬프트로 넘길 최대 길이. 넘치면 잘라서 넘긴다. */
    private static final int MAX_ANSWER_LENGTH = 200;

    private final StudentDetailQueryRepository studentDetailRepository;

    /** 선택 학생의 채점 결과를 문장 생성 요청으로 정리한다. */
    public AnalysisReportRequest assemble(
            long assignmentId,
            long studentId,
            long assignmentStudentId
    ) {
        StudentAnalysisSummaryRow summary = studentDetailRepository
                .findSummary(assignmentId, studentId);
        List<StudentItemDetailRow> items = studentDetailRepository
                .findItems(assignmentId, studentId);
        Map<Long, List<StudentAnswerUnitRow>> answerUnitsByItem = studentDetailRepository
                .findAnswerUnits(assignmentId, studentId).stream()
                .collect(Collectors.groupingBy(StudentAnswerUnitRow::worksheetItemId));

        List<AnalysisReportRequest.GradedItem> gradedItems = items.stream()
                .filter(item -> item.gradingStatus() == GradingStatus.GRADED)
                .map(item -> toGradedItem(item, answerUnitsByItem))
                .toList();
        List<Integer> unansweredItemNumbers = items.stream()
                .filter(item -> item.gradingStatus() != GradingStatus.GRADED)
                .map(StudentItemDetailRow::itemNumber)
                .toList();
        List<AnalysisReportRequest.WeakSubcategory> weakSubcategories = studentDetailRepository
                .findWeakSubcategories(assignmentId, studentId).stream()
                .map(row -> new AnalysisReportRequest.WeakSubcategory(
                        row.subcategoryName(), row.accuracyRate()))
                .toList();

        return new AnalysisReportRequest(
                assignmentStudentId,
                new AnalysisReportRequest.StudentSummary(
                        summary.totalItemCount(),
                        summary.gradedItemCount(),
                        summary.correctItemCount(),
                        summary.accuracyRate(),
                        summary.classAccuracyRate()),
                gradedItems,
                unansweredItemNumbers,
                weakSubcategories);
    }

    private AnalysisReportRequest.GradedItem toGradedItem(
            StudentItemDetailRow item,
            Map<Long, List<StudentAnswerUnitRow>> answerUnitsByItem
    ) {
        List<AnalysisReportRequest.AnswerUnit> answerUnits = answerUnitsByItem
                .getOrDefault(item.worksheetItemId(), List.of()).stream()
                .map(unit -> new AnalysisReportRequest.AnswerUnit(
                        unit.label(),
                        truncate(unit.studentAnswer()),
                        truncate(unit.correctAnswer()),
                        name(unit.resultType())))
                .toList();
        return new AnalysisReportRequest.GradedItem(
                item.worksheetItemId(),
                item.itemNumber(),
                truncate(item.questionTitle()),
                item.evaluationArea(),
                DifficultyBand.from(item.sourceDifficulty()).name(),
                name(item.resultType()),
                item.score(),
                item.maxScore(),
                item.classAccuracyRate(),
                answerUnits);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ANSWER_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ANSWER_LENGTH);
    }

    private String name(StudentItemResultType resultType) {
        return resultType == null ? null : resultType.name();
    }
}
