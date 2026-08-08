package com.cenedu.backend.ai.guard.input;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.guard.GuardDecision;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 사용자가 시스템·개발자·이전 지시를 무시하거나 공개하도록 유도하는 요청을 차단한다.
 *
 * <p>공백, 구두점, 전각 문자 등을 섞은 단순 난독화를 피하기 위해 NFKC 정규화 후
 * 한글·영문·숫자만 남긴 문자열을 검사한다. 정답 요청이나 역할극 자체는 이 가드의 책임이
 * 아니므로 단독으로 차단하지 않고, 보호해야 할 지시를 무력화하려는 표현만 대상으로 삼는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class PromptInjectionGuard implements InputGuard {

    static final String REASON_CODE = "PROMPT_INJECTION_DETECTED";

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 이전 지시나 규칙을 무시·폐기·우회하라는 한국어 요청
            Pattern.compile(
                    "(?:이전|기존|앞선|위|상기|모든|지금까지)(?:의)?"
                            + "(?:시스템|개발자)?(?:지시|명령|규칙|프롬프트|정책|가이드라인)"
                            + "(?:을|를|은|는)?(?:전부|모두)?"
                            + "(?:무시|잊어|잊고|폐기|취소|삭제|무효화|우회|덮어쓰|따르지말|따르지마|따르지않)"),

            // 시스템·개발자 지시를 직접 무시하거나 노출하라는 한국어 요청
            Pattern.compile(
                    "(?:시스템|개발자)(?:의)?(?:지시|명령|메시지|프롬프트|규칙|정책)"
                            + "(?:을|를|은|는)?"
                            + "(?:무시|잊어|폐기|취소|삭제|무효화|우회|공개|출력|복사|반복|보여|알려)"),

            // 영어로 이전 또는 상위 지시를 무시·우회하라는 요청
            Pattern.compile(
                    "(?:ignore|disregard|forget|override|bypass)"
                            + "(?:all)?(?:the)?(?:previous|prior|above|system|developer)"
                            + "(?:instructions?|rules?|prompts?|messages?|polic(?:y|ies))"),

            // 영어로 시스템·개발자 프롬프트를 노출하라는 요청
            Pattern.compile(
                    "(?:reveal|show|print|repeat|display|expose|leak)"
                            + "(?:the)?(?:hidden)?(?:system|developer)"
                            + "(?:prompts?|messages?|instructions?|rules?|polic(?:y|ies))")
    );

    @Override
    public GuardDecision inspect(AgentRequest request) {
        String userInput = request.userInput();
        if (userInput == null || userInput.isBlank()) {
            return GuardDecision.allow();
        }

        String normalizedInput = normalize(userInput);
        boolean injectionDetected = INJECTION_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(normalizedInput).find());

        if (!injectionDetected) {
            return GuardDecision.allow();
        }

        return GuardDecision.block(
                REASON_CODE,
                "시스템 지시를 무력화하거나 공개하려는 요청은 처리할 수 없습니다.");
    }

    // 사용자가 입력한 원본 문자열을 검사하기 좋은 형태로 가공
    private static String normalize(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);

        StringBuilder compact = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(PromptInjectionGuard::isLetterOrDigit)
                .forEach(compact::appendCodePoint);
        return compact.toString();
    }

    private static boolean isLetterOrDigit(int codePoint) {
        return Character.isLetterOrDigit(codePoint);
    }
}
