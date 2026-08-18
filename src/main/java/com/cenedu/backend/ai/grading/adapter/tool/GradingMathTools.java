package com.cenedu.backend.ai.grading.adapter.tool;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.cenedu.backend.domain.grading.service.ExpressionEvaluator;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * {@link ExpressionEvaluator} 를 서술형 채점 LLM 이 부를 수 있는 도구로 등록하는 껍데기.
 *
 * <p><b>계산 로직을 여기서 바꾸지 않는다.</b> 전부 위임이다. 규칙 채점 5종과 서술형 채점이
 * <b>같은 계산 코드</b>를 봐야 두 경로의 판정 차이를 계산기 탓으로 돌리지 않을 수 있다.
 *
 * <p>껍데기를 따로 둔 이유는 <b>인자의 출처가 다르기 때문</b>이다. 규칙 채점의 인자는 우리 코드가
 * 정규화해서 만들지만, 도구의 인자는 LLM 이 학생 필기를 읽어 만든다. 신뢰 수준이 다른 입력이
 * 같은 문을 통과하면 안 되므로, LLM 입력에만 필요한 방어를 이 층에 둔다.
 *
 * <p><b>사전 검사로 크래시 도달 자체를 막는다. {@link Error} 를 잡지 않는다.</b> 파서는 백트래킹
 * 없는 재귀 하강이라 시간 폭발은 없지만(40만 자 평탄 입력 104ms), 깊이가 쌓이면
 * {@code StackOverflowError} 가 난다 — 실측 임계는 괄호 1,071 · 절댓값 1,563 · 지수 연쇄 4,911 ·
 * 단항 부호 5,893 이다. 이건 {@code Error} 라 배치 러너의 {@code catch (RuntimeException)} 에
 * 걸리지 않아 <b>한 칸이 나머지 전부를 못 돌게 만든다.</b> 임계값은 JIT 상태에 따라 흔들리므로
 * 가드는 훨씬 아래에 둔다.
 *
 * <p><b>공백만 보정한다.</b> 파서는 공백을 건너뛰지 않아 {@code 3*x + 5} 가 그대로면 거의 모든 호출이
 * {@code UNREADABLE} 이 되고, D9 가 재려던 "곱셈 기호 누락률" 이 공백에 파묻힌다. 공백은 지우는 규칙이
 * 하나뿐이라 보정해도 해석이 갈리지 않는다 — 반면 {@code 2(x+1)} 은 해석 규칙을 새로 만들어야 하므로
 * 미룬다. <b>숫자 사이 공백은 예외다</b>: {@code 1 2} 를 {@code 12} 로 붙이는 것은 보정이 아니라 창작이다.
 *
 * <p><b>도구 인자를 로그에 남기지 않는다.</b> 학생 답안 조각이다. 로그에는 상태와 길이만 남긴다.
 *
 * <p>{@code ai/client} 도 {@code com.openai} 도 참조하지 않는다. Spring AI 의 어노테이션만 쓴다.
 */
@Component
@RequiredArgsConstructor
public class GradingMathTools {

    private static final Logger log = LoggerFactory.getLogger(GradingMathTools.class);

    /** 식 길이 상한. 중1 수식에 충분하고, 실측 크래시 임계 중 가장 낮은 1,071 의 1/5 이하다. */
    static final int MAX_LENGTH = 200;

    /** 괄호 중첩 상한. 실측 임계 1,070 의 2% 다. */
    static final int MAX_PAREN_DEPTH = 20;

    /** 절댓값 중첩 상한. 파서가 여는 것과 닫는 것이 같은 문자라 중첩을 지원하지 않는다. */
    static final int MAX_ABS_DEPTH = 1;

    /** 단항 부호 연속 상한. {@code --1} 까지 받는다. */
    static final int MAX_SIGN_RUN = 3;

    /** 파서가 0으로 나눌 때 내는 메시지. 이 값이 바뀌면 {@code 1/0} 테스트가 먼저 깨진다. */
    private static final String DIVIDE_BY_ZERO_MESSAGE = "0으로 나눔";

    /** 공백을 지우면 두 수가 한 수로 붙는 자리. 여기서는 보정하지 않고 못 읽었다고 한다. */
    private static final Pattern DIGIT_SPACE_DIGIT = Pattern.compile("[0-9] +[0-9]");

    private final ExpressionEvaluator expressionEvaluator;

