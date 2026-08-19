package com.cenedu.backend.domain.grading.port;

import java.util.List;

/**
 * 서술형 채점 한 칸의 결과.
 *
 * <p><b>점수가 없다.</b> 점수는 가중치를 아는 도메인이 {@code BigDecimal} 로 계산한다(D16).
 * 모델은 판정만 한다.
 *
 * @param transcription 모델이 이미지에서 읽어낸 글. 실패해도 읽은 데까지는 남는다
 * @param judgements    요청한 기준 항목에 대한 판정. {@code JUDGED} 가 아니면 불완전할 수 있다
 */
public record EssayGradingResult(EssayGradingStatus status, String transcription,
                                 List<RubricJudgement> judgements) {

    public static EssayGradingResult judged(String transcription, List<RubricJudgement> judgements) {
        return new EssayGradingResult(EssayGradingStatus.JUDGED, transcription, List.copyOf(judgements));
    }

    /** 판정이 다 붙지 않았다. 읽어낸 글과 붙은 판정까지는 그대로 넘긴다 — 실패 원인 귀속에 쓴다. */
    public static EssayGradingResult incomplete(EssayGradingStatus status, String transcription,
                                                List<RubricJudgement> judgements) {
        return new EssayGradingResult(status, transcription, List.copyOf(judgements));
    }

    public boolean isJudged() {
        return status == EssayGradingStatus.JUDGED;
    }
}
