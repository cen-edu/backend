package com.cenedu.backend.global.common;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반. {@link GlobalExceptionHandler} 가 {@link ErrorCode} 를 보고 응답을 만든다.
 *
 * <p>컨트롤러에서 직접 상태 코드를 만들지 말고 이 예외를 던진다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황에 맞는 메시지를 내려야 할 때 사용한다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
