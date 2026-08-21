package com.cenedu.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import com.openai.errors.OpenAIException;
import org.junit.jupiter.api.Test;

class OpenAiFailureClassifierTest {
    private final OpenAiFailureClassifier classifier = new OpenAiFailureClassifier();

    @Test
    void 일시적인_오류만_재시도한다() {
        assertThat(classifier.retryable(new OpenAIException("timeout"))).isTrue();
        assertThat(classifier.retryable(new OpenAIException("401 unauthorized"))).isFalse();
        assertThat(classifier.retryable(new IllegalStateException("timeout"))).isFalse();
    }
}