    /** 도구가 돌려주는 상태. 못 읽은 것과 읽고 나서 실패한 것을 가른다. */
    public enum Status {
        /** 계산했다. {@code value} 또는 {@code holds} 중 하나가 채워진다. */
        OK,
        /** 식으로 읽지 못했다. 오답이라는 뜻이 <b>아니다</b>. */
        UNREADABLE,
        /** 읽었는데 0으로 나눴다. 채점에서 {@code UNREADABLE} 과 뜻이 완전히 다르다. */
        DIVIDE_BY_ZERO,
        /**
         * 읽고 계산했는데 결과가 수가 아니다({@code NaN} · 무한대). 음수의 제곱근처럼
         * <b>그 자체가 채점 신호</b>일 수 있다. 식을 줄여 다시 부를 일이 아니라서
         * {@code TOO_COMPLEX} 와 가른다.
         */
        NOT_FINITE,
        /** 안전 가드에 걸렸다. 식이 너무 길거나 너무 깊다. */
        TOO_COMPLEX,
        /** 식에 있는 변수의 값을 {@code variables} 로 주지 않았다. */
        UNDEFINED_VARIABLE
    }

    /**
     * 도구 반환값. <b>예외를 던지지 않고 상태를 돌려준다</b> — 도구가 던진 예외는 루프를 끊지만,
     * 상태는 모델이 읽고 다음 수를 고를 수 있다.
     *
     * @param value  비교 연산자가 없는 식의 값. 그 외에는 {@code null}
     * @param holds  비교 연산자가 있는 식의 참·거짓. 그 외에는 {@code null}
     * @param note   모델이 읽을 짧은 설명. 로그에는 남기지 않는다
     */
    public record MathResult(Status status, Double value, Boolean holds, String note) {

        static MathResult ok(double value) {
            return new MathResult(Status.OK, value, null, null);
        }

        static MathResult ok(boolean holds) {
            return new MathResult(Status.OK, null, holds, null);
        }

        static MathResult failure(Status status, String note) {
            return new MathResult(status, null, null, note);
        }
    }

    /**
     * 식의 값 또는 참·거짓을 구한다.
     *
     * <p>비교 연산자({@code = < > <= >= !=})가 있으면 참·거짓을, 없으면 값을 낸다.
     * 어느 쪽인지는 {@link ExpressionEvaluator#hasComparison} 이 판단한다.
     */
    @Tool(name = "math",
            description = """
                    수식의 값을 구하거나 등식·부등식이 참인지 확인한다.
                    학생 답안의 수치·식은 눈으로 판단하지 말고 이 도구로 확인한다.
                    비교 연산자(= < > <= >= !=)가 있으면 참·거짓(holds)을, 없으면 값(value)을 돌려준다.
                    곱셈은 반드시 * 를 명시한다. 3x 나 2(x+1) 은 읽지 못한다. 3*x, 2*(x+1) 로 쓴다.
                    변수가 있으면 variables 에 값을 함께 준다. 예: 3*x+5=11 과 {"x": 2} -> holds=true
                    status 가 OK 가 아니면 값이 없다는 뜻이지 학생이 틀렸다는 뜻이 아니다.
                    UNREADABLE 이면 식을 다시 써서 부른다. TOO_COMPLEX 면 식을 짧게 나눠 부른다.
                    NOT_FINITE 는 식은 읽었는데 결과가 수가 아니라는 뜻이다(음수의 제곱근 등).
                    식을 줄여 다시 부르지 말고, 그 사실을 판정 근거로 삼는다.""")
    public MathResult math(
            @ToolParam(description = "확인할 식 하나. 곱셈은 * 를 명시한다. 200자 이내")
            String expression,
            @ToolParam(required = false, description = "식에 쓰인 변수의 값. 변수가 없으면 생략한다")
            Map<String, Double> variables) {

        MathResult result = evaluateGuarded(expression, variables);
        log.info("[도구] math len={} status={}",
                expression == null ? 0 : expression.length(), result.status());
        return result;
    }

