package com.cenedu.backend.domain.analysis.report.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionTextNormalizerTest {

    @Test
    @DisplayName("수식 구분자를 벗겨 낸다")
    void stripsMathDelimiters() {
        assertThat(QuestionTextNormalizer.toPlainText(
                "세 수 $-3, 4, -6$의 절댓값의 합은?"))
                .isEqualTo("세 수 -3, 4, -6의 절댓값의 합은?");
    }

    @Test
    @DisplayName("지수와 아래첨자를 유니코드로 옮긴다")
    void convertsScripts() {
        assertThat(QuestionTextNormalizer.toPlainText("다음 중 $4^{4}$을 나타내는 것은?"))
                .isEqualTo("다음 중 4⁴을 나타내는 것은?");
        assertThat(QuestionTextNormalizer.toPlainText("$a_{1}$과 $a_{2}$"))
                .isEqualTo("a₁과 a₂");
        assertThat(QuestionTextNormalizer.toPlainText("$2^3$")).isEqualTo("2³");
    }

    @Test
    @DisplayName("옮길 수 없는 지수는 괄호로 남긴다")
    void keepsUnmappableScripts() {
        // 억지로 비슷한 글자를 넣으면 교사가 다른 수식으로 읽는다.
        assertThat(QuestionTextNormalizer.toPlainText("$x^{k}$")).isEqualTo("x^(k)");
    }

    @Test
    @DisplayName("HTML 태그와 엔티티를 정리한다")
    void stripsHtml() {
        assertThat(QuestionTextNormalizer.toPlainText(
                "<div class=\"t\">가로</div>&nbsp;세로 <span>합</span>"))
                .isEqualTo("가로 세로 합");
    }

    @Test
    @DisplayName("너무 긴 문항은 줄여서 표가 밀리지 않게 한다")
    void shortensLongText() {
        String raw = "가".repeat(200);

        String result = QuestionTextNormalizer.toPlainText(raw);

        assertThat(result).hasSize(81).endsWith("…");
    }

    @Test
    @DisplayName("본문이 없으면 빈칸 대신 표시를 남긴다")
    void handlesBlank() {
        assertThat(QuestionTextNormalizer.toPlainText(null)).isEqualTo("-");
        assertThat(QuestionTextNormalizer.toPlainText("   ")).isEqualTo("-");
    }
}
