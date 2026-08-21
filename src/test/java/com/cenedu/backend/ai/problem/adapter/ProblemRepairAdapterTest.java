package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.ai.client.LlmUseCase;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairCommand;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairPlan;
import com.cenedu.backend.domain.problem.authoring.repair.RepairTarget;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;

import tools.jackson.databind.ObjectMapper;

class ProblemRepairAdapterTest {

    @Test
    void 여러_수정대상을_구조화_LLM_한번으로_요청한다() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient client = client("{\"replacements\":{\"EXPLANATION\":\"수정 해설\",\"STEPS\":[]},\"rationale\":\"정합 수정\"}", calls);
        var adapter = new ProblemRepairAdapter(client, new ObjectMapper(), new ProblemRepairPromptFactory());
        var command = command(Set.of(RepairTarget.EXPLANATION, RepairTarget.STEPS));

        var delta = adapter.repair(command);

        assertThat(calls).hasValue(1);
        assertThat(delta.replacements().keySet()).containsExactlyInAnyOrder(
                RepairTarget.EXPLANATION, RepairTarget.STEPS);
    }

    @Test
    void 계획에_없는_수정대상이_응답에_포함되면_거부한다() {
        var adapter = new ProblemRepairAdapter(
                client("{\"replacements\":{\"ANSWERS\":[]},\"rationale\":\"변경\"}", new AtomicInteger()),
                new ObjectMapper(), new ProblemRepairPromptFactory());

        assertThatThrownBy(() -> adapter.repair(command(Set.of(RepairTarget.EXPLANATION))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProblemRepairCommand command(Set<RepairTarget> targets) {
        return new ProblemRepairCommand(UUID.randomUUID(), ProblemSnapshotFixtures.shortInput(),
                new ProblemRepairPlan(targets, List.of("검증 실패"), true));
    }

    private LlmClient client(String response, AtomicInteger calls) {
        return new LlmClient() {
            @Override
            public LlmResponse complete(String systemPrompt, List<ChatMessage> messages,
                                        Long seed, LlmUseCase useCase) {
                calls.incrementAndGet();
                return new LlmResponse(response, 1, 1, 0);
            }
        };
    }
}
