package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.ai.agent.ChatMessage;
import java.util.List;

public record ProblemGenerationPrompt(String systemPrompt, List<ChatMessage> messages) {
    public ProblemGenerationPrompt {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
