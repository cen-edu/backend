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

/**
 * semantic model 없이 LLM이 최종 문항 JSON을 한 번에 만드는 경로다.
 *
 * <p>{@code PROBLEM_SEMANTIC_AUTHORING_ENABLED=false}(기본값)일 때는 이 경로가 유일한
 * 생성 경로이고, semantic authoring이 켜져 있어도 시각 자료가 필요 없는 문항
 * ({@code VisualGenerationMode.NONE})은 계속 이 경로로 라우팅된다. "예전 방식"이 아니라
 * "semantic model이 필요 없을 때 쓰는 경로"다.
 */
@Component
public final class NonSemanticProblemGenerationPipeline {
    private final LlmClient client;
    private final ObjectMapper mapper;
    private final ProblemGenerationPromptFactory prompts;
    private final ProblemGenerationOutputMapper output;
    private final SnapshotStructuralValidator structural;
    private final SnapshotNormalizedValidator normalized;

    public NonSemanticProblemGenerationPipeline(LlmClient client, ObjectProvider<ObjectMapper> mapper, ProblemGenerationPromptFactory prompts, ProblemGenerationOutputMapper output, SnapshotStructuralValidator structural, SnapshotNormalizedValidator normalized) {
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