    private MathResult evaluateGuarded(String expression, Map<String, Double> variables) {
        if (expression == null || expression.isBlank()) {
            // 파서에 null 을 넘기면 ParseException 이 아니라 NullPointerException 이 난다.
            return MathResult.failure(Status.UNREADABLE, "식이 비어 있다.");
        }
        if (DIGIT_SPACE_DIGIT.matcher(expression).find()) {
            return MathResult.failure(Status.UNREADABLE,
                    "숫자와 숫자 사이에 공백이 있다. 한 수인지 두 수인지 정할 수 없다.");
        }
        String cleaned = expression.replace(" ", "");
        if (cleaned.length() > MAX_LENGTH) {
            return MathResult.failure(Status.TOO_COMPLEX, "식이 " + MAX_LENGTH + "자를 넘는다.");
        }
        MathResult rejected = scanShape(cleaned);
        if (rejected != null) {
            return rejected;
        }

        Map<String, Double> values = variables == null ? Map.of() : variables;
        Set<String> undefined = undefinedVariables(cleaned, values);
        if (!undefined.isEmpty()) {
            return MathResult.failure(Status.UNDEFINED_VARIABLE,
                    "값이 정해지지 않은 변수: " + String.join(", ", undefined));
        }

        try {
            if (expressionEvaluator.hasComparison(cleaned)) {
                return MathResult.ok(expressionEvaluator.evaluateComparison(cleaned, values));
            }
            double value = expressionEvaluator.evaluate(cleaned, values);
            if (!Double.isFinite(value)) {
                // 무한대·NaN 은 값으로 돌려줄 수 없다. JSON 에 실리지 않고, 뜻도 값이 아니다.
                return MathResult.failure(Status.NOT_FINITE, "계산 결과가 수가 아니다.");
            }
            return MathResult.ok(value);
        } catch (ExpressionEvaluator.ParseException e) {
            // 0으로 나눈 것만 메시지로 가른다. 시그니처를 바꿀 수 없어 다른 길이 없다.
            // 메시지가 바뀌면 여기서 조용히 UNREADABLE 로 떨어지고, 1/0 테스트가 먼저 깨진다.
            Status status = DIVIDE_BY_ZERO_MESSAGE.equals(e.getMessage())
                    ? Status.DIVIDE_BY_ZERO
                    : Status.UNREADABLE;
            return MathResult.failure(status, e.getMessage());
        } catch (RuntimeException e) {
            // 도구는 예외를 던지지 않는다(D10). 예상 못 한 실패도 상태로 돌려줘야 루프가 끊기지 않는다.
            // Error 는 잡지 않는다 — 가드가 이미 크래시 도달 자체를 막는다.
            return MathResult.failure(Status.UNREADABLE, "식을 계산하지 못했다.");
        }
    }

    /**
     * 한 번 훑어 문자·깊이·부호 연속을 본다. <b>공백이 지워진 식</b>을 받는다.
     * 파서와 같은 순서로 읽어야 깊이가 맞는다 —
     * 절댓값은 여는 것과 닫는 것이 같은 문자라, "값이 올 자리인지" 를 따라가야 구분된다.
     */
    private MathResult scanShape(String expression) {
        int parenDepth = 0;
        int absDepth = 0;
        int signRun = 0;
        boolean atomExpected = true;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (c == '+' || c == '-') {
                signRun++;
                if (signRun > MAX_SIGN_RUN) {
                    return MathResult.failure(Status.TOO_COMPLEX,
                            "부호가 " + MAX_SIGN_RUN + "개를 넘게 이어진다.");
                }
            } else {
                signRun = 0;
            }

            switch (c) {
                case '(' -> {
                    parenDepth++;
                    if (parenDepth > MAX_PAREN_DEPTH) {
                        return MathResult.failure(Status.TOO_COMPLEX,
                                "괄호가 " + MAX_PAREN_DEPTH + "겹을 넘는다.");
                    }
                    atomExpected = true;
                }
                case ')' -> {
                    parenDepth--;
                    atomExpected = false;
                }
                case '|' -> {
                    if (atomExpected) {
                        absDepth++;
                        if (absDepth > MAX_ABS_DEPTH) {
                            return MathResult.failure(Status.TOO_COMPLEX,
                                    "절댓값 안에 절댓값을 넣을 수 없다.");
                        }
                        // 여는 절댓값 뒤에는 값이 온다.
                        atomExpected = true;
                    } else {
                        absDepth--;
                        atomExpected = false;
                    }
                }
                case '+', '-', '*', '/', '^', '<', '>', '=', '!' -> atomExpected = true;
                default -> {
                    if (!isAllowedAtom(c)) {
                        // 어떤 문자였는지는 남기지 않는다. 학생 답안 조각이다.
                        return MathResult.failure(Status.UNREADABLE, "식에 쓸 수 없는 문자가 있다.");
                    }
                    atomExpected = false;
                }
            }
        }
        return null;
    }

    private static boolean isAllowedAtom(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '.';
    }

    /** 식에 나오는 변수 중 값이 주어지지 않은 것. 값이 {@code null} 인 것도 안 준 것으로 본다. */
    private Set<String> undefinedVariables(String expression, Map<String, Double> values) {
        Set<String> undefined = new LinkedHashSet<>();
        for (String name : expressionEvaluator.findVariables(expression)) {
            if (values.get(name) == null) {
                undefined.add(name);
            }
        }
        return undefined;
    }
}
