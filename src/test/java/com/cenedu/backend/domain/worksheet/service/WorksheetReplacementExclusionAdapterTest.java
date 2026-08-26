package com.cenedu.backend.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;

/**
 * 학습지 문항 재수정 시 같은 학습지의 문항이 교체 후보에서 빠지는지 고정한다.
 *
 * <p>빼지 않으면 문제은행 조회가 이미 학습지에 있는 문항을 골라 와서, 교사가 확인을 누른
 * 뒤에야 문항 중복으로 실패한다.
 */
class WorksheetReplacementExclusionAdapterTest {

    @Test
    void 수정_중인_학습지의_모든_문항을_후보에서_뺀다() {
        var repository = mock(WorksheetItemRepository.class);
        var worksheet = mock(Worksheet.class);
        when(worksheet.getId()).thenReturn(5L);
        var item = mock(WorksheetItem.class);
        when(item.getWorksheet()).thenReturn(worksheet);
        when(repository.findByEditingSessionId(11L)).thenReturn(Optional.of(item));
        when(repository.findQuestionIdsByWorksheetId(5L)).thenReturn(List.of(100L, 101L, 102L));

        var excluded = new WorksheetReplacementExclusionAdapter(repository).excludedQuestionIds(11L);

        assertThat(excluded).containsExactlyInAnyOrder(100L, 101L, 102L);
    }

    @Test
    void 학습지와_무관한_생성_세션은_제외_대상이_없다() {
        var repository = mock(WorksheetItemRepository.class);
        when(repository.findByEditingSessionId(anyLong())).thenReturn(Optional.empty());

        var excluded = new WorksheetReplacementExclusionAdapter(repository).excludedQuestionIds(11L);

        assertThat(excluded).isEmpty();
        verify(repository, never()).findQuestionIdsByWorksheetId(anyLong());
    }
}
