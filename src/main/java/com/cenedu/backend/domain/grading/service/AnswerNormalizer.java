package com.cenedu.backend.domain.grading.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 채점 직전에 답을 비교 가능한 한 가지 모양으로 맞춘다.
 *
 * <p>{@code submission_answer.normalized}는 저장 API가 {@code null}로 남겨 둔 값이다(엔티티 주석:
 * "정규화는 채점 작업 소관이라 저장 경로가 대충 채우면 안 된다"). 여기서 {@code raw_latex}로부터 만든다.
 *
 * <p><b>정답에도 같은 함수를 한 번 더 먹인다.</b> {@code problem_answer_unit.answer_normalized}가
 * 이미 채워져 있지만(실측: {@code \frac{40}{3}} → {@code 40/3}, {@code \pi} → {@code pi}), 그 값이
 * 어떤 규칙으로 만들어졌는지 이 코드가 보증할 수 없다. 양쪽을 같은 함수에 통과시키면 정답 쪽 정규형이
 * 무엇이든 축이 맞는다 — 이 함수는 멱등이라 이미 정규형인 값은 그대로 남는다.
 */
@Component
public class AnswerNormalizer {

    /** {@code \dfrac}·{@code \tfrac}·{@code \cfrac}은 표시 방식만 다르다. 필기 입력이 이것들을 낸다. */
    private static final Pattern FRAC_ALIAS = Pattern.compile("\\\\[dtc]frac");

    /**
     * 표시용 공백. {@code \quad} 류와 함께 <b>{@code ~}(비분리 공백)도 지운다</b> —
     * 실측에서 {@code 10 \pi ~}, {@code 8 ~cm} 처럼 답 끝이나 단위 앞에 붙어 있다.
     * 범위 기호가 아니다(범위는 {@code \sim}).
     */
    private static final Pattern LATEX_SPACING =
            Pattern.compile("\\\\(?:quad|qquad|[,;:!])|~");

    /**
     * 표를 열고 닫는 명령. 추출이 표의 한 칸만 잘라 오면서 <b>짝 없는 {@code \end{array}}</b>가
     * 답 뒤에 남는 경우가 있다({@code 324\end{array}} = 324).
     */
    private static final Pattern ARRAY_ENVIRONMENT =
            Pattern.compile("\\\\begin\\{array\\}(?:\\{[a-z|]*\\})?|\\\\end\\{array\\}");

    /** 각도. {@code 35^{\circ}} 는 35도이며 값 자체는 35다. {@code \prime}(분)도 같이 떨어뜨린다. */
    private static final Pattern DEGREE =
            Pattern.compile("\\^\\{\\s*\\\\circ(?:\\s*\\\\prime)?\\s*\\}|\\{\\s*\\}\\^\\{\\s*\\\\circ\\s*\\}|°");

    /** {@code \angle x=50^{\circ}} 의 각 기호. 값 비교에는 쓰이지 않는다. */
    private static final Pattern ANGLE = Pattern.compile("\\\\angle\\s*");

    /** {@code \overline{AD}} → {@code AD}. 선분 이름이라 기호만 남기면 된다. */
    private static final Pattern OVERLINE = Pattern.compile("\\\\overline\\{([^{}]*)\\}");

    /** {@code 2^{4}} → {@code 2^4}. 소인수분해 답이 전부 이 모양이다. */
    private static final Pattern BRACED_EXPONENT = Pattern.compile("\\^\\{([^{}]*)\\}");

    /** 보기 번호 접두. {@code ① 3^{3}} 의 답은 {@code 3^3}이고 ①은 문항 번호다. */
    private static final Pattern CHOICE_MARKER_PREFIX = Pattern.compile("^[①-⑮]\\s*");

    /** 표 구분자 {@code &}. 표에서 잘라 온 흔적이다({@code 105^{\circ} &}). */
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("&");

    /** 리터럴 중괄호 {@code \{…\}}. 감싸는 껍데기라 벗긴다. */
    private static final Pattern LITERAL_BRACES = Pattern.compile("\\\\\\{|\\\\\\}");

    /** 답 전체를 꺾쇠로 감싼 표기({@code <14/5 a>}). 부등호가 아니다. */
    private static final Pattern ANGLE_BRACKET_WRAPPER = Pattern.compile("^<(.*)>$");

