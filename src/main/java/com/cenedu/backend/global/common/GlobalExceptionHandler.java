package com.cenedu.backend.global.common;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 {@link ApiResponse} 포맷으로 변환하는 단일 지점.
 *
 * <p>각자 예외 처리 방식을 만들지 않는다. 새 에러는 {@link ErrorCode} 에 추가한다.
 *
 * <p><b>주의:</b> Spring Security 가 필터 체인에서 던지는 인증·인가 예외는 컨트롤러 진입 전에
 * 발생하므로 여기까지 오지 않는다. 인증 실패 응답도 같은 포맷으로 내리려면
 * {@code AuthenticationEntryPoint} 와 {@code AccessDeniedHandler} 를 별도로 등록해야 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {} - {}", e.getErrorCode().name(), e.getMessage());
        return toResponse(e.getErrorCode(), e.getMessage());
    }

    /** {@code @Valid} 검증 실패. 필드 오류는 message 에 합쳐 응답 모양을 고정한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return toResponse(ErrorCode.INVALID_INPUT_VALUE,
                message.isBlank() ? ErrorCode.INVALID_INPUT_VALUE.getMessage() : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return toResponse(ErrorCode.MALFORMED_REQUEST_BODY);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Type mismatch: {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_TYPE_VALUE, e.getName() + " 값의 타입이 올바르지 않습니다.");
    }

    /** multipart 요청이 서버의 이미지 크기 제한을 넘은 경우. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e) {
        log.warn("Upload size limit exceeded: {}", e.getMessage());
        return toResponse(ErrorCode.IMAGE_TOO_LARGE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 매핑되지 않은 경로. 이 핸들러가 없으면 아래 {@code Exception} 핸들러가 잡아 500 이 나간다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("No resource found: {}", e.getResourcePath());
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /** 예상하지 못한 예외. 원인은 로그에만 남기고 응답에는 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        return toResponse(errorCode, errorCode.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, message));
    }
}
