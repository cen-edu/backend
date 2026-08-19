package com.cenedu.backend.domain.worksheet.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService;
import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 진행률 분모(totalUnits) 조회. "종합평가는 문항 수, 나머지는 칸 수 합"이라는 규칙을 쓰는 화면이
 * 여럿이라 조회까지 한 곳에 둔다 — 규칙만 {@link WorksheetUnitCounter}에 두고 조회를 화면마다
 * 다시 짜면 배치 방식이 갈려 같은 학습지가 다른 분모를 갖는다.
 */
@Component
@RequiredArgsConstructor
class WorksheetTotalUnitsLoader {

    private final WorksheetItemRepository worksheetItemRepository;
    private final ProblemAnswerUnitService problemAnswerUnitService;

    /** 학습지 하나의 진행률 분모. */
    int totalUnits(Worksheet worksheet) {
        List<WorksheetItem> items =
                worksheetItemRepository.findAllByWorksheetIdOrderByDisplayOrderAsc(worksheet.getId());
        if (worksheet.getType() == WorksheetType.COMPREHENSIVE_ASSESSMENT) {
            return WorksheetUnitCounter.totalUnits(worksheet.getType(), items, Map.of());
        }
        List<Long> questionIds = items.stream().map(WorksheetItem::getQuestionId).distinct().toList();
        return WorksheetUnitCounter.totalUnits(
                worksheet.getType(), items, problemAnswerUnitService.countByQuestionIds(questionIds));
    }

    /**
     * 학습지 여러 개의 진행률 분모. 종합평가는 문항 수 집계 한 번이면 되고, 일반·맞춤 학습만
     * 문항을 읽어 칸 수를 센다 — 배점형에만 필요한 조회를 전체에 걸지 않는다.
     */
    Map<Long, Integer> byWorksheetId(Map<Long, WorksheetType> typeByWorksheetId) {
        List<Long> assessmentWorksheetIds = worksheetIds(typeByWorksheetId, WorksheetType.COMPREHENSIVE_ASSESSMENT);
        List<Long> practiceWorksheetIds = worksheetIds(typeByWorksheetId, WorksheetType.GENERAL_LEARNING);

        Map<Long, Integer> totalUnitsByWorksheetId = new HashMap<>();
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

    private List<Long> worksheetIds(Map<Long, WorksheetType> typeByWorksheetId, WorksheetType type) {
        return typeByWorksheetId.entrySet().stream()
                .filter(entry -> entry.getValue() == type)
                .map(Map.Entry::getKey)
                .toList();
    }
}
