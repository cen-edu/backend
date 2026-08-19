package com.cenedu.backend.infra.pdf;

/** PDF 렌더링 실패. HTML 이 XHTML 로 파싱되지 않거나 폰트를 읽지 못한 경우다. */
public class PdfRenderException extends RuntimeException {

    public PdfRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
