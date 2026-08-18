package com.cenedu.backend.ai.analysis.adapter;

/** 생성 응답을 계약대로 읽지 못했을 때. 보고서를 생성 실패로 되돌리는 신호다. */
public class AnalysisReportResponseParseException extends RuntimeException {

    public AnalysisReportResponseParseException(String message) {
        super(message);
    }

    public AnalysisReportResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
