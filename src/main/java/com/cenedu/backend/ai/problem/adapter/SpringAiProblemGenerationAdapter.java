package com.cenedu.backend.ai.problem.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** 공통 LlmClient를 통해 문제 생성 결과를 S1 후보로 변환하는 시스템 Adapter다. */
@Component
public class SpringAiProblemGenerationAdapter implements ProblemGenerationPort {
    private static final Logger log = LoggerFactory.getLogger(SpringAiProblemGenerationAdapter.class);
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ProblemGenerationPromptFactory promptFactory;
    private final ProblemGenerationOutputMapper outputMapper;
    private final SnapshotStructuralValidator structuralValidator;
    private final SnapshotNormalizedValidator normalizedValidator;

    public SpringAiProblemGenerationAdapter(LlmClient llmClient, ObjectProvider<ObjectMapper> objectMapper,
            ProblemGenerationPromptFactory promptFactory, ProblemGenerationOutputMapper outputMapper,
            SnapshotStructuralValidator structuralValidator, SnapshotNormalizedValidator normalizedValidator) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper.getIfAvailable(ObjectMapper::new);
        this.promptFactory = promptFactory;
        this.outputMapper = outputMapper;
        this.structuralValidator = structuralValidator;
        this.normalizedValidator = normalizedValidator;
    }

    /** 한 문항을 생성하고 서버가 소유하는 requestId를 후보에 부여한다. */
    @Override
    public ProblemCandidateDraft generate(ProblemGenerationCommand command) {
        try {
            String response = llmClient.completeStructured(promptFactory.create(command),
                    List.of(new ChatMessage(ChatMessage.Role.USER, "조건에 맞는 문제 JSON을 생성하라.")),
                    ProblemStructuredOutputSchemas.CANDIDATE).text();
            ProblemGenerationOutput output = objectMapper.readValue(response, ProblemGenerationOutput.class);
            ProblemCandidateDraft candidate = outputMapper.map(command, output);
            structuralValidator.validate(candidate.snapshot());
            normalizedValidator.validate(candidate.snapshot());
            return candidate;
        } catch (Exception exception) {
            log.warn("문제 생성 후보 변환 실패 — requestId={}, questionType={}, errorType={}, message={}",
                    command.requestId(), command.specification().questionType(),
                    exception.getClass().getSimpleName(), safeMessage(exception));
            if (exception instanceof IllegalArgumentException) throw (IllegalArgumentException) exception;
            throw new IllegalStateException("문제 생성 결과를 해석할 수 없습니다.", exception);
        }
    }

    /** 예외 메시지에서 줄바꿈을 제거해 로그 한 줄로 남긴다. */
    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "(no-message)";
        return message.replaceAll("\\s+", " ");
    }
}
