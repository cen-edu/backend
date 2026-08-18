package com.cenedu.backend.ai.problem.adapter;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemModificationPort;
import com.cenedu.backend.domain.problem.authoring.validation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 확정 수정 계획을 공통 LlmClient로 실행하고 S1 후보로 반환한다. */
@Component
public class ProblemModificationAdapter implements ProblemModificationPort {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ModificationPromptStrategy promptStrategy;
    private final ProblemGenerationOutputMapper outputMapper;
    private final SnapshotStructuralValidator structuralValidator;
    private final SnapshotNormalizedValidator normalizedValidator;
    private final ProblemModificationSnapshotMerger snapshotMerger;

    public ProblemModificationAdapter(LlmClient llmClient, ObjectProvider<ObjectMapper> objectMapper,
            ModificationPromptStrategy promptStrategy, ProblemGenerationOutputMapper outputMapper,
            SnapshotStructuralValidator structuralValidator, SnapshotNormalizedValidator normalizedValidator,
            ProblemModificationSnapshotMerger snapshotMerger) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper.getIfAvailable(ObjectMapper::new);
        this.promptStrategy = promptStrategy;
        this.outputMapper = outputMapper;
        this.structuralValidator = structuralValidator;
        this.normalizedValidator = normalizedValidator;
        this.snapshotMerger = snapshotMerger;
    }

    /** 수정 JSON을 생성하고 command의 requestId와 AI_MODIFY 출처를 적용한다. */
    @Override
    public ProblemCandidateDraft modify(ProblemModificationCommand command) {
        try {
            String response = llmClient.complete(promptStrategy.create(command),
                    List.of(ChatMessage.user("확정된 수정 계획을 실행하라."))).text();
            ProblemGenerationOutput output = objectMapper.readValue(response, ProblemGenerationOutput.class);
            var requested = command.plan().requestedSpecification();
            ProblemCandidateDraft mapped = outputMapper.map(
                    new com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand(
                            command.requestId(),
                            com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose.PERSONALIZED_APPLICATION,
                            new com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification(
                                    requested != null && requested.questionType() != null
                                            ? requested.questionType() : command.baseSnapshot().metadata().questionType(),
                                    requested != null && requested.difficulty() != null
                                            ? requested.difficulty() : command.baseSnapshot().metadata().difficulty(),
                                    command.baseSnapshot().metadata().evaluationArea(), List.of()),
                            new com.cenedu.backend.domain.problem.authoring.generation.CurriculumContext(
                                    command.baseSnapshot().metadata().subUnitId(), null, null, null, null, null),
                            List.of(), List.of()), output);
            var mergedSnapshot = snapshotMerger.merge(command.plan(), command.baseSnapshot(), mapped.snapshot());
            ProblemCandidateDraft candidate = new ProblemCandidateDraft(command.requestId(), mergedSnapshot,
                    mapped.assetPlans(), new CandidateProvenance(CandidateSourceType.AI_MODIFY,
                    null, List.of()));
            structuralValidator.validate(candidate.snapshot());
            normalizedValidator.validate(candidate.snapshot());
            return candidate;
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("문제 수정 결과를 해석할 수 없습니다.", exception);
        }
    }
}
