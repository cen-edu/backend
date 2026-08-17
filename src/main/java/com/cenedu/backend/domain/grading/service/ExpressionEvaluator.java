package com.cenedu.backend.domain.grading.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 정규형 수식을 읽어 값을 낸다. {@code VALUE}의 수치 동치와 {@code SUBST}의 표본 대입이 함께 쓴다.
 *
 * <p>받아들이는 문법은 데이터에 실제로 있는 것만이다(실측) — 숫자·소수, 변수 한 글자, 상수 {@code pi},
 * {@code + - * / ^}, 괄호, 단항 부호. 그 밖의 것({@code |b|}, {@code 155~160}, 한글이 섞인 값 등
 * {@code SUBST} 2,233건 중 228건)은 <b>파싱 실패로 처리한다</b> — 틀렸다고 판정하지 않는다.
 * 못 읽는 것과 오답은 다르고, 그 구분이 {@code FAILED}와 0점을 가른다.
 */
@Component
public class ExpressionEvaluator {

    /** 읽을 수 없는 수식. 오답이 아니라 판정 불가라는 뜻이다. */
    public static class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }

    /** 정규형 문자열의 값을 구한다. 변수는 {@code values}에서 찾는다. */
    public double evaluate(String expression, Map<String, Double> values) {
        Parser parser = new Parser(expression, values);
        double result = parser.parseSum();
        parser.expectEnd();
        return result;
    }

    /**
     * 부등식·등식의 참·거짓을 구한다. {@code b<c<a} 같은 <b>연쇄 부등식</b>은 이웃한 쌍을 모두
     * 만족해야 참이다(수학에서 읽는 그대로다).
     *
     * <p>값이 아니라 진리값을 내므로 {@link #evaluate}와 쓰임이 다르다 — 두 부등식이 같은지는
     * 값을 빼서 볼 수 없고, 표본마다 진리값이 같은지로 봐야 한다.
     */
    public boolean evaluateComparison(String expression, Map<String, Double> values) {
        List<String> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        splitComparison(expression, operands, operators);
        if (operators.isEmpty()) {
            throw new ParseException("비교 연산자가 없다: " + expression);
        }
        for (int i = 0; i < operators.size(); i++) {
            double left = evaluate(operands.get(i), values);
            double right = evaluate(operands.get(i + 1), values);
            if (!holds(operators.get(i), left, right)) {
                return false;
            }
        }
        return true;
    }

    /** 식에 비교 연산자가 있는지. 있으면 {@link #evaluateComparison}으로 판정해야 한다. */
    public boolean hasComparison(String expression) {
        List<String> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        splitComparison(expression, operands, operators);
        return !operators.isEmpty();
    }

    /** 괄호 밖의 비교 연산자를 기준으로 끊는다. 괄호 안의 것은 식의 일부다. */
    private void splitComparison(String expression, List<String> operands, List<String> operators) {
        int depth = 0;
        int start = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && (c == '<' || c == '>' || c == '=' || c == '!')) {
                String operator = expression.startsWith("=", i + 1)
                        ? expression.substring(i, i + 2)
                        : String.valueOf(c);
                operands.add(expression.substring(start, i));
                operators.add(operator);
                i += operator.length() - 1;
                start = i + 1;
            }
        }
        operands.add(expression.substring(start));
    }

    private boolean holds(String operator, double left, double right) {
        return switch (operator) {
            case "<" -> left < right;
            case ">" -> left > right;
            case "<=" -> left <= right;
            case ">=" -> left >= right;
            case "!=" -> Math.abs(left - right) > 1e-9;
            case "=", "==" -> Math.abs(left - right) <= 1e-9;
            default -> throw new ParseException("알 수 없는 비교 연산자: " + operator);
        };
    }

    /** 식에 나오는 변수 이름을 등장 순서대로 모은다. {@code pi}는 상수라 세지 않는다. */
    public Set<String> findVariables(String expression) {
        Set<String> variables = new LinkedHashSet<>();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            if (expression.startsWith("pi", i)) {
                i++;
                continue;
            }
            variables.add(String.valueOf(c));
        }
        return variables;
    }

    /** 재귀 하강 파서. 한 번 쓰고 버린다. */
    private static final class Parser {

        private final String input;
        private final Map<String, Double> values;
        private int position;

        private Parser(String input, Map<String, Double> values) {
            this.input = input;
            this.values = values;
        }

        private double parseSum() {
            double result = parseProduct();
            while (position < input.length()) {
                char operator = input.charAt(position);
                if (operator != '+' && operator != '-') {
                    break;
                }
                position++;
                double right = parseProduct();
                result = operator == '+' ? result + right : result - right;
            }
            return result;
        }

        private double parseProduct() {
            double result = parsePower();
            while (position < input.length()) {
                char operator = input.charAt(position);
                if (operator != '*' && operator != '/') {
                    break;
                }
                position++;
                double right = parsePower();
                if (operator == '/') {
                    if (right == 0.0) {
                        throw new ParseException("0으로 나눔");
                    }
                    result /= right;
                } else {
                    result *= right;
                }
            }
            return result;
        }

        private double parsePower() {
            double base = parseUnary();
            if (position < input.length() && input.charAt(position) == '^') {
                position++;
                // 지수는 오른쪽 결합이다: 2^3^2 = 2^(3^2).
                return Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary() {
            if (position < input.length()) {
                char c = input.charAt(position);
                if (c == '-') {
                    position++;
                    return -parseUnary();
                }
                if (c == '+') {
                    position++;
                    return parseUnary();
                }
            }
            return parseAtom();
        }

        private double parseAtom() {
            if (position >= input.length()) {
                throw new ParseException("식이 도중에 끝남");
            }
            char c = input.charAt(position);
            if (c == '(') {
                position++;
                double inner = parseSum();
                if (position >= input.length() || input.charAt(position) != ')') {
                    throw new ParseException("괄호가 닫히지 않음");
                }
                position++;
                return inner;
            }
            if (c == '|') {
                // 절댓값. 여는 것과 닫는 것이 같은 문자라 중첩은 지원하지 않는다 —
                // 데이터에 |a|b|| 같은 형태가 없다.
                position++;
                double inner = parseSum();
                if (position >= input.length() || input.charAt(position) != '|') {
                    throw new ParseException("절댓값이 닫히지 않음");
                }
                position++;
                return Math.abs(inner);
            }
            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }
            if (input.startsWith("pi", position)) {
                position += 2;
                return Math.PI;
            }
            if (Character.isLetter(c) && c < 128) {
                position++;
                String name = String.valueOf(c);
                Double value = values.get(name);
                if (value == null) {
                    throw new ParseException("값이 정해지지 않은 변수: " + name);
                }
                return value;
            }
            throw new ParseException("읽을 수 없는 문자: " + c);
        }

        private double parseNumber() {
            int start = position;
            while (position < input.length()
                    && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
                position++;
            }
            try {
                return Double.parseDouble(input.substring(start, position));
            } catch (NumberFormatException e) {
                throw new ParseException("숫자로 읽을 수 없음: " + input.substring(start, position));
            }
        }

        private void expectEnd() {
            if (position < input.length()) {
                throw new ParseException("식 뒤에 남은 문자: " + input.substring(position));
            }
        }
    }
}
