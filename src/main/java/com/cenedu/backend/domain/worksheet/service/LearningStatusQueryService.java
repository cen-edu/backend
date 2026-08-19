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
import com.cenedu.backend.domain.worksheet.dto.response.CustomLearningSummaryResponse;
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
import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningStudentCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningWorksheetCountRow;
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
    private final WorksheetTotalUnitsLoader totalUnitsLoader;

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
        Map<Long, CustomLearningSummaryResponse> customSummaries =
                customLearningSummaries(assignmentIds, now);

        List<LearningStatusAssignmentResponse> assignments = rows.stream()
                .map(row -> {
                    AssignmentStudentCountRow counts = countsByAssignmentId.get(row.assignmentId());
                    return LearningStatusAssignmentResponse.from(
                            row,
                            row.classId() == null ? null : classNamesByClassId.get(row.classId()),
                            isOngoing(row.dueAt(), now) ? "ongoing" : "completed",
                            totalUnitsByWorksheetId.getOrDefault(row.worksheetId(), 0),
                            counts == null ? 0 : Math.toIntExact(counts.studentCount()),
                            counts == null ? 0L : counts.submittedCount(),
                            customSummaries.get(row.assignmentId()));
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
     * <p>정렬 규칙은 {@link StudentDisplayOrder}가 갖는다. 맞춤 학습 화면이 같은 번호를 써야 해서
     * 규칙을 한 곳에 뒀다.
     */
    private List<LearningStatusStudentResponse> numbered(
            List<WorksheetAssignmentStudent> rows, Map<Long, String> namesByStudentId,
            WorksheetType type, OffsetDateTime dueAt
    ) {
        Map<Long, WorksheetAssignmentStudent> rowByStudentId = rows.stream()
                .collect(Collectors.toMap(WorksheetAssignmentStudent::getStudentId, row -> row));
        List<Long> ordered = StudentDisplayOrder.order(rowByStudentId.keySet(), namesByStudentId);

        List<LearningStatusStudentResponse> students = new java.util.ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Long studentId = ordered.get(index);
            students.add(LearningStatusStudentResponse.from(
                    rowByStudentId.get(studentId), type, dueAt,
                    namesByStudentId.get(studentId), index + 1));
        }
        return students;
    }

    /** 배포 하나의 진행률 분모. 목록과 같은 규칙을 쓴다. */
    private int totalUnits(com.cenedu.backend.domain.worksheet.entity.Worksheet worksheet) {
        return totalUnitsLoader.totalUnits(worksheet);
    }

    /**
     * 맞춤 학습 요약. 배정 목록 전체에 대해 두 쿼리로 낸다 — 배정마다 부르면 목록 길이만큼 쿼리가 든다.
     *
     * <p>학습지 축과 학생 축을 나눠 세는 이유는 한 쿼리로 묶으면 학습지 하나가 학생 수만큼
     * 늘어나(팬아웃) 집계마다 distinct 를 겹쳐 써야 하기 때문이다. 맞춤이 없는 배정은 두 집계 모두
     * 그룹이 나오지 않아 맵에 없고, 응답에서 {@code null}이 된다.
     */
    private Map<Long, CustomLearningSummaryResponse> customLearningSummaries(
            List<Long> assignmentIds, OffsetDateTime now
    ) {
        Map<Long, CustomLearningWorksheetCountRow> worksheetCounts = worksheetAssignmentRepository
                .summarizeCustomLearningWorksheets(assignmentIds).stream()
                .collect(Collectors.toMap(CustomLearningWorksheetCountRow::sourceAssignmentId, row -> row));
        if (worksheetCounts.isEmpty()) {
            return Map.of();
        }
        Map<Long, CustomLearningStudentCountRow> studentCounts = worksheetAssignmentRepository
                .summarizeCustomLearningStudents(worksheetCounts.keySet(), now).stream()
                .collect(Collectors.toMap(CustomLearningStudentCountRow::sourceAssignmentId, row -> row));

        return worksheetCounts.values().stream()
                .collect(Collectors.toMap(
                        CustomLearningWorksheetCountRow::sourceAssignmentId,
                        row -> CustomLearningSummaryResponse.from(
                                row, studentCounts.get(row.sourceAssignmentId()))));
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

    /** 학습지별 진행률 분모. 계산 규칙과 배치 조회는 {@link WorksheetTotalUnitsLoader}가 갖는다. */
    private Map<Long, Integer> totalUnits(List<LearningStatusAssignmentRow> rows) {
        return totalUnitsLoader.byWorksheetId(rows.stream()
                .collect(Collectors.toMap(
                        LearningStatusAssignmentRow::worksheetId,
                        LearningStatusAssignmentRow::type,
                        (left, right) -> left)));
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
