package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProblemSemanticExtractionPromptFactory {
    private final ObjectMapper mapper = new ObjectMapper();

    public String systemPrompt() {
        return "기존 수학 문제를 semantic model v1로 구조화하라. 원본의 정답과 의미를 보존하고 "
                + "지원하지 않는 도형·연산은 임의로 추정하지 말라. JSON만 출력하라.";
    }

    public List<ChatMessage> messages(SemanticExtractionCommand command) {
        try {
            return List.of(ChatMessage.user("SOURCE_SNAPSHOT_JSON\n"
                    + mapper.writeValueAsString(Map.of("curriculum", command.curriculum(),
                    "snapshot", command.snapshot()))));
        } catch (Exception e) {
            throw new IllegalArgumentException("extraction prompt를 만들 수 없습니다.", e);
        }
    }
}
