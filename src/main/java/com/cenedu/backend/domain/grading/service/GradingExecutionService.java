package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.grading.dto.request.GradingAutoRequest;
import com.cenedu.backend.domain.grading.dto.response.GradingAutoProgressResponse;
import com.cenedu.backend.domain.grading.dto.response.GradingAutoStartResponse;
import com.cenedu.backend.domain.grading.repository.GradingRubricResultRepository;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
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
import com.cenedu.backend.global.common.enums.CompareMethod;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동채점 실행 대상을 고르고 백그라운드로 넘긴다. 진행률 조회도 여기서 한다.
 *
 * <p>채점 자체는 {@link GradingBatchRunner}가 한다 — {@code @Async}는 프록시라 같은 빈 안에서
 * 부르면 동기로 돌아버린다.
 */
@Service
@RequiredArgsConstructor
public class GradingExecutionService {

    private static final Set<AssignmentStatus> SUBMITTED_STATUSES =
            Set.of(AssignmentStatus.SUBMITTED, AssignmentStatus.GRADED);

    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final WorksheetAssignmentStudentRepository assignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final GradingRubricResultRepository gradingRubricResultRepository;
    private final GradingJobRegistry gradingJobRegistry;
    private final GradingBatchRunner gradingBatchRunner;

    /**
     * 대상 칸을 확정하고 즉시 돌려준다. 실제 채점은 백그라운드에서 이어진다.
     *
     * <p>순서가 중요하다 — 소유권·미제출·중복 실행을 <b>대상 산정보다 먼저</b> 검사한다. 그래야
     * 거절할 요청 때문에 500칸을 읽지 않는다.
     */
    @Transactional(readOnly = true)
    public GradingAutoStartResponse start(long teacherId, long assignmentId,
                                          GradingAutoRequest request) {
        WorksheetAssignment assignment = getOwnedAssignment(teacherId, assignmentId);

        List<WorksheetAssignmentStudent> students =
                assignmentStudentRepository.findByAssignment_Id(assignmentId);
        Map<Long, WorksheetAssignmentStudent> studentsById = students.stream()
                .collect(Collectors.toMap(WorksheetAssignmentStudent::getId, student -> student));

        Set<Long> targetStudentIds = resolveTargetStudents(request, students, studentsById);
        Map<Long, Set<Long>> itemFilterByStudentId = resolveItemFilters(request);

        if (!gradingJobRegistry.tryStart(assignmentId)) {
            throw new BusinessException(ErrorCode.GRADING_ALREADY_RUNNING);
        }
        try {
            Selection selection = selectTargets(assignment, targetStudentIds, itemFilterByStudentId);
            boolean assessment =
                    assignment.getWorksheet().getType() == WorksheetType.COMPREHENSIVE_ASSESSMENT;
            gradingBatchRunner.run(assignmentId, assessment, selection.targets(), targetStudentIds);
            return new GradingAutoStartResponse(
                    selection.targets().size(), selection.skippedCount(), OffsetDateTime.now());
        } catch (RuntimeException e) {
            // 넘기기 전에 터졌으면 러너의 finally 가 돌지 않는다 — 자리를 직접 비운다.
            gradingJobRegistry.finish(assignmentId);
            throw e;
        }
    }

