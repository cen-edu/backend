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

    // ===== storage (모수환) =====
    IMAGE_FILE_REQUIRED(HttpStatus.BAD_REQUEST, "이미지 파일은 필수입니다."),
    IMAGE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "PNG 또는 JPEG 이미지만 업로드할 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "이미지는 5MB 이하만 업로드할 수 있습니다."),
    IMAGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 이미지에 접근할 권한이 없습니다."),
    IMAGE_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."),
    IMAGE_ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생의 학습지 수행 기록을 찾을 수 없습니다."),
    IMAGE_ANSWER_UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "답안 칸을 찾을 수 없습니다."),
    IMAGE_ANSWER_UNIT_NOT_ASSIGNED(HttpStatus.BAD_REQUEST, "배정된 학습지에 포함되지 않은 답안 칸입니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 이미지를 찾을 수 없습니다."),
    IMAGE_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소 설정이 필요합니다."),
    IMAGE_STORAGE_FAILED(HttpStatus.BAD_GATEWAY, "이미지 저장소 요청을 처리하지 못했습니다."),

    // ===== auth (이동규) =====
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "로그인 아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    AUTH_PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "새 비밀번호 확인이 일치하지 않습니다."),

    // ===== member (이동규) =====
    MEMBER_LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일 입니다."),
    MEMBER_TEACHER_NOT_FOUND(HttpStatus.NOT_FOUND, "교사 계정을 찾을 수 없습니다."),
    MEMBER_TEACHER_REQUIRED(HttpStatus.FORBIDDEN, "교사 계정만 수행할 수 있습니다."),
    MEMBER_STUDENT_LOGIN_ID_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "학생 로그인 아이디를 생성하지 못했습니다."),
    MEMBER_STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생 계정을 찾을 수 없습니다."),
    MEMBER_STUDENT_REQUIRED(HttpStatus.BAD_REQUEST, "학생 계정만 반에 배정할 수 있습니다."),
    MEMBER_STUDENT_NOT_OWNED(HttpStatus.FORBIDDEN, "담당하는 학생만 반에 배정할 수 있습니다."),
    MEMBER_SCHOOL_CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "반을 찾을 수 없습니다."),
    MEMBER_SCHOOL_CLASS_NOT_OWNED(HttpStatus.FORBIDDEN, "담당하는 반에만 접근할 수 있습니다."),
    MEMBER_CLASS_GRADE_MISMATCH(HttpStatus.BAD_REQUEST, "반과 학생의 학년이 일치하지 않습니다."),
    MEMBER_SCHOOL_CLASS_ORDER_INVALID(
            HttpStatus.BAD_REQUEST, "현재 교사가 소유한 전체 활성 반의 순서를 전달해야 합니다."),
    MEMBER_CLASS_STUDENT_IDS_DUPLICATED(
            HttpStatus.BAD_REQUEST, "중복된 학생 ID를 반에 배정할 수 없습니다."),
    MEMBER_SCHOOL_CLASS_IDS_DUPLICATED(
            HttpStatus.BAD_REQUEST, "중복된 반 ID를 삭제할 수 없습니다."),

    // ===== worksheet (배세빈) =====
    WORKSHEET_NOT_FOUND(HttpStatus.NOT_FOUND, "학습지를 찾을 수 없습니다."),
    WORKSHEET_QUESTION_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 문항이 포함되어 있습니다."),
    WORKSHEET_QUESTION_DUPLICATED(HttpStatus.BAD_REQUEST, "같은 문항이 두 번 이상 포함되어 있습니다."),
    WORKSHEET_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "일반 학습에는 빈칸형 문항만 포함할 수 있습니다."),
    WORKSHEET_SPEC_MISMATCH(HttpStatus.BAD_REQUEST, "출제 조건의 문항 수 합계가 실제 문항 수와 다릅니다."),
    WORKSHEET_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "배포된 학습지는 삭제할 수 없습니다."),
    WORKSHEET_DUPLICATE_ASSIGNMENT(HttpStatus.CONFLICT, "이미 같은 반에 배포된 학습지입니다."),
    WORKSHEET_CLASS_NOT_OWNED(HttpStatus.NOT_FOUND, "담당하는 반을 찾을 수 없습니다."),
    WORKSHEET_DUE_IN_PAST(HttpStatus.BAD_REQUEST, "제출 기한은 현재 시각 이후여야 합니다."),
    WORKSHEET_ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "배정된 학습지를 찾을 수 없습니다."),
    WORKSHEET_RESULT_NOT_RELEASED(HttpStatus.CONFLICT, "아직 채점 결과가 공개되지 않았습니다."),

    // ===== submission (배세빈) =====
    SUBMISSION_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출한 학습지입니다."),
    SUBMISSION_DUE_PASSED(HttpStatus.CONFLICT, "제출 기한이 지났습니다."),
    SUBMISSION_ANSWER_UNIT_MISMATCH(HttpStatus.BAD_REQUEST, "이 문항의 채점 칸이 아닙니다."),

    // ===== grading (배세빈) =====

    // ===== chat (배세빈) =====
    CHAT_QUESTION_BLANK(HttpStatus.BAD_REQUEST, "질문을 입력해 주세요."),
    CHAT_QUESTION_TOO_LONG(HttpStatus.BAD_REQUEST, "질문이 너무 깁니다."),
    CHAT_HISTORY_INVALID(HttpStatus.BAD_REQUEST, "이전 대화 형식이 올바르지 않습니다."),

    // ===== curriculum (이하영) =====

    // ===== problem (이하영) =====
    QUESTION_INVENTORY_INSUFFICIENT(HttpStatus.BAD_REQUEST, "요청한 조건의 문항 수가 부족합니다."),
    ASSESSMENT_QUESTION_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "종합평가에서는 객관식, 단답형, 서술형 문항만 사용할 수 있습니다."),
    PROBLEM_DETAIL_DATA_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "문항 상세 데이터를 해석할 수 없습니다."),
    CURRICULUM_SUB_UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "소단원을 찾을 수 없습니다."),
    // ===== analysis (모수환) =====
    ASSESSMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "평가를 찾을 수 없습니다."),
    STUDENT_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생 풀이를 찾을 수 없습니다."),
    ASSESSMENT_NOT_COMPLETED(HttpStatus.CONFLICT, "평가를 마친 뒤에 정답을 확인할 수 있습니다."),
    ASSESSMENT_HEADER_CONFLICT(HttpStatus.CONFLICT, "같은 평가 ID의 기본 정보가 서로 다릅니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다."),
    REPORT_RENDERER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "보고서를 PDF로 만들 수 없습니다. 서버에 브라우저가 설치되어 있지 않습니다."),
    REPORT_RENDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "보고서를 만들지 못했습니다."),
    ANALYSIS_CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석할 반을 찾을 수 없습니다."),
    ANALYSIS_CLASS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "담당하는 반의 분석만 조회할 수 있습니다."),
    ANALYSIS_ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "분석할 학습지 배정을 찾을 수 없습니다."),
    ANALYSIS_ASSIGNMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "담당하는 학습지의 분석만 조회할 수 있습니다."),
    ANALYSIS_ASSIGNMENT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "종합평가 학습지만 조회할 수 있습니다."),
    ANALYSIS_STUDENT_NOT_ASSIGNED(HttpStatus.NOT_FOUND, "해당 학습지를 배정받은 학생을 찾을 수 없습니다."),

    // ===== dashboard (모수환) =====
    DASHBOARD_CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "대시보드에서 조회할 반을 찾을 수 없습니다."),
    DASHBOARD_CLASS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "담당하는 반의 대시보드만 조회할 수 있습니다."),

    // ===== ai (이동규) =====
    AI_AGENT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리할 수 있는 에이전트가 없습니다."),
    AI_REQUEST_BLOCKED(HttpStatus.BAD_REQUEST, "이 요청은 처리할 수 없습니다."),
    AI_RESPONSE_BLOCKED(HttpStatus.INTERNAL_SERVER_ERROR, "답변을 생성하지 못했습니다. 다시 시도해 주세요."),

    // ===== ai/client (배세빈) =====
    // 두 코드를 나눈 이유: 호출 자체가 실패한 것과, 호출은 됐는데 텍스트가 비어 돌아온 것은
    // 원인도 대응도 다르다. 후자는 max-completion-tokens 를 의심해야 한다.
    // 사용자에게 나가는 문구는 같다. 원인은 로그와 예외 메시지에만 남긴다.
    AI_CLIENT_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "답변을 생성하지 못했습니다. 다시 시도해 주세요."),
    AI_CLIENT_EMPTY_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "답변을 생성하지 못했습니다. 다시 시도해 주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
