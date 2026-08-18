package com.cenedu.backend.domain.worksheet.service;

import java.text.Collator;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.domain.member.service.StudentListQueryService;
import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService;
import com.cenedu.backend.domain.worksheet.dto.request.LearningStatusListRequest;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusAssignmentResponse;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusListResponse;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusStudentResponse;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusStudentsResponse;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusSummaryResponse;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.domain.worksheet.repository.row.AssignmentStudentCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.LearningStatusAssignmentRow;
import com.cenedu.backend.domain.worksheet.repository.row.LearningStatusSummaryRow;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교사의 학습 현황 조회. 배포한 학습지별로 학생이 어디까지 왔는지 본다.
 *
 * <p>전부 읽기 전용이고 파생 값에 컬럼을 두지 않는다. 학습지 상태는 {@code due_at} 경과로,
 * 학생 상태는 {@code StudentResponseFormatter}로 판정한다 — 규칙을 복사하면 채점 화면·대시보드와
 * 같은 학생이 다르게 보인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningStatusQueryService {

    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final SchoolClassService schoolClassService;
    private final StudentListQueryService studentListQueryService;
    private final ProblemAnswerUnitService problemAnswerUnitService;

    /** 상태 필터가 받는 값. 오타가 조용히 빈 목록으로 나가지 않게 서버가 검사한다. */
    private static final Set<String> STUDENT_STATUSES =
            Set.of("not-started", "in-progress", "submitted", "not-submitted");

    public LearningStatusListResponse getLearningStatus(long teacherId, LearningStatusListRequest request) {
        Short grade = request.grade() == null ? null : request.grade().shortValue();
        String semester = request.semester() == null ? null : SemesterCodec.toDbSemester(request.semester());
        String keyword = toKeywordPattern(request.q());
        OffsetDateTime now = OffsetDateTime.now();

        List<LearningStatusAssignmentRow> rows = worksheetAssignmentRepository.findAllForLearningStatus(
                teacherId, grade, semester, request.classId(), keyword);
        LearningStatusSummaryRow summaryRow = worksheetAssignmentRepository.summarizeLearningStatus(
                teacherId, grade, semester, request.classId(), keyword, now);

        // 진행 중이고 맞춤이 아닌 배포 수. 목록을 이미 읽었으므로 쿼리를 더 쓰지 않는다.
        int ongoingCount = (int) rows.stream()
                .filter(row -> row.origin() != WorksheetOrigin.CUSTOM && isOngoing(row.dueAt(), now))
                .count();
        LearningStatusSummaryResponse summary = LearningStatusSummaryResponse.of(ongoingCount, summaryRow);

        if (rows.isEmpty()) {
            return LearningStatusListResponse.of(summary, List.of());
        }

        List<Long> assignmentIds = rows.stream().map(LearningStatusAssignmentRow::assignmentId).toList();
        Map<Long, AssignmentStudentCountRow> countsByAssignmentId = worksheetAssignmentRepository
                .countStudentsByAssignmentIdIn(assignmentIds).stream()
                .collect(Collectors.toMap(AssignmentStudentCountRow::assignmentId, row -> row));

        Map<Long, String> classNamesByClassId = classNames(teacherId, rows);
        Map<Long, Integer> totalUnitsByWorksheetId = totalUnits(rows);

        List<LearningStatusAssignmentResponse> assignments = rows.stream()
                .map(row -> {
                    AssignmentStudentCountRow counts = countsByAssignmentId.get(row.assignmentId());
                    return LearningStatusAssignmentResponse.from(
                            row,
                            row.classId() == null ? null : classNamesByClassId.get(row.classId()),
                            isOngoing(row.dueAt(), now) ? "ongoing" : "completed",
                            totalUnitsByWorksheetId.getOrDefault(row.worksheetId(), 0),
                            counts == null ? 0 : Math.toIntExact(counts.studentCount()),
                            counts == null ? 0L : counts.submittedCount());
                })
                .toList();

        return LearningStatusListResponse.of(summary, assignments);
    }


    /**
     * 선택한 배포의 학생별 진행 행. 명단은 {@code worksheet_assignment_student} 행이 정본이라
     * 반 명단을 다시 읽지 않는다 — 배포 후 전학 온 학생은 이 배포에 행이 없어 표에 나오지 않는다.
     */
    public LearningStatusStudentsResponse getStudents(long teacherId, long assignmentId, String status) {
        if (status != null && !STUDENT_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "status 값이 올바르지 않습니다.");
        }
        WorksheetAssignment assignment = worksheetAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND));
        // 불일치는 404 — 존재 여부를 노출하지 않는다(명세 2.1).
        if (!assignment.getWorksheet().getOwnerTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND);
        }

        List<WorksheetAssignmentStudent> rows =
                worksheetAssignmentStudentRepository.findByAssignment_Id(assignmentId);
        String className = assignment.getClassId() == null
                ? null
                : schoolClassService.getClassNamesByIds(teacherId, List.of(assignment.getClassId()))
                        .get(assignment.getClassId());
        int totalUnits = totalUnits(assignment.getWorksheet());

        if (rows.isEmpty()) {
            return LearningStatusStudentsResponse.from(assignment, className, totalUnits, List.of());
        }

        List<Long> studentIds = rows.stream().map(WorksheetAssignmentStudent::getStudentId).distinct().toList();
        // 담당이 바뀐 학생은 맵에 없다. 예외를 던지지 않고 이름을 비운다(명세 5.2).
        Map<Long, String> namesByStudentId = studentListQueryService.getStudentNamesByIds(teacherId, studentIds);

        List<LearningStatusStudentResponse> students = numbered(
                rows, namesByStudentId, assignment.getWorksheet().getType(), assignment.getDueAt());
        List<LearningStatusStudentResponse> filtered = status == null
                ? students
                : students.stream().filter(student -> status.equals(student.status())).toList();

        return LearningStatusStudentsResponse.from(assignment, className, totalUnits, filtered);
    }

    /**
     * 표시 순번 부여. <b>필터 전 전체 명단</b>을 이름순으로 세운 뒤 1부터 연속으로 준다 —
     * 필터 뒤에 매기면 같은 학생이 「전체」에서 7번, 「미제출」에서 2번으로 보인다.
     *
     * <p>이름 정렬은 {@link Collator}(한국어)를 쓴다. {@code String.compareTo}는 유니코드
     * 코드포인트 순이라 한글 이름 정렬이 직관과 어긋난다. 이름이 같으면 {@code studentId}
     * 오름차순으로 안정 정렬하고, 이름이 없는 학생은 뒤로 보낸다.
     */
    private List<LearningStatusStudentResponse> numbered(
            List<WorksheetAssignmentStudent> rows, Map<Long, String> namesByStudentId,
            com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType type, OffsetDateTime dueAt
    ) {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        List<WorksheetAssignmentStudent> sorted = rows.stream()
                .sorted(java.util.Comparator
                        .comparing((WorksheetAssignmentStudent was) ->
                                namesByStudentId.get(was.getStudentId()) == null)
                        .thenComparing(was -> namesByStudentId.getOrDefault(was.getStudentId(), ""), collator)
                        .thenComparing(WorksheetAssignmentStudent::getStudentId))
                .toList();

        List<LearningStatusStudentResponse> students = new java.util.ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            WorksheetAssignmentStudent was = sorted.get(i);
            students.add(LearningStatusStudentResponse.from(
                    was, type, dueAt, namesByStudentId.get(was.getStudentId()), i + 1));
        }
        return students;
    }

    /** 배포 하나의 진행률 분모. 목록과 같은 규칙을 쓴다. */
    private int totalUnits(com.cenedu.backend.domain.worksheet.entity.Worksheet worksheet) {
        List<WorksheetItem> items =
                worksheetItemRepository.findAllByWorksheetIdOrderByDisplayOrderAsc(worksheet.getId());
        if (worksheet.getType() == WorksheetType.COMPREHENSIVE_ASSESSMENT) {
            return WorksheetUnitCounter.totalUnits(worksheet.getType(), items, Map.of());
        }
        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();
        return WorksheetUnitCounter.totalUnits(
                worksheet.getType(), items, problemAnswerUnitService.countByQuestionIds(questionIds));
    }

    /** 반 이름은 ID를 모아 한 번에 읽는다. 배포마다 부르면 목록 길이만큼 쿼리가 는다. */
    private Map<Long, String> classNames(long teacherId, List<LearningStatusAssignmentRow> rows) {
        List<Long> classIds = rows.stream()
                .map(LearningStatusAssignmentRow::classId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return classIds.isEmpty() ? Map.of() : schoolClassService.getClassNamesByIds(teacherId, classIds);
    }

    /**
     * 학습지별 진행률 분모. 종합평가는 문항 수 집계 한 번이면 되고, 일반·맞춤 학습만 문항을 읽어
     * 칸 수를 센다 — 배점형에만 필요한 조회를 전체에 걸지 않는다.
     */
    private Map<Long, Integer> totalUnits(List<LearningStatusAssignmentRow> rows) {
        List<Long> assessmentWorksheetIds = worksheetIds(rows, WorksheetType.COMPREHENSIVE_ASSESSMENT);
        List<Long> practiceWorksheetIds = worksheetIds(rows, WorksheetType.GENERAL_LEARNING);

        Map<Long, Integer> totalUnitsByWorksheetId = new java.util.HashMap<>();
        if (!assessmentWorksheetIds.isEmpty()) {
            worksheetItemRepository.countByWorksheetIdIn(assessmentWorksheetIds).forEach(
                    row -> totalUnitsByWorksheetId.put(row.worksheetId(), Math.toIntExact(row.count())));
        }
        if (practiceWorksheetIds.isEmpty()) {
            return totalUnitsByWorksheetId;
        }

        Map<Long, List<WorksheetItem>> itemsByWorksheetId = worksheetItemRepository
                .findByWorksheetIdInOrderByWorksheetIdAscDisplayOrderAsc(practiceWorksheetIds).stream()
                .collect(Collectors.groupingBy(item -> item.getWorksheet().getId()));
        List<Long> questionIds = itemsByWorksheetId.values().stream()
                .flatMap(List::stream)
                .map(WorksheetItem::getQuestionId)
                .distinct()
                .toList();
        Map<Long, Long> answerUnitCountByQuestionId = problemAnswerUnitService.countByQuestionIds(questionIds);

        for (Long worksheetId : practiceWorksheetIds) {
            totalUnitsByWorksheetId.put(worksheetId, WorksheetUnitCounter.totalUnits(
                    WorksheetType.GENERAL_LEARNING,
                    itemsByWorksheetId.getOrDefault(worksheetId, List.of()),
                    answerUnitCountByQuestionId));
        }
        return totalUnitsByWorksheetId;
    }

    private List<Long> worksheetIds(List<LearningStatusAssignmentRow> rows, WorksheetType type) {
        return rows.stream()
                .filter(row -> row.type() == type)
                .map(LearningStatusAssignmentRow::worksheetId)
                .distinct()
                .toList();
    }

    private boolean isOngoing(OffsetDateTime dueAt, OffsetDateTime now) {
        return dueAt.isAfter(now);
    }

    /**
     * 제목 검색어를 소문자 {@code like} 패턴으로 만든다. 필터가 없으면 전체를 뜻하는 {@code "%"}다 —
     * {@code null}을 바인딩하면 타입 추론이 실패해 쿼리가 깨진다.
     */
    private String toKeywordPattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        return "%" + q.trim().toLowerCase() + "%";
    }
}