    /**
     * 한글 라벨 접두({@code 점 C}, {@code 변 AD}, {@code 모서리 AB}, {@code 각 ABC}).
     * <b>정답과 학생 답 양쪽에 같이 적용</b>되므로 축이 어긋나지 않는다.
     */
    private static final Pattern KOREAN_LABEL_PREFIX =
            Pattern.compile("(^|[,\\s])(?:점|변|각|모서리|면|선분|호|현)\\s+(?=[A-Za-z])");

    /**
     * 부등호 명령을 기호로. LaTeX 명령은 <b>글자가 아닌 것</b>에서 끝나므로 {@code \ge1} 처럼
     * 숫자가 바로 붙어도 잘라야 한다 — {@code \b}를 쓰면 {@code e}와 {@code 1} 사이에 경계가 없어
     * 매치되지 않는다.
     */
    private static final Pattern INEQUALITY_COMMAND =
            Pattern.compile("\\\\(?:leq|le|geq|ge|neq|lt|gt)(?![a-zA-Z])");

    /** {@code \frac{분자}{분모}}. 중첩은 안쪽부터 반복 치환한다. */
    private static final Pattern FRACTION =
            Pattern.compile("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}");

    /** 괄호 없이 그대로 이어 붙여도 안전한 분자·분모(숫자 하나 또는 변수 하나). */
    private static final Pattern SINGLE_TERM = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?|[a-zA-Z]");

