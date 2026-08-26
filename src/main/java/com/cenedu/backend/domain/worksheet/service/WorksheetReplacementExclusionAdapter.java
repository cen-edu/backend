package com.cenedu.backend.domain.worksheet.service;

import java.util.LinkedHashSet;
import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.edit.ReplacementExclusionPort;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습지 문항을 다시 수정 중인 Session이면 그 학습지의 모든 문항을 교체 후보에서 뺀다.
 *
 * <p>수정 중인 문항 자신도 포함해 제외한다 — 같은 문항이 다시 뽑히면 교사가 보기에 아무것도
 * 바뀌지 않은 것과 같다. 학습지와 무관한 생성 단계의 Session은 연결된 문항이 없으므로
 * 빈 집합을 반환하고, 문제은행 조회는 평소대로 동작한다.
 */
@Component
@RequiredArgsConstructor
public class WorksheetReplacementExclusionAdapter implements ReplacementExclusionPort {

    private final WorksheetItemRepository worksheetItemRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> excludedQuestionIds(long sessionId) {
        return worksheetItemRepository.findByEditingSessionId(sessionId)
                .map(item -> new LinkedHashSet<>(worksheetItemRepository
                        .findQuestionIdsByWorksheetId(item.getWorksheet().getId())))
                .map(ids -> (Set<Long>) ids)
                .orElseGet(Set::of);
    }
}
