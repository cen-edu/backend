package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.ai.client.LlmUseCase;

/**
 * 정해 둔 응답을 순서대로 돌려주는 {@link LlmClient}.
 *
 * <p>프롬프트를 그대로 보관한다. Solver 프롬프트에 정답이 들어갔는지를 종단으로 확인할 수 있어야
 * 하기 때문이다 — Blind 변환 단위 테스트만으로는 <b>Adapter 가 Blind 를 실제로 쓰는지</b>를 보지 못한다.
 */
class FakeLlmClient implements LlmClient {

    final List<String> systemPrompts = new ArrayList<>();
    final List<String> userPrompts = new ArrayList<>();
    final List<LlmUseCase> useCases = new ArrayList<>();
    final List<Long> seeds = new ArrayList<>();

    private final Deque<String> responses = new ArrayDeque<>();
    private RuntimeException failure;

    FakeLlmClient respondWith(String... texts) {
        for (String text : texts) {
            responses.add(text);
        }
        return this;
    }

    FakeLlmClient failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public LlmResponse complete(
            String systemPrompt, List<ChatMessage> messages, Long seed, LlmUseCase useCase
    ) {
        systemPrompts.add(systemPrompt);
        userPrompts.add(messages.getLast().content());
        useCases.add(useCase);
        seeds.add(seed);

        if (failure != null) {
            throw failure;
        }
        if (responses.isEmpty()) {
            throw new IllegalStateException("준비된 응답이 없다 — 테스트가 예상보다 많이 호출했다.");
        }
        return new LlmResponse(responses.poll(), 10, 20, 5);
    }

    /** Solver 호출의 사용자 프롬프트. 문항이 실려 나간 내용이다. */
    String solverPrompt() {
        return userPrompts.getFirst();
    }
}
