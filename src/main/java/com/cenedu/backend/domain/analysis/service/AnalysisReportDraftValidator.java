package com.cenedu.backend.domain.analysis.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.analysis.report.AnalysisReportDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log =
            LoggerFactory.getLogger(AnalysisReportDraftValidator.class);

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
            warnIfNotKorean("itemMessages[" + item.worksheetItemId() + "].observation",
                    item.observation());
            warnIfNotKorean("itemMessages[" + item.worksheetItemId() + "].learningPoint",
                    item.learningPoint());
            warnIfNotKorean("itemMessages[" + item.worksheetItemId() + "].retryGuide",
                    item.retryGuide());
        }
        warnIfNotKorean("summaryMessage", draft.summaryMessage());
        warnIfNotKorean("overallObservation", draft.overallObservation());
        return draft.itemMessages();
    }

    private void requireText(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new AnalysisReportDraftInvalidException(errorCode);
        }
    }

    /**
     * 한국어가 아닌 글자가 섞였는지 보고 <b>기록만 남긴다</b>. 저장은 막지 않는다.
     *
     * <p>다국어 모델이 한국어를 쓰다 뜻이 같은 한자·가나 토큰으로 새는 일이 있다. 실측에서
     * "두세 문제" 가 "두세題" 로 나왔다. 눈에 거슬리지만 문장 전체는 멀쩡한데, 이걸로 보고서를
     * 실패시키면 <b>한 글자 때문에 교사가 아무것도 못 본다</b>. 손해가 더 크다.
     *
     * <p>대신 빈도를 남긴다. 프롬프트로 낮춰 보고, 그래도 반복되면 그때 거부로 올릴지 판단한다.
     *
     * <p>문장 자체는 남기지 않는다. 시험 문항과 학생 풀이가 로그로 새면 정답 유출 정책이
     * 무너진다(AGENTS.md 5절). 어느 필드에서 어떤 글자가 나왔는지만 적는다.
     */
    private void warnIfNotKorean(String field, String value) {
        if (value == null) {
            return;
        }
        Set<Character> foreign = new LinkedHashSet<>();
        value.chars().forEach(codePoint -> {
            if (isForeignScript(codePoint)) {
                foreign.add((char) codePoint);
            }
        });
        if (!foreign.isEmpty()) {
            log.warn("분석 문장에 한국어가 아닌 글자가 섞였습니다 — field={}, 글자={}",
                    field, foreign);
        }
    }

    /** 한자와 일본어 가나만 본다. 문장부호와 수식 기호는 정상적으로 쓰이므로 제외한다. */
    private boolean isForeignScript(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)     // 한중일 통합 한자
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF) // 한자 확장 A
                || (codePoint >= 0x3040 && codePoint <= 0x30FF) // 히라가나·가타카나
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF); // 한자 호환
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
