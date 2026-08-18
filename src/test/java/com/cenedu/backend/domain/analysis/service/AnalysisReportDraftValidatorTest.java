package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.cenedu.backend.domain.analysis.report.AnalysisReportDraft;
import com.cenedu.backend.domain.analysis.service.AnalysisReportDraftValidator
        .AnalysisReportDraftInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisReportDraftValidatorTest {

    private final AnalysisReportDraftValidator validator = new AnalysisReportDraftValidator();

    @Test
    @DisplayName("채점 완료 문항의 문장만 있으면 통과한다")
    void acceptsDraftWithAllowedItems() {
        AnalysisReportDraft draft = draft(
                "요약", "관찰", List.of(item(11L), item(12L)));

        List<AnalysisReportDraft.ItemMessageDraft> validated =
                validator.validate(draft, List.of(11L, 12L));

        assertThat(validated).hasSize(2);
    }

    @Test
    @DisplayName("채점되지 않은 문항의 문장이 섞이면 거부한다")
    void rejectsUnknownItem() {
        AnalysisReportDraft draft = draft("요약", "관찰", List.of(item(99L)));

        assertThatThrownBy(() -> validator.validate(draft, List.of(11L)))
                .isInstanceOf(AnalysisReportDraftInvalidException.class)
                .extracting(error ->
                        ((AnalysisReportDraftInvalidException) error).getErrorCode())
                .isEqualTo(AnalysisReportDraftValidator.UNKNOWN_ITEM);
    }

    @Test
    @DisplayName("같은 문항이 두 번 오면 거부한다")
    void rejectsDuplicateItem() {
        AnalysisReportDraft draft = draft("요약", "관찰", List.of(item(11L), item(11L)));

        assertThatThrownBy(() -> validator.validate(draft, List.of(11L)))
                .isInstanceOf(AnalysisReportDraftInvalidException.class)
                .extracting(error ->
                        ((AnalysisReportDraftInvalidException) error).getErrorCode())
                .isEqualTo(AnalysisReportDraftValidator.DUPLICATE_ITEM);
    }

    @Test
    @DisplayName("공백 문자열은 NOT NULL 을 통과하므로 검증에서 막는다")
    void rejectsBlankText() {
        AnalysisReportDraft blankSummary = draft("   ", "관찰", List.of());
        AnalysisReportDraft blankItem = draft("요약", "관찰", List.of(
                new AnalysisReportDraft.ItemMessageDraft(11L, "확인", "", "안내")));

        assertThatThrownBy(() -> validator.validate(blankSummary, List.of()))
                .isInstanceOf(AnalysisReportDraftInvalidException.class);
        assertThatThrownBy(() -> validator.validate(blankItem, List.of(11L)))
                .isInstanceOf(AnalysisReportDraftInvalidException.class)
                .extracting(error ->
                        ((AnalysisReportDraftInvalidException) error).getErrorCode())
                .isEqualTo(AnalysisReportDraftValidator.EMPTY_ITEM_MESSAGE);
    }

    @Test
    @DisplayName("채점 완료 문항이 없으면 문항 문장 없이도 통과한다")
    void acceptsEmptyItemMessages() {
        AnalysisReportDraft draft = draft("요약", "관찰", List.of());

        assertThat(validator.validate(draft, List.of())).isEmpty();
    }

    private AnalysisReportDraft draft(
            String summary,
            String observation,
            List<AnalysisReportDraft.ItemMessageDraft> items
    ) {
        return new AnalysisReportDraft(summary, observation, items, "v1", "model", (short) 1);
    }

    private AnalysisReportDraft.ItemMessageDraft item(long worksheetItemId) {
        return new AnalysisReportDraft.ItemMessageDraft(
                worksheetItemId, "확인된 점", "학습 포인트", "다시 풀 때");
    }
}
