package com.cenedu.backend.ai.verification.adapter;

/**
 * Solver 응답이 요구한 JSON 형식이 아닐 때 던진다.
 *
 * <p>이 예외는 <b>FAIL 이 아니라 ERROR 로</b> 이어진다. 모델이 형식을 어긴 것과 문항이 틀린 것은
 * 다르다. 형식 위반을 FAIL 로 기록하면 조율측이 멀쩡한 문항을 재생성하고, 원인은 계속 남는다.
 */
public class SolverResponseParseException extends RuntimeException {

    public SolverResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public SolverResponseParseException(String message) {
        super(message);
    }
}
