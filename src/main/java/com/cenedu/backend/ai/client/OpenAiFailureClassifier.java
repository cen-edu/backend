package com.cenedu.backend.ai.client;

import com.openai.errors.OpenAIException;
import org.springframework.stereotype.Component;

/** 공급자 오류 중 일시적인 오류만 재시도 대상으로 분류한다. */
@Component
public class OpenAiFailureClassifier {
    public boolean retryable(Throwable error) {
        if (!(error instanceof OpenAIException)) return false;
        String message = String.valueOf(error.getMessage()).toLowerCase();
        return message.contains("timeout") || message.contains("timed out")
                || message.contains("connection") || message.contains("temporar")
                || message.contains("rate limit") || message.contains("429")
                || message.contains("500") || message.contains("502")
                || message.contains("503") || message.contains("504");
    }
}
