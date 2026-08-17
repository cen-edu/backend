package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.grading.dto.request.GradingAnswerPatchRequest;
import com.cenedu.backend.domain.grading.dto.response.GradingAnswerPatchResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingReleaseResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingResponseFormatter;
import com.cenedu.backend.domain.grading.entity.GradingRubricResult;
import com.cenedu.backend.domain.grading.repository.GradingRubricResultRepository;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.submission.repository.SubmissionAnswerRepository;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.AssignmentStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 교사가 점수를 고치고 반 단위로 확정한다(명세 9·10절). */
@Service
@RequiredArgsConstructor
@Transactional
public class GradingCommandService {

    private static final Set<AssignmentStatus> SUBMITTED_STATUSES =
            Set.of(AssignmentStatus.SUBMITTED, AssignmentStatus.GRADED);

    private static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.ONE;

    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final WorksheetAssignmentStudentRepository assignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final GradingRubricResultRepository gradingRubricResultRepository;
    private final ProblemAnswerUnitService problemAnswerUnitService;

    // ===== PATCH =====

    /**
     * 답안 한 칸을 고친다.
     *
     * <p>{@code released_at}이 있어도 허용한다 — 컬럼 주석이 "확정 후 수정은 정정으로 허용"이다.
     * 그 경우 {@code overridden_at > released_at}이 되어 목록의 {@code modified}가 켜진다.
     */
    public GradingAnswerPatchResponse patchAnswer(long teacherId, long assignmentId,
                                                  long submissionAnswerId,
                                                  GradingAnswerPatchRequest request) {
        GradingAnswerPatchRequest.Kind kind = request.kind();
        if (kind == null) {
            // finalScore·rubricChecks·resetToAuto 중 정확히 하나여야 한다(명세 9절).
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        WorksheetAssignment assignment = getOwnedAssignment(teacherId, assignmentId);
        SubmissionAnswer answer = submissionAnswerRepository.findById(submissionAnswerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND));
        WorksheetAssignmentStudent student = assignmentStudentRepository
                .findById(answer.getAssignmentStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND));
        // 경로의 배포와 답안이 속한 배포가 다르면 남의 자원이다.
        if (!student.getAssignment().getId().equals(assignmentId)) {
            throw new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND);
        }

        WorksheetItem item = findItem(assignment, answer.getAnswerUnitId());
        BigDecimal maxScore = item.getMaxScore() != null ? item.getMaxScore() : DEFAULT_MAX_SCORE;

        switch (kind) {
            case FINAL_SCORE -> applyFinalScore(answer, request.finalScore(), maxScore, teacherId);
            case RUBRIC_CHECKS -> applyRubricChecks(answer, request.rubricChecks(), teacherId);
            case RESET_TO_AUTO -> applyResetToAuto(answer);
        }

        boolean assessment =
                assignment.getWorksheet().getType() == WorksheetType.COMPREHENSIVE_ASSESSMENT;
        BigDecimal studentTotal = recalculateTotal(student, assessment);
        return new GradingAnswerPatchResponse(
                answer.getId(),
                answer.getFinalScore(),
                GradingResponseFormatter.toApiGradedBy(
                        answer.getOverriddenBy(), answer.getGradingStatus()),
                resolveItemResult(student.getId(), item, maxScore),
                studentTotal);
    }

    private void applyFinalScore(SubmissionAnswer answer, BigDecimal finalScore,
                                 BigDecimal maxScore, long teacherId) {
        if (finalScore.compareTo(BigDecimal.ZERO) < 0 || finalScore.compareTo(maxScore) > 0) {
            throw new BusinessException(ErrorCode.GRADING_SCORE_OUT_OF_RANGE);
        }
        answer.overrideScore(finalScore, teacherId, OffsetDateTime.now());
    }

    /**
     * 서술형 판정을 기록하고 <b>점수는 서버가 합산한다</b>.
     *
     * <p>충족한 기준의 배점을 더한 값이 점수다. 합이 문항 배점과 다르면 출제 쪽 데이터 문제이며
     * 여기서 깎지 않는다 — 채점 API 가 숨기면 어긋난 배점이 드러나지 않는다.
     *
     * <p>{@code evidence}는 {@code null}이다. 사람이 체크한 판정에는 근거 문자열이 없다(명세 6절).
     */
    private void applyRubricChecks(SubmissionAnswer answer,
                                   List<GradingAnswerPatchRequest.RubricCheck> checks,
                                   long teacherId) {
        long questionId = problemAnswerUnitService.getQuestionId(answer.getAnswerUnitId());
        Map<Long, ProblemRubricItem> rubricItems = gradingRubricResultRepository
                .findRubricItemsByQuestionIdIn(List.of(questionId)).stream()
                .collect(Collectors.toMap(ProblemRubricItem::getId, rubricItem -> rubricItem));

        Map<Long, GradingRubricResult> existing = gradingRubricResultRepository
                .findByStudentAnswerIdIn(List.of(answer.getId())).stream()
                .collect(Collectors.toMap(GradingRubricResult::getRubricItemId, result -> result));

        BigDecimal score = BigDecimal.ZERO;
        List<GradingRubricResult> created = new ArrayList<>();
        for (GradingAnswerPatchRequest.RubricCheck check : checks) {
            ProblemRubricItem rubricItem = rubricItems.get(check.rubricItemId());
            if (rubricItem == null) {
                throw new BusinessException(ErrorCode.GRADING_RUBRIC_ITEM_MISMATCH);
            }
            if (Boolean.TRUE.equals(check.satisfied())) {
                score = score.add(BigDecimal.valueOf(rubricItem.getWeight()));
            }
            GradingRubricResult result = existing.get(check.rubricItemId());
            if (result != null) {
                // 유니크 제약이 (답안, 기준 항목)을 묶고 있어 행을 새로 만들 수 없다.
                result.updateJudgement(Boolean.TRUE.equals(check.satisfied()), null);
            } else {
                created.add(GradingRubricResult.create(
                        answer.getId(), check.rubricItemId(),
                        Boolean.TRUE.equals(check.satisfied()), null));
            }
        }
        if (!created.isEmpty()) {
            gradingRubricResultRepository.saveAll(created);
        }
        answer.overrideScore(score, teacherId, OffsetDateTime.now());
    }

    /** 되돌릴 자동값이 없으면 쓸 수 없다 — {@code RUBRIC} 칸이 여기 해당한다(명세 9절). */
    private void applyResetToAuto(SubmissionAnswer answer) {
        if (answer.getAutoScore() == null) {
            throw new BusinessException(ErrorCode.GRADING_SCORE_OUT_OF_RANGE);
        }
        answer.resetToAutoScore();
    }

    // ===== 확정 =====

    /**
     * 반 단위 확정(명세 10절).
     *
     * <p>미채점이 남았는지 <b>서버도 검사한다</b> — 프론트가 버튼을 막지만 버튼 비활성은 UX 이지
     * 검증이 아니다.
     */
    public GradingReleaseResponse release(long teacherId, long assignmentId) {
        getOwnedAssignment(teacherId, assignmentId);
        List<WorksheetAssignmentStudent> students =
                assignmentStudentRepository.findByAssignment_Id(assignmentId);

        List<WorksheetAssignmentStudent> submitted = students.stream()
                .filter(student -> SUBMITTED_STATUSES.contains(student.getStatus()))
                .toList();
        if (submitted.stream().anyMatch(student -> student.getReleasedAt() != null)) {
            throw new BusinessException(ErrorCode.GRADING_ALREADY_RELEASED);
        }
        if (submitted.stream().anyMatch(student -> student.getStatus() != AssignmentStatus.GRADED)) {
            throw new BusinessException(ErrorCode.GRADING_INCOMPLETE);
        }

        OffsetDateTime releasedAt = OffsetDateTime.now();
        submitted.forEach(student -> student.release(releasedAt));
        // 미제출자는 확정을 막지 않는다. 상태만 남기고 released_at 은 채우지 않는다.
        students.stream()
                .filter(student -> !SUBMITTED_STATUSES.contains(student.getStatus()))
                .forEach(WorksheetAssignmentStudent::markNotSubmitted);

        return new GradingReleaseResponse(assignmentId, "confirmed", releasedAt, submitted.size());
    }

    // ===== 공통 =====

    /** 이 칸이 속한 문항의 학습지 항목. 배점이 여기서 나온다. */
    private WorksheetItem findItem(WorksheetAssignment assignment, long answerUnitId) {
        long questionId = problemAnswerUnitService.getQuestionId(answerUnitId);
        return worksheetItemRepository
                .findAllByWorksheetIdOrderByDisplayOrderAsc(assignment.getWorksheet().getId())
                .stream()
                .filter(item -> item.getQuestionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND));
    }

    /**
     * 점수 합을 다시 계산하고, 이 수정으로 전 칸이 채워졌으면 학생을 채점 완료로 올린다.
     *
     * <p><b>승격이 여기에도 있어야 한다.</b> 자동채점 배치만 학생 상태를 올리면, 서술형처럼
     * 교사가 손으로 채우는 칸이 마지막인 학생은 영원히 {@code SUBMITTED}에 남아 확정(10절)이
     * 막힌다. 서술형 자동채점이 없는 현 단계에서는 이 경로가 유일한 완료 경로다.
     *
     * <p>이미 완료된 학생의 점수를 고치는 것은 <b>정정</b>이라 {@code graded_at}을 다시 찍지 않는다.
     * 일반·맞춤 학습은 {@code total_score}가 "종합평가 전용" 컬럼이라 채우지 않는다.
     */
    private BigDecimal recalculateTotal(WorksheetAssignmentStudent student, boolean assessment) {
        List<SubmissionAnswer> answers =
                submissionAnswerRepository.findByAssignmentStudentId(student.getId());
        BigDecimal total = answers.stream()
                .filter(a -> a.getGradingStatus() == GradingStatus.GRADED && a.getFinalScore() != null)
                .map(SubmissionAnswer::getFinalScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (assessment) {
            student.updateTotalScore(total);
        }
        boolean allGraded = !answers.isEmpty()
                && answers.stream().allMatch(a -> a.getGradingStatus() == GradingStatus.GRADED);
        if (allGraded && student.getStatus() != AssignmentStatus.GRADED) {
            student.markGraded(OffsetDateTime.now(), assessment ? total : null);
        }
        return total;
    }

    /** 고친 칸이 속한 문항의 판정. 그 문항의 칸들을 다시 접어 계산한다. */
    private String resolveItemResult(long assignmentStudentId, WorksheetItem item,
                                     BigDecimal maxScore) {
        Set<Long> unitIds = gradingRubricResultRepository
                .findAnswerUnitsByQuestionIdIn(List.of(item.getQuestionId())).stream()
                .map(ProblemAnswerUnit::getId)
                .collect(Collectors.toSet());
        List<String> unitResults = submissionAnswerRepository
                .findByAssignmentStudentId(assignmentStudentId).stream()
                .filter(a -> unitIds.contains(a.getAnswerUnitId()))
                .map(a -> GradingResponseFormatter.classifyUnit(
                        a.getGradingStatus(), a.getFinalScore(), hasAnswer(a), maxScore))
                .toList();
        return GradingResponseFormatter.aggregateItemResult(unitResults);
    }

    private boolean hasAnswer(SubmissionAnswer answer) {
        return answer.getSelectedChoiceId() != null
                || (answer.getRawLatex() != null && !answer.getRawLatex().isBlank())
                || answer.getAnswerImageRef() != null;
    }

    private WorksheetAssignment getOwnedAssignment(long teacherId, long assignmentId) {
        WorksheetAssignment assignment = worksheetAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND));
        if (!assignment.getWorksheet().getOwnerTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND);
        }
        return assignment;
    }
}
