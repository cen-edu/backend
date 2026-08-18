package com.cenedu.backend.ai.verification.adapter;

/**
 * {@code evidence} 첫머리에 붙이는 대문자 접두어.
 *
 * <p>계약의 CheckType 은 10종뿐인데 한 CheckType 이 여러 성격의 결함을 담는다. 접두어로 가르면
 * 조율측이 나중에 성격별로 분기하고 싶어질 때 파싱만 붙이면 되고, 그때까지 계약 enum 을 늘리지
 * 않아도 된다.
 *
 * <p><b>리터럴로 흩뿌리지 않는다.</b> 접두어는 조율측이 파싱할 값이므로 오타가 곧 계약 위반인데,
 * 문자열을 각 판정기에 적어 두면 오타가 컴파일도 테스트도 통과한다.
 */
final class EvidencePrefix {

    // ── ANSWER_CONSISTENCY ────────────────────────────────────────────────
    /** Validator 위임 결과. 구조 불변식 위반이다. */
    static final String STRUCTURE = "STRUCTURE";
    /** {@code expectation.expectedQuestionType} 와 실제 유형이 다르다. */
    static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    /** 해설이 정답과 모순되거나 스냅샷에 없는 값을 쓴다. */
    static final String EXPLANATION = "EXPLANATION";
    /** learningGuide 가 정답이나 직접 힌트를 노출한다. */
    static final String LEAKAGE = "LEAKAGE";

    // ── RUBRIC_QUALITY ────────────────────────────────────────────────────
    static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";
    static final String UNCOVERED = "UNCOVERED";
    static final String OVERLAPPING = "OVERLAPPING";
    static final String UNJUDGEABLE = "UNJUDGEABLE";

    // ── ASSET_CONSISTENCY ─────────────────────────────────────────────────
    /** 자산 준비 상태. */
    static final String MANIFEST = "MANIFEST";
    /** altText 내용. 하위 구분({@code LEAK} · {@code MISMATCH})은 뒤에 한 겹 더 붙는다. */
    static final String ALTTEXT = "ALTTEXT";

    /** {@code 접두어: 내용} 으로 조립한다. */
    static String of(String prefix, String detail) {
        return prefix + ": " + (detail == null ? "" : detail);
    }

    /**
     * 두 단계 접두어. {@code ALTTEXT: LEAK — 내용} 형태다.
     *
     * <p>조율측이 자산 준비 문제와 altText 문제를 먼저 가르고, 그다음 유출·불일치를 가른다.
     */
    static String of(String prefix, String subKind, String detail) {
        return prefix + ": " + subKind + " — " + (detail == null ? "" : detail);
    }

    private EvidencePrefix() {
    }
}
