package com.cenedu.backend.global.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드. 응답의 {@code error.code} 로 그대로 나간다.
 *
 * <p>도메인 코드는 아래 자기 도메인 블록 <b>끝에</b> 추가한다. 블록을 지켜야 머지 충돌이 줄어든다.
 * 이름은 {@code {대상}_{사유}} 형식으로 짓는다 (예: {@code STUDENT_NOT_FOUND}).
 */
@Getter
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "입력값의 타입이 올바르지 않습니다."),
    MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ===== auth (배세빈) =====

    // ===== member (배세빈) =====

    // ===== worksheet (배세빈) =====

    // ===== submission (배세빈) =====

    // ===== grading (배세빈) =====

    // ===== chat (배세빈) =====

    // ===== curriculum (이하영) =====

    // ===== problem (이하영) =====

    // ===== analysis (모수환) =====

    // ===== dashboard (모수환) =====

    // ===== ai (이동규) =====
    AI_AGENT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리할 수 있는 에이전트가 없습니다."),
    AI_REQUEST_BLOCKED(HttpStatus.BAD_REQUEST, "이 요청은 처리할 수 없습니다."),
    AI_RESPONSE_BLOCKED(HttpStatus.INTERNAL_SERVER_ERROR, "답변을 생성하지 못했습니다. 다시 시도해 주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