    /**
     * 채점할 학생. {@code targets}가 비면 제출한 전원이다.
     *
     * <p>교사가 지정한 학생이 미제출이면 거절한다 — 조용히 건너뛰면 교사는 채점된 줄 안다.
     */
    private Set<Long> resolveTargetStudents(GradingAutoRequest request,
                                            List<WorksheetAssignmentStudent> students,
                                            Map<Long, WorksheetAssignmentStudent> studentsById) {
        if (request.resolvedTargets().isEmpty()) {
            return students.stream()
                    .filter(student -> SUBMITTED_STATUSES.contains(student.getStatus()))
                    .map(WorksheetAssignmentStudent::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Set<Long> targetIds = new LinkedHashSet<>();
        for (GradingAutoRequest.Target target : request.resolvedTargets()) {
            WorksheetAssignmentStudent student = studentsById.get(target.assignmentStudentId());
            if (student == null) {
                throw new BusinessException(ErrorCode.GRADING_ASSIGNMENT_NOT_FOUND);
            }
            if (!SUBMITTED_STATUSES.contains(student.getStatus())) {
                throw new BusinessException(ErrorCode.GRADING_NOT_SUBMITTED);
            }
            targetIds.add(student.getId());
        }
        return targetIds;
    }

    /** 학생별 문항 제한. 값이 없으면 그 학생은 전 문항이 대상이다. */
    private Map<Long, Set<Long>> resolveItemFilters(GradingAutoRequest request) {
        Map<Long, Set<Long>> filters = new LinkedHashMap<>();
        for (GradingAutoRequest.Target target : request.resolvedTargets()) {
            List<Long> itemIds = target.worksheetItemIds();
            if (itemIds != null && !itemIds.isEmpty()) {
                filters.put(target.assignmentStudentId(), Set.copyOf(itemIds));
            }
        }
        return filters;
    }

    private record Selection(List<GradingBatchRunner.Target> targets, int skippedCount) {
    }

    /**
     * 대상 칸을 고른다.
     *
     * <p>교사가 이미 점수를 고친 칸({@code overridden_by IS NOT NULL})은 빼고 센다 — 자동채점이
     * 덮어쓰면 교사 작업이 조용히 사라진다(명세 7절). 되돌리려면 {@code resetToAuto}를 쓴다.
     *
     * <p><b>채점이 끝난 서술형 칸도 뺀다(D17).</b> 표식으로 {@code auto_score} 가 아니라
     * {@code grading_status} 를 본다 — 배점이 없는 학습지의 서술형은 점수 없이 판정만 남기므로
     * (D25) {@code auto_score} 가 계속 {@code null} 이고, 그것을 표식으로 쓰면 그 칸이 돌 때마다
     * 다시 LLM 에 들어간다. {@code recordGradingFailure} 는 상태를 {@code FAILED} 로 두므로
     * <b>실패한 칸은 다시 대상이 된다</b> — 의도한 동작이다.
     */
    private Selection selectTargets(WorksheetAssignment assignment, Set<Long> studentIds,
                                    Map<Long, Set<Long>> itemFilterByStudentId) {
        if (studentIds.isEmpty()) {
            return new Selection(List.of(), 0);
        }
        List<WorksheetItem> items = worksheetItemRepository
                .findAllByWorksheetIdOrderByDisplayOrderAsc(assignment.getWorksheet().getId());
        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();
        if (questionIds.isEmpty()) {
            return new Selection(List.of(), 0);
        }

        // 칸 → 문항 → 배점. 학생이 고른 문항만 남기려면 칸이 어느 문항 것인지 알아야 한다.
        Map<Long, Long> questionIdByAnswerUnitId = gradingRubricResultRepository
                .findAnswerUnitsByQuestionIdIn(questionIds).stream()
                .collect(Collectors.toMap(ProblemAnswerUnit::getId,
                        unit -> unit.getQuestion().getId()));
        Map<Long, WorksheetItem> itemByQuestionId = new LinkedHashMap<>();
        for (WorksheetItem item : items) {
            itemByQuestionId.putIfAbsent(item.getQuestionId(), item);
        }

        List<GradingBatchRunner.Target> targets = new ArrayList<>();
        int skipped = 0;
        for (SubmissionAnswer answer : submissionAnswerRepository
                .findByAssignmentStudentIdIn(studentIds)) {
            Long questionId = questionIdByAnswerUnitId.get(answer.getAnswerUnitId());
            WorksheetItem item = questionId == null ? null : itemByQuestionId.get(questionId);
            if (item == null) {
                continue;
            }
            Set<Long> itemFilter = itemFilterByStudentId.get(answer.getAssignmentStudentId());
            if (itemFilter != null && !itemFilter.contains(item.getId())) {
                continue;
            }
            if (answer.getOverriddenBy() != null) {
                skipped++;
                continue;
            }
            if (answer.getCompareMethod() == CompareMethod.RUBRIC
                    && answer.getGradingStatus() == GradingStatus.GRADED) {
                // D17 — 서술형은 LLM 이 한 번만 채점한다. 이후 정정은 전부 교사 수동이다.
                skipped++;
                continue;
            }
            targets.add(new GradingBatchRunner.Target(answer.getId(), item.getMaxScore()));
        }
        return new Selection(targets, skipped);
    }

    /**
     * 진행률(명세 8절).
     *
     * <p>세 카운트는 {@code grading_status}에서 직접 세고, 모집단은 <b>제출한 학생 전원의 전 칸</b>이다.
     * 지시서 8-4는 {@code totalCount}를 "대상 칸 수"라 적었지만 그 값은 실행 때만 메모리에 있어
     * 재시작하면 사라지고, 그러면 "{@code running: false}인데 {@code remainingCount > 0}이면 끊긴
     * 것"이라는 판정을 할 수 없다. 전원·전 칸으로 잡으면 세 카운트의 합이 {@code totalCount}와 같고
     * 재시작에도 값이 유지된다. 전원 전 문항을 채점하는 통상 경로에서는 두 정의가 같은 값이다.
     */
    @Transactional(readOnly = true)
    public GradingAutoProgressResponse getProgress(long teacherId, long assignmentId) {
        getOwnedAssignment(teacherId, assignmentId);
        List<Long> submittedStudentIds = assignmentStudentRepository
                .findByAssignment_Id(assignmentId).stream()
                .filter(student -> SUBMITTED_STATUSES.contains(student.getStatus()))
                .map(WorksheetAssignmentStudent::getId)
                .toList();
        boolean running = gradingJobRegistry.isRunning(assignmentId);
        if (submittedStudentIds.isEmpty()) {
            return new GradingAutoProgressResponse(running, 0, 0, 0, 0);
        }

        List<SubmissionAnswer> answers =
                submissionAnswerRepository.findByAssignmentStudentIdIn(submittedStudentIds);
        int graded = 0;
        int failed = 0;
        int remaining = 0;
        for (SubmissionAnswer answer : answers) {
            switch (answer.getGradingStatus()) {
                case GRADED -> graded++;
                case FAILED -> failed++;
                case NOT_GRADED -> remaining++;
            }
        }
        return new GradingAutoProgressResponse(running, answers.size(), graded, failed, remaining);
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
