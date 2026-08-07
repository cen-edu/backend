package com.cenedu.backend.global.common;

/**
 * 모든 API 응답의 공통 포맷.
 *
 * <pre>
 * { "success": true,  "data": { }, "error": null }
 * { "success": false, "data": null, "error": { "code": "STUDENT_NOT_FOUND", "message": "..." } }
 * </pre>
 *
 * <p>{@code null} 필드를 JSON 에서 제외하지 않는다. 응답 모양을 항상 고정해야
 * 프론트가 {@code error === null} 한 가지만 처리하면 된다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 내려줄 데이터가 없는 성공 응답.
     *
     * <p>record 가 만드는 접근자 {@code success()} 와 시그니처가 겹쳐 이름을 달리 둔다.
     */
    public static ApiResponse<Void> successEmpty() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), message));
    }

    public record ErrorBody(String code, String message) {
    }
}
