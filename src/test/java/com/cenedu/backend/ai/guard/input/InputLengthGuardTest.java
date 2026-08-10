package com.cenedu.backend.ai.guard.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.guard.GuardDecision;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InputLengthGuardTest {

    private static final int MAX_LENGTH = 5;

    private final InputLengthGuard guard = new InputLengthGuard(MAX_LENGTH);

    @ParameterizedTest
    @ValueSource(strings = {"", "1234", "12345"})
    @DisplayName("최대 길이 이하의 프롬프트는 통과시킨다")
    void allowsInputWithinMaxLength(String userInput) {
        GuardDecision decision = guard.inspect(request(userInput));

        assertThat(decision).isEqualTo(GuardDecision.allow());
    }

    @Test
    @DisplayName("최대 길이를 초과한 프롬프트는 차단한다")
    void blocksInputExceedingMaxLength() {
        GuardDecision decision = guard.inspect(request("123456"));

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo(InputLengthGuard.REASON_CODE);
        assertThat(decision.message()).isEqualTo("프롬프트는 최대 5자까지 입력할 수 있습니다.");
    }

    @Test
    @DisplayName("null 프롬프트는 길이 검사에서 통과시킨다")
    void allowsNullInput() {
        GuardDecision decision = guard.inspect(request(null));

        assertThat(decision).isEqualTo(GuardDecision.allow());
    }

    @Test
    @DisplayName("보조 평면 문자는 유니코드 코드 포인트 한 문자로 계산한다")
    void countsSupplementaryCharacterAsOneCharacter() {
        InputLengthGuard oneCharacterGuard = new InputLengthGuard(1);

        GuardDecision decision = oneCharacterGuard.inspect(request("😀"));

        assertThat(decision).isEqualTo(GuardDecision.allow());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("최대 길이는 1 이상이어야 한다")
    void rejectsInvalidMaxLength(int maxLength) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InputLengthGuard(maxLength))
                .withMessage("입력 길이 제한은 1 이상이어야 합니다.");
    }

    private static AgentRequest request(String userInput) {
        return AgentRequest.of(
                AgentKind.SOLVE_CHAT,
                new Actor(1L, Actor.Role.STUDENT),
                userInput,
                Map.of());
    }
}
