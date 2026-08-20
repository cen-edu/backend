package com.cenedu.backend.domain.grading.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerNormalizerTest {
    private final AnswerNormalizer normalizer = new AnswerNormalizer();

    @Test
    void 데이터셋의_유니코드_수식과_latex_표기를_같은_비교값으로_정규화한다() {
        assertThat(normalizer.normalize("$2²×3³÷6$", null)).isEqualTo("2^2*3^3/6");
        assertThat(normalizer.normalize("2^{2}\\times 3^{3}\\div 6", null)).isEqualTo("2^2*3^3/6");
        assertThat(normalizer.normalize("−12", null)).isEqualTo("-12");
        assertThat(normalizer.normalize("∠D", null)).isEqualTo("D");
    }

    @Test
    void 표시용_수식_구분자는_비교값에서_제거한다() {
        assertThat(normalizer.normalize("\\(\\frac{40}{3}\\)", null)).isEqualTo("40/3");
        assertThat(normalizer.normalize("$$\\frac{40}{3}$$", null)).isEqualTo("40/3");
    }

    @Test
    void 정규화는_멱등이다() {
        String normalized = normalizer.normalize("$2²×3³$", null);
        assertThat(normalizer.normalize(normalized, null)).isEqualTo(normalized);
    }
}
