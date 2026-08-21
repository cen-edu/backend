package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.*;
import com.cenedu.backend.ai.problem.adapter.*;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.validation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class LegacyProblemGenerationPipeline {
    private final LlmClient client;
    private final ObjectMapper mapper;
    private final ProblemGenerationPromptFactory prompts;
    private final ProblemGenerationOutputMapper output;
    private final SnapshotStructuralValidator structural;
    private final SnapshotNormalizedValidator normalized;

    public LegacyProblemGenerationPipeline(LlmClient client, ObjectProvider<ObjectMapper> mapper, ProblemGenerationPromptFactory prompts, ProblemGenerationOutputMapper output, SnapshotStructuralValidator structural, SnapshotNormalizedValidator normalized) {
        this.client = client;
        this.mapper = mapper.getIfAvailable(ObjectMapper::new);
        this.prompts = prompts;
        this.output = output;
        this.structural = structural;
        this.normalized = normalized;
    }

    public ProblemCandidateDraft generate(ProblemGenerationCommand command) {
        try {
            var p = prompts.create(command);
            String json = client.completeStructured(p.systemPrompt(), p.messages(), ProblemStructuredOutputSchemas.CANDIDATE).text();
            var candidate = output.map(command, mapper.readValue(json, ProblemGenerationOutput.class));
            structural.validate(candidate.snapshot());
            normalized.validate(candidate.snapshot());
            return candidate;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("문제 생성 결과를 해석할 수 없습니다.", e);
        }
    }
}
