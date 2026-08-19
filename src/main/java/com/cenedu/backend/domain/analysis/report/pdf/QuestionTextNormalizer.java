package com.cenedu.backend.domain.analysis.report.pdf;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문항 본문을 인쇄용 평문으로 바꾼다.
 *
 * <p>문제 은행의 본문에는 화면에서 KaTeX 가 그려 줄 수식 표기가 그대로 들어 있다. PDF 는
 * 자바스크립트를 실행하지 않아 <b>{@code $x$} 같은 표기가 글자 그대로 인쇄된다</b>.
 *
 * <p>본격적인 수식 렌더러를 붙이지 않는다. 실제 문제 은행 5,594 문항을 세어 보면 {@code \frac},
 * {@code \sqrt}, {@code \times} 는 한 건도 없고 지수 232 건, 아래첨자 7 건이 전부다. 구분자를
 * 벗기고 지수만 유니코드로 옮기면 거의 모든 문항이 읽힌다. 렌더러를 붙이는 비용이 얻는 것보다 크다.
 *
 * <p>옮길 수 없는 글자가 섞이면 {@code ^(...)} 처럼 괄호로 남긴다. 억지로 비슷한 글자를 넣으면
 * 교사가 다른 수식으로 읽는다.
 */
final class QuestionTextNormalizer {

    /** 제목이 길면 표가 밀린다. 문항 번호가 이미 있어 앞부분만으로도 어느 문항인지 알 수 있다. */
    private static final int MAX_LENGTH = 80;

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SUPERSCRIPT = Pattern.compile("\\^\\{([^}]*)\\}|\\^(\\S)");
    private static final Pattern SUBSCRIPT = Pattern.compile("_\\{([^}]*)\\}|_(\\S)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Map<Character, Character> SUPERSCRIPTS = Map.ofEntries(
            Map.entry('0', '⁰'), Map.entry('1', '¹'), Map.entry('2', '²'),
            Map.entry('3', '³'), Map.entry('4', '⁴'), Map.entry('5', '⁵'),
            Map.entry('6', '⁶'), Map.entry('7', '⁷'), Map.entry('8', '⁸'),
            Map.entry('9', '⁹'), Map.entry('+', '⁺'), Map.entry('-', '⁻'),
            Map.entry('=', '⁼'), Map.entry('(', '⁽'), Map.entry(')', '⁾'),
            Map.entry('n', 'ⁿ'), Map.entry('i', 'ⁱ'));

    private static final Map<Character, Character> SUBSCRIPTS = Map.ofEntries(
            Map.entry('0', '₀'), Map.entry('1', '₁'), Map.entry('2', '₂'),
            Map.entry('3', '₃'), Map.entry('4', '₄'), Map.entry('5', '₅'),
            Map.entry('6', '₆'), Map.entry('7', '₇'), Map.entry('8', '₈'),
            Map.entry('9', '₉'), Map.entry('+', '₊'), Map.entry('-', '₋'),
            Map.entry('=', '₌'), Map.entry('(', '₍'), Map.entry(')', '₎'));

    private QuestionTextNormalizer() {
    }

    /** 표와 제목에 넣을 한 줄짜리 평문으로 만든다. */
    static String toPlainText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        String text = HTML_TAG.matcher(raw).replaceAll(" ");
        text = text.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">");
        text = convert(SUPERSCRIPT, text, SUPERSCRIPTS, "^");
        text = convert(SUBSCRIPT, text, SUBSCRIPTS, "_");
        text = text.replace("$", "");
        text = WHITESPACE.matcher(text).replaceAll(" ").strip();
        return shorten(text);
    }

    private static String convert(
            Pattern pattern,
            String text,
            Map<Character, Character> table,
            String marker
    ) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String body = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(replaceAll(body, table, marker)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String replaceAll(
            String body,
            Map<Character, Character> table,
            String marker
    ) {
        StringBuilder converted = new StringBuilder();
        for (char character : body.toCharArray()) {
            Character mapped = table.get(character);
            if (mapped == null) {
                // 하나라도 옮길 수 없으면 통째로 원래 표기를 남긴다.
                return marker + "(" + body + ")";
            }
            converted.append(mapped);
        }
        return converted.toString();
    }

    private static String shorten(String text) {
        if (text.length() <= MAX_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_LENGTH).strip() + "…";
    }
}
