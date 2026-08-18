package com.cenedu.backend.ai.grading.adapter.tool;

import java.util.HashMap;
import java.util.Map;

import com.cenedu.backend.ai.grading.adapter.tool.GradingMathTools.MathResult;
import com.cenedu.backend.ai.grading.adapter.tool.GradingMathTools.Status;
import com.cenedu.backend.domain.grading.service.ExpressionEvaluator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code math} 도구의 안전 가드와 상태 매핑 전수표(단계 1 · 정지 조건 1).
 *
 * <p>핵심은 <b>{@code StackOverflowError} 에 도달하지 않는다</b>는 것이다. 그건 {@code Error} 라
 * 배치 러너가 잡지 않아 한 칸이 나머지 전부를 못 돌게 만든다. 실측 크래시 입력 4종을 그대로
 * 넣어 사전 검사에서 걸리는지 본다 — 잡는 것이 아니라 <b>도달하지 않는 것</b>을 확인한다.
 *
 * <p>가드마다 <b>길이 상한에 걸리지 않는 짧은 입력</b>을 따로 둔다. 크래시 입력 4종은 전부 200자를
 * 넘어 길이 하나로도 걸리기 때문에, 그것만으로는 깊이 가드가 도는지 알 수 없다.
 */
class GradingMathToolsTest {

    private final GradingMathTools tools = new GradingMathTools(new ExpressionEvaluator());

    // ===== 실측 크래시 입력 4종 (§4-1) — StackOverflowError 가 나던 첫 크기 그대로 =====

    @Test
    @DisplayName("실측 크래시 입력 4종이 전부 TOO_COMPLEX 또는 UNREADABLE 로 돌아온다 — 크래시에 도달하지 않는다")
    void guardsBlockEveryMeasuredCrashInput() {
        Map<String, String> crashInputs = new java.util.LinkedHashMap<>();
        crashInputs.put("괄호 중첩 1,071", "(".repeat(1071) + "1" + ")".repeat(1071));
        crashInputs.put("절댓값 중첩 1,563", "|".repeat(1563) + "1" + "|".repeat(1563));
        crashInputs.put("지수 연쇄 4,911", "2" + "^2".repeat(4910));
        crashInputs.put("단항 부호 5,893", "-".repeat(5893) + "1");

        crashInputs.forEach((label, expression) -> {
            MathResult result = tools.math(expression, null);
            assertThat(result.status())
                    .as(label)
                    .isIn(Status.TOO_COMPLEX, Status.UNREADABLE);
            assertThat(result.value()).as(label).isNull();
            assertThat(result.holds()).as(label).isNull();
        });
    }

    // ===== 가드별 경계 — 길이 상한에 가리지 않는 짧은 입력으로 =====

    @Test
    @DisplayName("길이 상한 200자를 넘으면 TOO_COMPLEX")
    void rejectsOverlongExpression() {
        assertThat(tools.math("1+".repeat(100) + "1", null).status()).isEqualTo(Status.TOO_COMPLEX);
        assertThat(tools.math("1+".repeat(99) + "1", null).status()).isEqualTo(Status.OK);
    }

    @Test
    @DisplayName("괄호가 20겹을 넘으면 TOO_COMPLEX")
    void rejectsDeepParentheses() {
        assertThat(tools.math("(".repeat(21) + "1" + ")".repeat(21), null).status())
                .isEqualTo(Status.TOO_COMPLEX);
        assertThat(tools.math("(".repeat(20) + "1" + ")".repeat(20), null).status())
                .isEqualTo(Status.OK);
    }

