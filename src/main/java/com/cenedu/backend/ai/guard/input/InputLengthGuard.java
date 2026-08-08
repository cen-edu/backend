package com.cenedu.backend.ai.guard.input;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.guard.GuardDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 사용자 프롬프트가 설정된 최대 문자 수를 넘는지 검사한다.
 *
 * <p>Java의 UTF-16 코드 유닛 수가 아니라 유니코드 코드 포인트 수를 사용해 이모지 같은
 * 보조 평면 문자도 사용자가 보는 한 문자로 계산한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class InputLengthGuard implements InputGuard {

    static final String REASON_CODE = "INPUT_LENGTH_EXCEEDED";

    private final int maxLength;

    public InputLengthGuard(@Value("${app.ai.guard.input.max-length}") int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("입력 길이 제한은 1 이상이어야 합니다.");
        }
        this.maxLength = maxLength;
    }

    @Override
    public GuardDecision inspect(AgentRequest request) {
        String userInput = request.userInput();
        if (userInput == null || userInput.codePointCount(0, userInput.length()) <= maxLength) {
            return GuardDecision.allow();
        }

        return GuardDecision.block(
                REASON_CODE,
                "프롬프트는 최대 %d자까지 입력할 수 있습니다.".formatted(maxLength));
    }
}
