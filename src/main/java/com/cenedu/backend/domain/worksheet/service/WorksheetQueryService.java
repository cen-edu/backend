package com.cenedu.backend.domain.worksheet.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.member.dto.request.SchoolClassListRequest;
import com.cenedu.backend.domain.member.dto.response.SchoolClassResponse;
import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.domain.member.service.StudentListQueryService;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.service.ProblemQuestionDetailService;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetListRequest;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetAssignmentHistoryResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetDetailItemResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetDetailResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetGenSpecItemResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetGenSpecPrefillResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetListItemResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetListResponse;
import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetGenSpecRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetRepository;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetGenSpecUnitRow;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetListRow;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문제 보관함 목록·상세·복제 프리필을 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetQueryService {

    private final WorksheetRepository worksheetRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final WorksheetGenSpecRepository worksheetGenSpecRepository;
    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final ProblemQuestionDetailService problemQuestionDetailService;
    private final CurriculumUnitQueryService curriculumUnitQueryService;
    private final SchoolClassService schoolClassService;
    private final StudentListQueryService studentListQueryService;

    /** 문제 보관함 목록 - 탭·학년·학기·검색어로 필터링한 학습지를 최신순으로 반환한다. */
    public WorksheetListResponse getWorksheets(long teacherId, WorksheetListRequest request) {
        Short grade = request.grade() == null ? null : request.grade().shortValue();
        String semester = request.semester() == null ? null : toDbSemester(request.semester());
        String tab = request.resolvedTab();
        WorksheetOrigin originFilter = resolveOriginFilter(tab);
        WorksheetType typeFilter = resolveTypeFilter(tab);
        String keyword = normalizeKeyword(request.q());

        List<WorksheetListRow> rows = keyword == null
                ? worksheetRepository.findAllForList(teacherId, grade, semester, originFilter, typeFilter)
                : worksheetRepository.findAllForListByKeyword(
                        teacherId, grade, semester, originFilter, typeFilter, keyword);

        if (rows.isEmpty()) {
            return WorksheetListResponse.from(List.of());
        }

        List<Long> worksheetIds = rows.stream().map(WorksheetListRow::id).toList();
        Map<Long, Long> problemCountByWorksheetId = toCountMap(
                worksheetItemRepository.countByWorksheetIdIn(worksheetIds));
        Map<Long, Long> assignmentCountByWorksheetId = toCountMap(
                worksheetAssignmentRepository.countByWorksheetIdIn(worksheetIds));
        Map<Long, String> unitSummaryByWorksheetId = buildUnitSummaries(worksheetIds);

        List<WorksheetListItemResponse> items = rows.stream()
                .map(row -> WorksheetListItemResponse.from(
                        row,
                        unitSummaryByWorksheetId.get(row.id()),
                        problemCountByWorksheetId.getOrDefault(row.id(), 0L),
                        assignmentCountByWorksheetId.getOrDefault(row.id(), 0L)))
                .toList();

        return WorksheetListResponse.from(items);
    }

    /** 학습지 상세 - 문항 본문·정답·해설과 출제 이력을 반환한다. */
    public WorksheetDetailResponse getWorksheetDetail(long teacherId, long worksheetId) {
        Worksheet worksheet = getOwnedWorksheet(teacherId, worksheetId);

        List<WorksheetAssignment> assignments = worksheetAssignmentRepository
                .findAllByWorksheetIdOrderByAssignedAtAsc(worksheetId);
        List<WorksheetAssignmentHistoryResponse> assignmentHistories =
                toAssignmentHistories(teacherId, assignments);

        List<WorksheetItem> items = worksheetItemRepository
                .findAllByWorksheetIdOrderByDisplayOrderAsc(worksheetId);
        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).toList();
        Map<Long, ProblemQuestionDetailResponse> questionsById = problemQuestionDetailService
                .getDetailsByIds(questionIds).stream()
                .collect(Collectors.toMap(ProblemQuestionDetailResponse::id, question -> question));
        List<WorksheetDetailItemResponse> itemResponses = items.stream()
                .map(item -> WorksheetDetailItemResponse.from(
                        item, questionsById.get(item.getQuestionId())))
                .toList();

        return WorksheetDetailResponse.from(worksheet, assignmentHistories, itemResponses);
    }

    /** 복제 프리필 - 학습지 생성 화면에 채울 출제 조건을 반환한다. */
    public WorksheetGenSpecPrefillResponse getGenSpecPrefill(long teacherId, long worksheetId) {
        Worksheet worksheet = getOwnedWorksheet(teacherId, worksheetId);
        List<WorksheetGenSpecItemResponse> genSpec = worksheetGenSpecRepository
                .findAllByWorksheetId(worksheetId).stream()
                .map(WorksheetGenSpecItemResponse::from)
                .toList();
        return WorksheetGenSpecPrefillResponse.from(worksheet, genSpec);
    }

    /** 소프트 삭제되지 않은 교사 소유 학습지를 조회한다. */
    private Worksheet getOwnedWorksheet(long teacherId, long worksheetId) {
        return worksheetRepository
                .findByIdAndOwnerTeacherIdAndDeletedAtIsNull(worksheetId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_NOT_FOUND));
    }

    /** 배포 행을 반·학생 이름과 dueAt 기준 파생 상태를 채운 출제 이력으로 변환한다. */
    private List<WorksheetAssignmentHistoryResponse> toAssignmentHistories(
            long teacherId, List<WorksheetAssignment> assignments
    ) {
        if (assignments.isEmpty()) {
            return List.of();
        }

        Set<Long> classIds = assignments.stream()
                .map(WorksheetAssignment::getClassId)
                .filter(classId -> classId != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> studentIds = assignments.stream()
                .map(WorksheetAssignment::getStudentId)
                .filter(studentId -> studentId != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, String> classNamesById = classIds.isEmpty() ? Map.of() : schoolClassService
                .getClasses(teacherId, new SchoolClassListRequest(null, null, null)).stream()
                .filter(schoolClass -> classIds.contains(schoolClass.id()))
                .collect(Collectors.toMap(SchoolClassResponse::id, SchoolClassResponse::name));
        Map<Long, String> studentNamesById = studentIds.isEmpty()
                ? Map.of()
                : studentListQueryService.getStudentNamesByIds(teacherId, List.copyOf(studentIds));

        OffsetDateTime now = OffsetDateTime.now();
        return assignments.stream()
                .map(assignment -> WorksheetAssignmentHistoryResponse.of(
                        assignment.getId(),
                        assignment.getClassId(),
                        assignment.getClassId() != null
                                ? classNamesById.get(assignment.getClassId())
                                : studentNamesById.get(assignment.getStudentId()),
                        assignment.getAssignedAt(),
                        assignment.getDueAt(),
                        now))
                .toList();
    }

    /** 목록에 필요한 대단원·첫 소단원 요약 문자열을 학습지 ID별로 조립한다. */
    private Map<Long, String> buildUnitSummaries(List<Long> worksheetIds) {
        List<WorksheetGenSpecUnitRow> unitRows = worksheetGenSpecRepository
                .findUnitRowsByWorksheetIdIn(worksheetIds);
        if (unitRows.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<Long>> subUnitIdsByWorksheetId = new LinkedHashMap<>();
        for (WorksheetGenSpecUnitRow row : unitRows) {
            List<Long> subUnitIds = subUnitIdsByWorksheetId
                    .computeIfAbsent(row.worksheetId(), key -> new ArrayList<>());
            if (!subUnitIds.contains(row.subUnitId())) {
                subUnitIds.add(row.subUnitId());
            }
        }
        for (List<Long> subUnitIds : subUnitIdsByWorksheetId.values()) {
            subUnitIds.sort(Comparator.naturalOrder());
        }

        Set<Long> allSubUnitIds = unitRows.stream()
                .map(WorksheetGenSpecUnitRow::subUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, CurriculumPathResponse> pathsBySubUnitId =
                curriculumUnitQueryService.getPathsBySubUnitIds(allSubUnitIds);

        Map<Long, String> unitSummaryByWorksheetId = new LinkedHashMap<>();
        subUnitIdsByWorksheetId.forEach((worksheetId, subUnitIds) -> {
            Long firstSubUnitId = subUnitIds.get(0);
            CurriculumPathResponse path = pathsBySubUnitId.get(firstSubUnitId);
            String summary = path.majorUnitName() + " · " + path.subUnitName();
            if (subUnitIds.size() > 1) {
                summary += " 외 " + (subUnitIds.size() - 1);
            }
            unitSummaryByWorksheetId.put(worksheetId, summary);
        });
        return unitSummaryByWorksheetId;
    }

    private Map<Long, Long> toCountMap(List<WorksheetCountRow> rows) {
        return rows.stream()
                .collect(Collectors.toMap(WorksheetCountRow::worksheetId, WorksheetCountRow::count));
    }

    /** tab이 custom이면 CUSTOM으로 좁힌다. 그 외는 유형 축으로 필터링하므로 origin은 제한하지 않는다. */
    private WorksheetOrigin resolveOriginFilter(String tab) {
        return "custom".equals(tab) ? WorksheetOrigin.CUSTOM : null;
    }

    /** tab이 practice/assessment면 해당 유형으로 좁힌다. all·custom은 유형을 제한하지 않는다. */
    private WorksheetType resolveTypeFilter(String tab) {
        return switch (tab) {
            case "practice" -> WorksheetType.GENERAL_LEARNING;
            case "assessment" -> WorksheetType.COMPREHENSIVE_ASSESSMENT;
            case "all", "custom" -> null;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "tab 값이 올바르지 않습니다.");
        };
    }

    private String toDbSemester(String semester) {
        return switch (semester) {
            case "first" -> "1";
            case "second" -> "2";
            case "common" -> "COMMON";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "semester 값이 올바르지 않습니다.");
        };
    }

    private String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim();
    }
}