    /**
     * 비교용 정규형으로 바꾼다. 바꿀 수 없으면 {@code null} — 호출부가 채점 실패로 기록한다.
     *
     * @param displayUnit 표시용 단위({@code cm^2} 등). 답에 붙어 있으면 떼어 낸다
     */
    public String normalize(String raw, String displayUnit) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        value = stripDisplayUnit(value, displayUnit);
        value = CHOICE_MARKER_PREFIX.matcher(value).replaceAll("");
        value = ARRAY_ENVIRONMENT.matcher(value).replaceAll("");
        value = TABLE_SEPARATOR.matcher(value).replaceAll("");
        value = FRAC_ALIAS.matcher(value).replaceAll("\\\\frac");
        value = LATEX_SPACING.matcher(value).replaceAll("");
        value = replaceInequalityCommands(value);
        value = ANGLE.matcher(value).replaceAll("");
        value = OVERLINE.matcher(value).replaceAll("$1");
        value = KOREAN_LABEL_PREFIX.matcher(value).replaceAll("$1");
        // 각도 → 지수 → 분수 순서를 지킨다. 지수를 먼저 풀지 않으면 \frac{2 x^{2}}{5} 의 분자에
        // 중괄호가 남아 분수 패턴이 매치되지 않고, 각도를 먼저 풀지 않으면 ^{\circ} 가 지수로 읽힌다.
        value = DEGREE.matcher(value).replaceAll("");
        value = BRACED_EXPONENT.matcher(value).replaceAll("^$1");
        value = expandFractions(value);
        value = LITERAL_BRACES.matcher(value).replaceAll("");
        value = value.replace("\\pi", "pi").replace("π", "pi");
        value = value.replace("\\times", "*").replace("\\cdot", "*").replace("\\div", "/");
        value = value.replaceAll("\\s+", "");
        value = stripAngleBracketWrapper(value);
        value = insertImplicitProducts(value);
        return value.isEmpty() ? null : value;
    }

    /**
     * {@code \frac{a}{b}} → {@code a/b}.
     *
     * <p>분자·분모가 항 하나면 괄호를 붙이지 않는다 — 정답 쪽 정규형이 {@code 40/3}이라 괄호를 붙이면
     * 문자열 비교(EXACT·SET)에서 어긋난다. 항이 여럿이면 괄호가 없으면 뜻이 바뀌므로 붙인다.
     */
    private String expandFractions(String value) {
        String previous;
        String current = value;
        do {
            previous = current;
            Matcher matcher = FRACTION.matcher(current);
            StringBuilder builder = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(
                        wrapIfNeeded(matcher.group(1)) + "/" + wrapIfNeeded(matcher.group(2))));
            }
            matcher.appendTail(builder);
            current = builder.toString();
        } while (!current.equals(previous));
        return current;
    }

    private String wrapIfNeeded(String term) {
        String trimmed = term.trim();
        return SINGLE_TERM.matcher(trimmed).matches() ? trimmed : "(" + trimmed + ")";
    }

    /**
     * 생략된 곱셈 기호를 되살린다({@code 8/9pix} → {@code 8/9*pi*x}).
     *
     * <p>정규식 하나로는 못 한다 — {@code pi}가 두 글자라 문자 단위 규칙이 {@code p}와 {@code i}
     * 사이를 끊는다. 값 토큰(숫자·{@code pi}·변수 한 글자·닫는 괄호)을 실제로 끊어 읽고, 값 토큰이
     * 연달아 나오는 자리에만 {@code *}를 넣는다.
     *
     * <p>한글 답({@code 정삼각형})은 값 토큰이 아니라 그대로 지나간다.
     */
    private String insertImplicitProducts(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        boolean previousWasValue = false;
        boolean insideAbsolute = false;
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            // 절댓값은 여는 것과 닫는 것이 같은 문자라 위치로 구분한다.
            if (c == '|') {
                if (!insideAbsolute && previousWasValue) {
                    result.append('*');
                }
                result.append(c);
                previousWasValue = insideAbsolute;
                insideAbsolute = !insideAbsolute;
                i++;
                continue;
            }
            int tokenEnd = valueTokenEnd(value, i);
            if (tokenEnd < 0) {
                result.append(c);
                previousWasValue = c == ')';
                i++;
                continue;
            }
            if (previousWasValue) {
                result.append('*');
            }
            result.append(value, i, tokenEnd);
            // 여는 괄호는 앞에는 곱이 붙지만 뒤는 값의 끝이 아니다 — 여기서 값으로 두면
            // 2*(x+1) 이 2*(*x+1) 이 된다.
            previousWasValue = c != '(';
            i = tokenEnd;
        }
        return result.toString();
    }

    /**
     * {@code start} 위치에서 시작하는 값 토큰의 끝 위치. 값 토큰이 아니면 {@code -1}.
     *
     * <p>여는 괄호도 값의 시작으로 본다 — {@code 2(x+1)}의 생략된 곱을 살리기 위해서다.
     */
    private int valueTokenEnd(String value, int start) {
        char c = value.charAt(start);
        if (Character.isDigit(c) || c == '.') {
            int end = start;
            while (end < value.length()
                    && (Character.isDigit(value.charAt(end)) || value.charAt(end) == '.')) {
                end++;
            }
            return end;
        }
        if (value.startsWith("pi", start)) {
            return start + 2;
        }
        if (c == '(') {
            return start + 1;
        }
        // ASCII 한 글자만 변수로 본다. 한글은 수식이 아니라 서술형 답이다.
        return (c < 128 && Character.isLetter(c)) ? start + 1 : -1;
    }

    /** {@code \le} {@code \ge} {@code \neq} 를 파서가 읽는 기호로 바꾼다. */
    private String replaceInequalityCommands(String value) {
        Matcher matcher = INEQUALITY_COMMAND.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String command = matcher.group();
            String symbol = switch (command) {
                case "\\leq", "\\le" -> "<=";
                case "\\geq", "\\ge" -> ">=";
                case "\\neq" -> "!=";
                case "\\lt" -> "<";
                default -> ">";
            };
            matcher.appendReplacement(builder, Matcher.quoteReplacement(symbol));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    /**
     * 답 전체를 감싼 꺾쇠를 벗긴다. 안에 부등호가 또 있으면 감싼 것이 아니라 진짜 부등식이므로
     * 건드리지 않는다.
     */
    private String stripAngleBracketWrapper(String value) {
        Matcher matcher = ANGLE_BRACKET_WRAPPER.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        String inner = matcher.group(1);
        return (inner.contains("<") || inner.contains(">")) ? value : inner;
    }

    private String stripDisplayUnit(String value, String displayUnit) {
        if (displayUnit == null || displayUnit.isBlank()) {
            return value;
        }
        String unit = displayUnit.trim();
        return value.endsWith(unit) ? value.substring(0, value.length() - unit.length()).trim() : value;
    }
}