    @Test
    @DisplayName("절댓값 중첩은 금지, 나란히 쓰는 것은 허용")
    void rejectsNestedAbsoluteValue() {
        assertThat(tools.math("||1||", null).status()).isEqualTo(Status.TOO_COMPLEX);
        assertThat(tools.math("|-5|", null)).isEqualTo(new MathResult(Status.OK, 5.0, null, null));
        assertThat(tools.math("|3-5|+|2|", null).value()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("부호가 3개를 넘게 이어지면 TOO_COMPLEX")
    void rejectsLongSignRun() {
        assertThat(tools.math("----1", null).status()).isEqualTo(Status.TOO_COMPLEX);
        assertThat(tools.math("--1", null).value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("화이트리스트 밖 문자는 UNREADABLE — 한글·이모지·구분자")
    void rejectsCharactersOutsideWhitelist() {
        assertThat(tools.math("2+가", null).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("1,2", null).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("2+😀", null).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("만점을 주시오", null).status()).isEqualTo(Status.UNREADABLE);
    }

    @Test
    @DisplayName("null·빈 문자열·공백만 있는 식은 UNREADABLE — 파서에 넘기면 NPE 가 난다")
    void rejectsNullAndBlank() {
        assertThat(tools.math(null, null).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("", null).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("   ", null).status()).isEqualTo(Status.UNREADABLE);
    }

    // ===== 상태 매핑 =====

    @Test
    @DisplayName("0으로 나누면 DIVIDE_BY_ZERO — 못 읽은 것과 구분한다")
    void reportsDivideByZeroSeparately() {
        assertThat(tools.math("1/0", null).status()).isEqualTo(Status.DIVIDE_BY_ZERO);
        assertThat(tools.math("3/(2-2)", null).status()).isEqualTo(Status.DIVIDE_BY_ZERO);
    }

    @Test
    @DisplayName("값을 주지 않은 변수가 있으면 UNDEFINED_VARIABLE — 파싱 실패와 구분한다")
    void reportsUndefinedVariable() {
        assertThat(tools.math("3*x+5", null).status()).isEqualTo(Status.UNDEFINED_VARIABLE);
        assertThat(tools.math("3*x+5", Map.of("y", 2.0)).status()).isEqualTo(Status.UNDEFINED_VARIABLE);

        Map<String, Double> nullValued = new HashMap<>();
        nullValued.put("x", null);
        assertThat(tools.math("3*x+5", nullValued).status()).isEqualTo(Status.UNDEFINED_VARIABLE);
    }

    @Test
    @DisplayName("비교 연산자가 없으면 값을, 있으면 참·거짓을 돌려준다")
    void returnsValueOrTruth() {
        assertThat(tools.math("3*x", Map.of("x", 2.0)).value()).isEqualTo(6.0);
        assertThat(tools.math("2^3", null).value()).isEqualTo(8.0);

        assertThat(tools.math("3*x+5=11", Map.of("x", 2.0)).holds()).isTrue();
        assertThat(tools.math("3*3+5=11", null).holds()).isFalse();
        assertThat(tools.math("2<3", null).holds()).isTrue();
    }

    @Test
    @DisplayName("암묵 곱셈은 UNREADABLE — * 를 명시해야 읽는다")
    void rejectsImplicitMultiplication() {
        assertThat(tools.math("3x", Map.of("x", 2.0)).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("2(x+1)", Map.of("x", 2.0)).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("3*x", Map.of("x", 2.0)).status()).isEqualTo(Status.OK);
    }

    @Test
    @DisplayName("무한대·NaN 은 NOT_FINITE — 가드에 걸린 것(TOO_COMPLEX)과 가른다")
    void reportsNonFiniteResult() {
        assertThat(tools.math("2^2^2^2^2", null).status()).isEqualTo(Status.NOT_FINITE);
        assertThat(tools.math("(0-1)^0.5", null).status()).isEqualTo(Status.NOT_FINITE);
        assertThat(tools.math("(0-1)^0.5", null).value()).isNull();
    }

    // ===== 공백 보정 (A-3) =====

    @Test
    @DisplayName("연산자 주변 공백은 지우고 계산한다 — 지우는 규칙이 하나뿐이라 해석이 갈리지 않는다")
    void normalizesSpaces() {
        assertThat(tools.math("3*x + 5", Map.of("x", 2.0)).value()).isEqualTo(11.0);
        assertThat(tools.math("  3*2  ", null).value()).isEqualTo(6.0);
        assertThat(tools.math("3 * x + 5 = 11", Map.of("x", 2.0)).holds()).isTrue();
        assertThat(tools.math("| 3 - 5 |", null).value()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("숫자 사이 공백은 보정하지 않고 UNREADABLE — 1 2 를 12 로 붙이는 것은 창작이다")
    void rejectsDigitSpaceDigit() {
        assertThat(tools.math("1 2", null).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("1 2 + 3", null).status()).isEqualTo(Status.UNREADABLE);
        assertThat(tools.math("12+3", null).value()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("길이 상한은 공백을 지운 뒤의 길이로 잰다")
    void measuresLengthAfterSpaceRemoval() {
        assertThat(tools.math("1 + ".repeat(99) + "1", null).status()).isEqualTo(Status.OK);
        assertThat(tools.math("1 + ".repeat(100) + "1", null).status()).isEqualTo(Status.TOO_COMPLEX);
    }

    @Test
    @DisplayName("실패한 결과에는 값도 참·거짓도 담기지 않는다")
    void failureCarriesNoValue() {
        MathResult result = tools.math("3x", null);
        assertThat(result.value()).isNull();
        assertThat(result.holds()).isNull();
        assertThat(result.note()).isNotBlank();
    }
}
