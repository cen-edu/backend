package com.cenedu.backend.domain.worksheet.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.domain.member.service.StudentListQueryService;
import com.cenedu.backend.domain.worksheet.dto.response.CustomLearningStatusResponse;
import com.cenedu.backend.domain.worksheet.dto.response.CustomLearningStudentResponse;
import com.cenedu.backend.domain.worksheet.dto.response.CustomLearningWorksheetResponse;
import com.cenedu.backend.domain.worksheet.dto.response.LearningStatusStudentResponse;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningAssignmentRow;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본 배정 하나에 딸린 맞춤 학습 현황.
 *
 * <p>맞춤 배정은 학생 한 명씩이라 화면 카드는 <b>맞춤 학습지</b> 단위로 묶는다. 조회 수는 맞춤
 * 학습지·학생 수와 무관하게 일정하다 — 카드마다 학생을 다시 읽으면 카드 수만큼 쿼리가 는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomLearningStatusQueryService {

    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final SchoolClassService schoolClassService;
    private final StudentListQueryService studentListQueryService;
    private final WorksheetTotalUnitsLoader totalUnitsLoader;

    /** 원본 배정에서 파생된 맞춤 학습을 학습지별로 묶어 학생 현황과 함께 반환한다. */
    public CustomLearningStatusResponse getCustomLearning(long teacherId, long sourceAssignmentId) {
        WorksheetAssignment source = worksheetAssignmentRepository.findDetailById(sourceAssignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND));
        // 불일치는 404 — 존재 여부를 노출하지 않는다(명세 2.1).
        if (!source.getWorksheet().getOwnerTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.WORKSHEET_ASSIGNMENT_NOT_FOUND);
        }

        String className = source.getClassId() == null
                ? null
                : schoolClassService.getClassNamesByIds(teacherId, List.of(source.getClassId()))
                        .get(source.getClassId());

        List<CustomLearningAssignmentRow> rows =
                worksheetAssignmentRepository.findCustomLearningAssignments(sourceAssignmentId);
        if (rows.isEmpty()) {
            return CustomLearningStatusResponse.of(source, className, List.of());
        }

        return CustomLearningStatusResponse.of(
                source, className, buildWorksheets(teacherId, sourceAssignmentId, rows));
    }

    private List<CustomLearningWorksheetResponse> buildWorksheets(
            long teacherId, long sourceAssignmentId, List<CustomLearningAssignmentRow> rows
    ) {
        List<Long> assignmentIds = rows.stream().map(CustomLearningAssignmentRow::assignmentId).toList();
        Map<Long, WorksheetAssignmentStudent> studentRowByAssignmentId =
                worksheetAssignmentStudentRepository.findByAssignment_IdIn(assignmentIds).stream()
                        .collect(Collectors.toMap(
                                was -> was.getAssignment().getId(), was -> was, (left, right) -> left));

        List<WorksheetAssignmentStudent> roster =
                worksheetAssignmentStudentRepository.findByAssignment_Id(sourceAssignmentId);
        // 이름은 한 번만 읽는다. 순번과 표시가 같은 이름을 써야 하고, 두 번 부르면 그 사이에
        // 담당이 바뀐 학생이 순번과 표시에서 다르게 취급된다.
        Map<Long, String> namesByStudentId = names(teacherId, roster, rows);
        Map<Long, Integer> displayNumbers = StudentDisplayOrder.numbers(
                roster.stream().map(WorksheetAssignmentStudent::getStudentId).toList(),
                customStudentIds(rows),
                namesByStudentId);

        Map<Long, Integer> totalUnitsByWorksheetId = totalUnitsLoader.byWorksheetId(rows.stream()
                .collect(Collectors.toMap(
                        CustomLearningAssignmentRow::worksheetId,
                        CustomLearningAssignmentRow::type,
                        (left, right) -> left)));

        Map<Long, List<CustomLearningAssignmentRow>> rowsByWorksheetId = rows.stream()
                .collect(Collectors.groupingBy(
                        CustomLearningAssignmentRow::worksheetId, LinkedHashMap::new, Collectors.toList()));

        // 회차는 저장 컬럼이 없다. 학습지의 가장 이른 배정일 오름차순으로 매기고, 같으면 학습지 ID로
        // 안정 정렬한다 — 한 배치로 만들어지면 배정일이 같을 수 있고, 그때 순서가 흔들리면 안 된다.
        List<Map.Entry<Long, List<CustomLearningAssignmentRow>>> ordered =
                rowsByWorksheetId.entrySet().stream()
                        .sorted(Comparator
                                .comparing((Map.Entry<Long, List<CustomLearningAssignmentRow>> entry) ->
                                        earliestAssignedAt(entry.getValue()))
                                .thenComparing(Map.Entry::getKey))
                        .toList();

        List<CustomLearningWorksheetResponse> worksheets = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            List<CustomLearningAssignmentRow> worksheetRows = ordered.get(index).getValue();
            CustomLearningAssignmentRow first = worksheetRows.getFirst();
            worksheets.add(CustomLearningWorksheetResponse.of(
                    first.worksheetId(),
                    first.title(),
                    first.type(),
                    index + 1,
                    earliestAssignedAt(worksheetRows),
                    latestDueAt(worksheetRows),
                    totalUnitsByWorksheetId.getOrDefault(first.worksheetId(), 0),
                    students(worksheetRows, studentRowByAssignmentId, namesByStudentId, displayNumbers)));
        }
        return worksheets;
    }

    /**
     * 학생 행. 상태 판정에는 카드 대표 마감이 아니라 <b>그 학생 배정의 마감</b>을 넘긴다 —
     * 맞춤은 배정이 학생마다 따로라, 대표값을 쓰면 자기 마감이 지난 학생이 미시작으로 보인다.
     */
    private List<CustomLearningStudentResponse> students(
            List<CustomLearningAssignmentRow> worksheetRows,
            Map<Long, WorksheetAssignmentStudent> studentRowByAssignmentId,
            Map<Long, String> namesByStudentId,
            Map<Long, Integer> displayNumbers
    ) {
        return worksheetRows.stream()
                .map(row -> {
                    WorksheetAssignmentStudent was = studentRowByAssignmentId.get(row.assignmentId());
                    if (was == null) {
                        return null;
                    }
                    return CustomLearningStudentResponse.of(row.assignmentId(),
                            LearningStatusStudentResponse.from(
                                    was, row.type(), row.dueAt(),
                                    namesByStudentId.get(was.getStudentId()),
                                    displayNumbers.getOrDefault(was.getStudentId(), 0)));
                })
                .filter(student -> student != null)
                .sorted(Comparator.comparingInt(CustomLearningStudentResponse::displayNumber))
                .toList();
    }

    /**
     * 원본 명단과 맞춤 대상의 이름을 한 번에 읽는다.
     *
     * <p>표시 순번은 <b>원본 배정 명단</b>으로 매긴다(호출부 참조). 카드마다 1부터 다시 매기면 같은
     * 화면의 원본 표와 번호가 어긋나고, 맞춤 배정은 학생 한 명씩이라 전원 1번이 되어 뜻이 없다.
     */
    private Map<Long, String> names(
            long teacherId, List<WorksheetAssignmentStudent> roster, List<CustomLearningAssignmentRow> rows
    ) {
        List<Long> studentIds = Stream.concat(
                        roster.stream().map(WorksheetAssignmentStudent::getStudentId),
                        customStudentIds(rows).stream())
                .distinct()
                .toList();
        return studentIds.isEmpty() ? Map.of() : studentListQueryService.getStudentNamesByIds(teacherId, studentIds);
    }

    /** 맞춤 배정이 가리키는 학생. 맞춤은 학생 단위 배정이라 {@code studentId}가 채워져 있다. */
    private List<Long> customStudentIds(List<CustomLearningAssignmentRow> rows) {
        return rows.stream()
                .map(CustomLearningAssignmentRow::studentId)
                .filter(studentId -> studentId != null)
                .distinct()
                .toList();
    }

    private OffsetDateTime earliestAssignedAt(List<CustomLearningAssignmentRow> rows) {
        return rows.stream().map(CustomLearningAssignmentRow::assignedAt)
                .min(OffsetDateTime::compareTo).orElseThrow();
    }

    private OffsetDateTime latestDueAt(List<CustomLearningAssignmentRow> rows) {
        return rows.stream().map(CustomLearningAssignmentRow::dueAt)
                .max(OffsetDateTime::compareTo).orElseThrow();
    }
}
