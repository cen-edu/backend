package com.cenedu.backend.domain.submission.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemAnswerUnitRepository;
import com.cenedu.backend.domain.problem.repository.ProblemChoiceRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.submission.dto.request.StudentAnswerSaveRequest;
import com.cenedu.backend.domain.submission.dto.request.StudentAnswerUnitSaveRequest;
import com.cenedu.backend.domain.submission.dto.response.StudentAnswerSaveResponse;
import com.cenedu.backend.domain.submission.dto.response.StudentSubmitResponse;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.domain.submission.entity.SubmissionQuestionTime;
import com.cenedu.backend.domain.submission.entity.enums.AnswerInputMode;
import com.cenedu.backend.domain.submission.repository.SubmissionAnswerRepository;
import com.cenedu.backend.domain.submission.repository.SubmissionQuestionTimeRepository;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import com.cenedu.backend.global.common.enums.QuestionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생 학습지 답안 저장·제출. 절차 순서는 명세 6-2/7절 그대로 고정한다 — 소유권 → 제출완료 →
 * 마감 → 칸 소속 검증 → 보기 소속 검증 → 스냅샷 → upsert → 진행률 재계산.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StudentSubmissionService {

    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final ProblemQuestionRepository problemQuestionRepository;
    private final ProblemChoiceRepository problemChoiceRepository;
    private final ProblemAnswerUnitRepository problemAnswerUnitRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final SubmissionQuestionTimeRepository submissionQuestionTimeRepository;

    public StudentAnswerSaveResponse saveAnswers(
            long studentId, long assignmentStudentId, long worksheetItemId, StudentAnswerSaveRequest request
    ) {
        WorksheetAssignmentStudent was = loadOwnedAssignment(studentId, assignmentStudentId);
        rejectIfSubmitted(was);
        rejectIfDuePassed(was);

        long worksheetId = was.getAssignment().getWorksheet().getId();
        WorksheetItem item = worksheetItemRepository.findById(worksheetItemId)
                .filter(candidate -> candidate.getWorksheet().getId().equals(worksheetId))
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND));
        ProblemQuestion question = problemQuestionRepository.findById(item.getQuestionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND));

        Map<Long, ProblemAnswerUnit> unitsForItem = problemAnswerUnitRepository
                .findAllByQuestionIds(List.of(item.getQuestionId())).stream()
                .collect(Collectors.toMap(ProblemAnswerUnit::getId, unit -> unit));
        Set<Long> validChoiceIds = problemChoiceRepository.findAllByQuestionIds(List.of(item.getQuestionId()))
                .stream()
                .map(ProblemChoice::getId)
                .collect(Collectors.toSet());

        // 4·5번을 건너뛰지 않는다 — 학생이 남의 문항 칸/보기 ID로 자기 배정에 답안을 만들 수 있다.
        for (StudentAnswerUnitSaveRequest answer : request.answers()) {
            if (!unitsForItem.containsKey(answer.answerUnitId())) {
                throw new BusinessException(ErrorCode.SUBMISSION_ANSWER_UNIT_MISMATCH);
            }
            if (answer.selectedChoiceId() != null && !validChoiceIds.contains(answer.selectedChoiceId())) {
                throw new BusinessException(ErrorCode.SUBMISSION_ANSWER_UNIT_MISMATCH);
            }
        }

        AnswerInputMode inputMode = toInputMode(question.getQuestionType());
        for (StudentAnswerUnitSaveRequest answer : request.answers()) {
            ProblemAnswerUnit unit = unitsForItem.get(answer.answerUnitId());
            String answerImageRef = Boolean.TRUE.equals(answer.hasHandwriting())
                    ? assembleAnswerImageRef(assignmentStudentId, answer.answerUnitId())
                    : null;

            submissionAnswerRepository
                    .findByAssignmentStudentIdAndAnswerUnitId(assignmentStudentId, answer.answerUnitId())
                    .ifPresentOrElse(
                            existing -> existing.updateAnswer(inputMode, answer.selectedChoiceId(),
                                    answer.rawLatex(), answerImageRef, unit.getCompareMethod()),
                            () -> submissionAnswerRepository.save(SubmissionAnswer.create(
                                    assignmentStudentId, answer.answerUnitId(), inputMode,
                                    answer.selectedChoiceId(), answer.rawLatex(), null, answerImageRef,
                                    unit.getCompareMethod())));
        }

        submissionQuestionTimeRepository
                .findByAssignmentStudentIdAndWorksheetItemId(assignmentStudentId, worksheetItemId)
                .ifPresentOrElse(
                        existing -> existing.updateTimeSpentSeconds(request.timeSpentSeconds()),
                        () -> submissionQuestionTimeRepository.save(SubmissionQuestionTime.create(
                                assignmentStudentId, worksheetItemId, request.timeSpentSeconds())));

        WorksheetType worksheetType = was.getAssignment().getWorksheet().getType();
        List<WorksheetItem> allItems = worksheetItemRepository
                .findAllByWorksheetIdOrderByDisplayOrderAsc(worksheetId);
        int totalUnits = totalUnits(worksheetType, allItems);
        short doneUnits = (short) recalcProgressCount(assignmentStudentId, worksheetType);
        was.updateProgressCount(doneUnits);

        return StudentAnswerSaveResponse.from(
                doneUnits, totalUnits, was.getStatus(), doneUnits, was.getAssignment().getDueAt());
    }

    public StudentSubmitResponse submit(long studentId, long assignmentStudentId) {
        WorksheetAssignmentStudent was = loadOwnedAssignment(studentId, assignmentStudentId);
        rejectIfSubmitted(was);
        rejectIfDuePassed(was);

        OffsetDateTime submittedAt = OffsetDateTime.now();
        was.submit(submittedAt);

        long worksheetId = was.getAssignment().getWorksheet().getId();
        WorksheetType worksheetType = was.getAssignment().getWorksheet().getType();
        List<WorksheetItem> items = worksheetItemRepository
                .findAllByWorksheetIdOrderByDisplayOrderAsc(worksheetId);
        int totalUnits = totalUnits(worksheetType, items);
        int answeredUnits = recalcProgressCount(assignmentStudentId, worksheetType);

        return StudentSubmitResponse.from(submittedAt, answeredUnits, totalUnits);
    }

    private WorksheetAssignmentStudent loadOwnedAssignment(long studentId, long assignmentStudentId) {
        WorksheetAssignmentStudent was = worksheetAssignmentStudentRepository.findDetailById(assignmentStudentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND));
        // 소유권 위반은 별도 코드 없이 WORKSHEET_ASSIGNMENT_NOT_FOUND — 존재 여부를 노출하지 않는다.
        if (was.getStudentId() != studentId) {
            throw new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND);
        }
        return was;
    }

    private void rejectIfSubmitted(WorksheetAssignmentStudent was) {
        if (was.getStatus() == AssignmentStatus.SUBMITTED || was.getStatus() == AssignmentStatus.GRADED) {
            throw new BusinessException(ErrorCode.SUBMISSION_ALREADY_SUBMITTED);
        }
    }

    private void rejectIfDuePassed(WorksheetAssignmentStudent was) {
        if (was.getAssignment().getDueAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.SUBMISSION_DUE_PASSED);
        }
    }

    /** COMPREHENSIVE_ASSESSMENT는 문항 수, GENERAL_LEARNING은 문항들의 답안 칸 수 합(명세 §4). */
    private int totalUnits(WorksheetType type, List<WorksheetItem> items) {
        if (type == WorksheetType.COMPREHENSIVE_ASSESSMENT) {
            return items.size();
        }
        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();
        return problemAnswerUnitRepository.findAllByQuestionIds(questionIds).size();
    }

    /** doneUnits/totalUnits와 같은 축으로 재계산한다. 종합평가에서 칸을 세면 축이 어긋난다. */
    private int recalcProgressCount(long assignmentStudentId, WorksheetType type) {
        List<SubmissionAnswer> answers = submissionAnswerRepository.findByAssignmentStudentId(assignmentStudentId);
        if (type == WorksheetType.GENERAL_LEARNING) {
            return answers.size();
        }
        List<Long> answerUnitIds = answers.stream().map(SubmissionAnswer::getAnswerUnitId).distinct().toList();
        return (int) problemAnswerUnitRepository.findAllById(answerUnitIds).stream()
                .map(unit -> unit.getQuestion().getId())
                .distinct()
                .count();
    }

    private AnswerInputMode toInputMode(QuestionType questionType) {
        return switch (questionType) {
            case MULTIPLE_CHOICE -> AnswerInputMode.CHOICE;
            case ESSAY -> AnswerInputMode.IMAGE;
            case SHORT_INPUT, STEP_FILL -> AnswerInputMode.HANDWRITING;
        };
    }

    /**
     * 답안 필기 이미지의 저장 키 조립. {@code (assignmentStudentId, answerUnitId)}로 완전히
     * 결정되므로 서버가 조립한다(명세 6절). API 경로가 아니라 S3 오브젝트 키다 — 키 형식은 아직
     * 확정 전이라(명세 10-②) 규칙을 이 메서드 한 곳에만 둔다.
     */
    private String assembleAnswerImageRef(long assignmentStudentId, long answerUnitId) {
        return "answers/%d/units/%d".formatted(assignmentStudentId, answerUnitId);
    }
}
