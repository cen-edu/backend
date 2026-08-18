package com.cenedu.backend.domain.analysis.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.analysis.report.AnalysisReportDraft;
import org.springframework.stereotype.Component;

/**
 * 생성된 문장을 저장해도 되는지 확인한다.
 *
 * <p>DB 제약만으로는 부족하다. {@code NOT NULL} 은 공백 문자열을 통과시키고, 문항 FK 는 그 문항이
 * <b>이 학생의 채점 완료 문항인지</b>까지 보지 않는다. 모델이 미응답 문항까지 문장을 만들어 보내는
 * 일이 실제로 있어서, 저장 전에 허용된 문항 집합과 대조한다.
 */
@Component
public class AnalysisReportDraftValidator {

    /** 검증에 실패한 이유. 보고서의 last_error_code 에 그대로 남는다. */
    public static final String EMPTY_SUMMARY = "EMPTY_SUMMARY";
    public static final String EMPTY_OBSERVATION = "EMPTY_OBSERVATION";
    public static final String EMPTY_ITEM_MESSAGE = "EMPTY_ITEM_MESSAGE";
    public static final String UNKNOWN_ITEM = "UNKNOWN_ITEM";
    public static final String DUPLICATE_ITEM = "DUPLICATE_ITEM";

    /**
     * 저장 가능한 문항 문장만 남겨 돌려준다.
     *
     * @param allowedWorksheetItemIds 채점이 끝나 문장을 만들어도 되는 문항
     * @throws AnalysisReportDraftInvalidException 요약이 비었거나 허용되지 않은 문항이 섞였을 때
     */
    public List<AnalysisReportDraft.ItemMessageDraft> validate(
            AnalysisReportDraft draft,
            List<Long> allowedWorksheetItemIds
    ) {
        requireText(draft.summaryMessage(), EMPTY_SUMMARY);
        requireText(draft.overallObservation(), EMPTY_OBSERVATION);

        Set<Long> allowed = Set.copyOf(allowedWorksheetItemIds);
        Set<Long> seen = new LinkedHashSet<>();
        for (AnalysisReportDraft.ItemMessageDraft item : draft.itemMessages()) {
            if (item.worksheetItemId() == null || !allowed.contains(item.worksheetItemId())) {
                throw new AnalysisReportDraftInvalidException(UNKNOWN_ITEM);
            }
            if (!seen.add(item.worksheetItemId())) {
                throw new AnalysisReportDraftInvalidException(DUPLICATE_ITEM);
            }
            requireText(item.observation(), EMPTY_ITEM_MESSAGE);
            requireText(item.learningPoint(), EMPTY_ITEM_MESSAGE);
            requireText(item.retryGuide(), EMPTY_ITEM_MESSAGE);
        }
        return draft.itemMessages();
    }

    private void requireText(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new AnalysisReportDraftInvalidException(errorCode);
        }
    }

    /** 생성된 문장이 저장 조건을 못 맞췄을 때. 보고서를 생성 실패로 되돌리는 신호다. */
    public static class AnalysisReportDraftInvalidException extends RuntimeException {

        private final String errorCode;

        public AnalysisReportDraftInvalidException(String errorCode) {
            super("AI 분석 문장 검증 실패: " + errorCode);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
