package com.cenedu.backend.domain.analysis.report;

/**
 * 문장 생성이 실패했을 때 구현체가 던지는 예외. Port 계약의 일부다.
 *
 * <p>도메인 패키지에 두는 이유는 <b>호출부가 실패 원인을 구분할 수 있어야</b> 하기 때문이다.
 * 예외가 어댑터 패키지에 있으면 도메인이 그것을 잡으려고 어댑터를 참조하게 되고, Port 로 갈라놓은
 * 의미가 사라진다. 그래서 원인 코드까지 계약에 포함한다.
 *
 * <p>{@link #errorCode} 는 보고서의 {@code last_error_code} 에 그대로 저장된다. 형식 오류인지
 * 호출 실패인지 구분되지 않으면, 실패가 쌓였을 때 프롬프트를 고쳐야 하는지 모델 설정을 고쳐야
 * 하는지 알 수 없다.
 */
public class AnalysisReportGenerationException extends RuntimeException {

    /** 응답을 계약대로 읽지 못했다. 프롬프트나 출력 상한을 살펴야 한다. */
    public static final String PARSE_ERROR = "PARSE_ERROR";

    /** 모델 호출 자체가 실패했다. 키·타임아웃·레이트리밋을 살펴야 한다. */
    public static final String LLM_CALL_FAILED = "LLM_CALL_FAILED";

    private final String errorCode;

    public AnalysisReportGenerationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AnalysisReportGenerationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
