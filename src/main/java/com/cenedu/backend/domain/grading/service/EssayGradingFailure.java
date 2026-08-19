package com.cenedu.backend.domain.grading.service;

/**
 * 서술형 채점이 점수를 내지 못했을 때 {@code failure_reason} 앞에 붙이는 코드.
 *
 * <p><b>교사가 "다시 돌려보세요" 와 "직접 채점하세요" 를 갈라야 한다.</b> 사유를 문장으로만
 * 남기면 그 구분이 사람 읽기에만 있고 화면은 두 경우를 똑같이 그린다. 코드를 앞에 붙여 두면
 * 나중에 화면이 분기하고 싶어질 때 파싱만 붙이면 된다.
 * ({@code ai/verification} 의 {@code EvidencePrefix} 와 같은 방식이다.)
 *
 * <p><b>리터럴로 흩뿌리지 않는다.</b> 접두어는 조율측·화면이 파싱할 값이라 오타가 곧 계약 위반인데,
 * 문자열을 호출부마다 적어 두면 오타가 컴파일도 테스트도 통과한다.
 *
 * <p><b>{@code failure_reason} 은 100자다.</b> 넘치면 DB 제약 위반으로 500이 난다. 자르는 것을
 * 호출부마다 두면 한 곳만 빠뜨려도 그 경로에서만 터지므로 {@link #reason} 이 직접 자른다 —
 * 접두어는 앞에 있어 잘리지 않는다.
 */
public enum EssayGradingFailure {

    // ── 다시 돌리면 달라질 수 있다 (일시) ────────────────────────────────
    /** 턴 상한에 닿을 때까지 판정이 다 붙지 않았다. 억지로 판정을 내지 않는다. */
    TURN_LIMIT(true),
    /** 모델이 약속한 JSON 을 내지 않았다. */
    MALFORMED(true),
    /** LLM 호출 자체가 실패했다(타임아웃·5xx). 판정이 없으므로 D17 을 깨지 않는다. */
    LLM_ERROR(true),

    // ── 다시 돌려도 같다 (구조) ──────────────────────────────────────────
    /**
     * 판정 불가 항목이 하나라도 있다(D14). 필기를 읽을 수 없다는 뜻이라 모델을 한 번 더 불러도
     * 같은 그림을 본다 — 사람이 봐야 한다.
     */
    UNJUDGEABLE(false),
    /** 그 문항에 채점 기준이 없거나 가중치 합이 0 이다. 문제은행 ESSAY 303건이 여기로 온다. */
    NO_RUBRIC(false),
    /** 이미지도 텍스트도 없다(D20). 채점할 것이 없다. */
    NO_ANSWER(false),
    /** 텍스트는 있는데 필기 이미지가 없다. 채점 입력이 이미지라(D1) 투입할 것이 없다. */
    NO_IMAGE(false),
    /** 이미지 저장소가 꺼져 있어 URL 을 발급할 수 없다. 설정 문제라 재실행으로 풀리지 않는다. */
    IMAGE_STORE_UNAVAILABLE(false);

    /** {@code failure_reason} 컬럼 길이. */
    private static final int MAX_LENGTH = 100;

    private final boolean retryable;

    EssayGradingFailure(boolean retryable) {
        this.retryable = retryable;
    }

    /** 다시 돌려볼 값이 있는 실패인가. 화면이 두 안내를 가르는 기준이다. */
    public boolean isRetryable() {
        return retryable;
    }

    /** {@code 코드: 내용} 으로 조립하고 컬럼 길이에 맞춰 자른다. 내용이 없으면 코드만 남긴다. */
    public String reason(String detail) {
        if (detail == null || detail.isBlank()) {
            return name();
        }
        String reason = name() + ": " + detail;
        return reason.length() <= MAX_LENGTH ? reason : reason.substring(0, MAX_LENGTH);
    }
}
