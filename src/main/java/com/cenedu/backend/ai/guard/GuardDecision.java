package com.cenedu.backend.ai.guard;

/**
 * 가드레일 판정 결과.
 *
 * @param blocked    막을지 여부
 * @param reasonCode 막은 이유의 기계 판독용 코드. 통과면 {@code null}. 프론트가 이 값으로 안내 문구를 고른다
 * @param message    로그·응답에 남길 설명. 통과면 {@code null}
 */
public record GuardDecision(boolean blocked, String reasonCode, String message) {

    private static final GuardDecision ALLOWED = new GuardDecision(false, null, null);

    public static GuardDecision allow() {
        return ALLOWED;
    }

    public static GuardDecision block(String reasonCode, String message) {
        return new GuardDecision(true, reasonCode, message);
    }
}
