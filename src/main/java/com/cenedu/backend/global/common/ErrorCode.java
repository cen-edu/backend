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

    // ===== auth (이동규) =====
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "로그인 아이디 또는 비밀번호가 올바르지 않습니다."),

    // ===== member (이동규) =====
    MEMBER_LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일 입니다."),
    MEMBER_TEACHER_NOT_FOUND(HttpStatus.NOT_FOUND, "교사 계정을 찾을 수 없습니다."),
    MEMBER_TEACHER_REQUIRED(HttpStatus.FORBIDDEN, "교사 계정만 학생을 생성할 수 있습니다."),
    MEMBER_STUDENT_LOGIN_ID_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "학생 로그인 아이디를 생성하지 못했습니다."),

    // ===== worksheet (배세빈) =====

    // ===== submission (배세빈) =====

    // ===== grading (배세빈) =====

    // ===== chat (배세빈) =====

    // ===== curriculum (이하영) =====

    // ===== problem (이하영) =====

    // ===== analysis (모수환) =====
    ASSESSMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "평가를 찾을 수 없습니다."),
    STUDENT_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생 풀이를 찾을 수 없습니다."),
    ASSESSMENT_NOT_COMPLETED(HttpStatus.CONFLICT, "평가를 마친 뒤에 정답을 확인할 수 있습니다."),
    ASSESSMENT_HEADER_CONFLICT(HttpStatus.CONFLICT, "같은 평가 ID의 기본 정보가 서로 다릅니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다."),
    REPORT_RENDERER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "보고서를 PDF로 만들 수 없습니다. 서버에 브라우저가 설치되어 있지 않습니다."),
    REPORT_RENDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "보고서를 만들지 못했습니다."),

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
