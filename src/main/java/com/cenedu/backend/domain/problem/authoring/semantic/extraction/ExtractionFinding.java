package com.cenedu.backend.domain.problem.authoring.semantic.extraction;

/**
 * 추출 실패 원인을 problem_question.semantic_extraction_findings에 남길 한 줄로 만든다.
 *
 * <p>예전에는 어느 단계에서 실패해도 고정 문구 하나로 덮어써서, 실패가 수천 건 쌓여도
 * placeholder 누락인지 정답 불일치인지 지원하지 않는 구성인지 구분할 수 없었다. 단계 이름과
 * 예외 종류·메시지를 함께 남겨 원인을 좁힐 수 있게 한다.
 *
 * <p>메시지는 그대로 두면 모델 본문이나 긴 검증 목록이 통째로 들어올 수 있어 길이를 제한한다.
 */
public final class ExtractionFinding {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private ExtractionFinding() {
    }

    public static String of(String stage, Throwable cause) {
        if (cause == null) {
            return stage + ": 원인을 알 수 없습니다.";
        }
        return stage + " 실패 [" + cause.getClass().getSimpleName() + "]: " + truncate(cause.getMessage());
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "(메시지 없음)";
        }
        String single = message.replaceAll("\\s+", " ").trim();
        return single.length() <= MAX_MESSAGE_LENGTH
                ? single : single.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }
}
