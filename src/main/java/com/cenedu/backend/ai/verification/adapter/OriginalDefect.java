package com.cenedu.backend.ai.verification.adapter;

/**
 * 원본 검사가 찾은 결함 하나. 파싱된 형태다.
 *
 * <p>{@code kind} 는 모델이 <b>사실을 분류한</b> 값이고, 심각도는 그 분류를 받아
 * {@link ContentIntegrityChecker} 가 정한다. 심각도는 우리 정책이라 모델에 맡기지 않는다 —
 * 모델이 심각도를 정하면 같은 결함이 호출마다 다른 무게로 조율측에 나간다.
 *
 * @param type     STRUCTURE · LEAKAGE · EXPLANATION · RUBRIC
 * @param kind     LEAKAGE 는 ANSWER_VALUE · SOLUTION_DIRECTION, RUBRIC 은 4축. 나머지는 빈 문자열
 * @param location 필드 경로·인덱스. <b>정답 값이 아니다</b>
 * @param detail   한 줄 설명
 */
public record OriginalDefect(String type, String kind, String location, String detail) {

    static final String TYPE_STRUCTURE = "STRUCTURE";
    static final String TYPE_LEAKAGE = "LEAKAGE";
    static final String TYPE_EXPLANATION = "EXPLANATION";
    static final String TYPE_RUBRIC = "RUBRIC";

    /** learningGuide 가 정답 값을 그대로 담은 경우. */
    static final String KIND_ANSWER_VALUE = "ANSWER_VALUE";
    /** learningGuide 가 풀이 방향을 지정한 경우. 정답 자체는 아니다. */
    static final String KIND_SOLUTION_DIRECTION = "SOLUTION_DIRECTION";

    /** 근거 문자열. 위치를 앞에 두어 조율측이 어디를 볼지 바로 알 수 있게 한다. */
    String describe() {
        if (location == null || location.isBlank()) {
            return detail == null ? "" : detail;
        }
        return location + " — " + (detail == null ? "" : detail);
    }
}
