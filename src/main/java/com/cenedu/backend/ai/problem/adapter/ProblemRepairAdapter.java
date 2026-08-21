package com.cenedu.backend.ai.problem.adapter;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmUseCase;
import com.cenedu.backend.domain.problem.authoring.port.ProblemRepairPort;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairCommand;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairDelta;
import com.cenedu.backend.domain.problem.authoring.repair.RepairTarget;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 묶음 Repair를 한 번의 구조화 LLM 호출로 수행한다. */
@Component
public class ProblemRepairAdapter implements ProblemRepairPort {
    private static final long REPAIR_SEED = 20260821L;
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["replacements","rationale"],"properties":{
              "replacements":{"type":"object","additionalProperties":false,"properties":{
                "CONTENT":{},"CHOICES":{},"ANSWERS":{},"STEPS":{},"EXPLANATION":{},"RUBRIC":{},"LEARNING_GUIDE":{},"ASSET":{}}},
              "rationale":{"type":"string"}}}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ProblemRepairPromptFactory promptFactory;

    public ProblemRepairAdapter(LlmClient llmClient, ObjectMapper objectMapper,
                                ProblemRepairPromptFactory promptFactory) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptFactory = promptFactory;
    }

    @Override
    public ProblemRepairDelta repair(ProblemRepairCommand command) {
        JsonNode root = objectMapper.readTree(llmClient.completeStructured(
                promptFactory.systemPrompt(), promptFactory.messages(command), REPAIR_SEED,
                LlmUseCase.VERIFICATION, SCHEMA).text());
        JsonNode replacements = root.path("replacements");
        if (!replacements.isObject()) {
            throw new IllegalArgumentException("Repair 응답의 replacements가 객체가 아닙니다.");
        }
        Map<RepairTarget, Object> result = new EnumMap<>(RepairTarget.class);
        for (String field : replacements.propertyNames()) {
            RepairTarget target;
            try {
                target = RepairTarget.valueOf(field);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("허용되지 않은 Repair 대상: " + field, exception);
            }
            if (!command.plan().targets().contains(target)) {
                throw new IllegalArgumentException("계획에 없는 Repair 대상이 응답에 포함되었습니다: " + field);
            }
            result.put(target, replacements.get(field));
        }
        return new ProblemRepairDelta(result, root.path("rationale").asString(""));
    }
}
