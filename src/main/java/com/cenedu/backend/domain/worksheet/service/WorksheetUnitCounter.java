package com.cenedu.backend.domain.worksheet.service;

import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/**
 * 진행률 분모. 종합평가는 문항 수, 일반·맞춤 학습은 채점 칸 수 합이다.
 *
 * <p>학생 화면과 교사 학습 현황이 같은 분모를 써야 한다 — 규칙을 복사하면 두 화면의 진행률이
 * 갈린다. {@code doneUnits}({@code progress_count})와 축이 같아야 한다.
 */
final class WorksheetUnitCounter {

    private WorksheetUnitCounter() {
    }

    static int totalUnits(WorksheetType type, List<WorksheetItem> items,
                          Map<Long, Long> answerUnitCountByQuestionId) {
        if (type == WorksheetType.COMPREHENSIVE_ASSESSMENT) {
            return items.size();
        }
        return items.stream()
                .mapToInt(item -> Math.toIntExact(
                        answerUnitCountByQuestionId.getOrDefault(item.getQuestionId(), 0L)))
                .sum();
    }
}
