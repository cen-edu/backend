package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.*;
import com.cenedu.backend.ai.problem.adapter.*;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.validation.*;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
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
    private final ProblemImageGenerationLoop imageGenerationLoop;

    public NonSemanticProblemGenerationPipeline(LlmClient client, ObjectProvider<ObjectMapper> mapper, ProblemGenerationPromptFactory prompts, ProblemGenerationOutputMapper output, SnapshotStructuralValidator structural, SnapshotNormalizedValidator normalized) {
        this(client, mapper, prompts, output, structural, normalized,
                new ProblemImageGenerationLoop(List.of(),
                        new com.cenedu.backend.domain.problem.authoring.visual.VisualSnapshotConsistencyValidator()));
    }

    /** 필요한 이미지 종류의 생성 전략을 실행하는 공통 루프를 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired
    public NonSemanticProblemGenerationPipeline(LlmClient client, ObjectProvider<ObjectMapper> mapper,
            ProblemGenerationPromptFactory prompts, ProblemGenerationOutputMapper output,
            SnapshotStructuralValidator structural, SnapshotNormalizedValidator normalized,
            ProblemImageGenerationLoop imageGenerationLoop) {
        this.client = client;
        this.mapper = mapper.getIfAvailable(ObjectMapper::new);
        this.prompts = prompts;
        this.output = output;
        this.structural = structural;
        this.normalized = normalized;
        this.imageGenerationLoop = imageGenerationLoop;
    }

    /**
     * 최대 3회, 직전 시도의 검증 위반을 프롬프트에 실어(findings feedback) 내용 교정 재시도한다.
     *
     * <p>semantic 파이프라인과 같은 전략이다 — 기존에는 1회만 호출하고 실패를 그대로 던져,
     * worker가 근거 없이 통째로 재생성하며 같은 구조 실수(예: BLANK↔answerUnit 개수 불일치)를
     * 반복했다. 이제 무엇이 왜 틀렸는지 모델에게 알려 교정을 유도한다.
     *
     * <p>전송·예산 계층 실패는 내용 문제가 아니므로 재시도하지 않고 그대로 던져 상위 계층이 다룬다.
     * 3회를 소진하면 {@link ErrorCode#PROBLEM_GENERATION_RETRY_EXHAUSTED}로 던져, worker가 같은
     * 실패를 바깥에서 또 반복하지 않게 한다.
     */
    public ProblemCandidateDraft generate(ProblemGenerationCommand command) {
        List<String> findings = List.of();
        ProblemGenerationOutput previousCandidate = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                ProblemGenerationCommand effectiveCommand = correctionCommand(
                        command, previousCandidate, findings);
                var p = prompts.create(effectiveCommand, previousCandidate, findings);
                String json = client.completeStructured(p.systemPrompt(), p.messages(),
                        ProblemStructuredOutputSchemas.CANDIDATE).text();
                ProblemGenerationOutput out = mapper.readValue(json, ProblemGenerationOutput.class);
                previousCandidate = out;
                validateBankCompatibleGenerationShape(effectiveCommand, out);
                var candidate = output.map(effectiveCommand, out);
                structural.validate(candidate.snapshot());
                normalized.validate(candidate.snapshot());
                return imageGenerationLoop.generate(candidate, out, effectiveCommand);
            } catch (RuntimeException e) {
                // 전송·예산 오류는 LlmClient가 이미 재시도한 인프라 실패다. 내용 교정 대상이 아니다.
                if (isInfrastructureFailure(e)) throw e;
                findings = violationMessages(e);
            } catch (Exception e) {
                // JSON 파싱 등 형식 오류 — 내용 문제로 보고 위반을 피드백해 재시도한다.
                findings = List.of(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        throw new BusinessException(ErrorCode.PROBLEM_GENERATION_RETRY_EXHAUSTED,
                "non-semantic generation retry exhausted: " + findings);
    }

    /** 그래프 누락 위반 뒤에는 다음 교정 시도를 좌표그래프 필수 계약으로 승격한다. */
    private ProblemGenerationCommand correctionCommand(ProblemGenerationCommand original,
                                                       ProblemGenerationOutput previousCandidate,
                                                       List<String> findings) {
        boolean visualDependency = findings.stream()
                .anyMatch(finding -> finding != null && finding.contains("visualDependency"));
        if (!visualDependency || !referencesCoordinateGraph(previousCandidate)) {
            return original;
        }

        GenerationSpecification specification = original.specification();
        GenerationSpecification corrected = new GenerationSpecification(
                specification.questionType(), specification.difficulty(),
                specification.targetEvaluationArea(), specification.targetDiagnosticTypes(),
                specification.requiresSolutionStructure(),
                new VisualGenerationRequirement(
                        VisualGenerationMode.REQUIRED, VisualReferenceKind.COORDINATE_GRAPH));
        return new ProblemGenerationCommand(original.requestId(), original.retrievalRequestId(),
                original.purpose(), corrected, original.curriculum(), original.references(),
                original.conceptEvidence(), original.personalizedEvidence(), original.editInstruction());
    }

    /** 지시형 그래프 문구가 있는 후보만 자동 승격하고, 표·도형은 텍스트 교정에 맡긴다. */
    private boolean referencesCoordinateGraph(ProblemGenerationOutput candidate) {
        if (candidate == null) return false;
        if (containsGraph(candidate.question())) return true;
        return candidate.contentBlocks() != null && candidate.contentBlocks().stream()
                .filter(java.util.Objects::nonNull)
                .map(ProblemGenerationOutput.ContentBlockOutput::text)
                .anyMatch(this::containsGraph);
    }

    private boolean containsGraph(String value) {
        return value != null && (value.contains("그래프") || value.contains("좌표평면"));
    }

    /** 원본 문제은행의 지배적인 객관식 계약을 신규 AI 생성에만 적용한다. */
    private void validateBankCompatibleGenerationShape(ProblemGenerationCommand command,
                                                       ProblemGenerationOutput candidate) {
        if (command.specification().questionType()
                != com.cenedu.backend.global.common.enums.QuestionType.MULTIPLE_CHOICE) {
            return;
        }
        int choiceCount = candidate.choices() == null ? 0 : candidate.choices().size();
        if (choiceCount != 5) {
            throw new SnapshotValidationException(List.of(
                    "choices: 문제은행 객관식 기준에 따라 정확히 5개의 보기가 필요합니다. actual="
                            + choiceCount));
        }
    }

    /** 내용 교정으로 회복할 수 없는 전송·예산 계층 실패인지 판정한다. */
    private boolean isInfrastructureFailure(RuntimeException exception) {
        if (!(exception instanceof BusinessException business)) return false;
        ErrorCode code = business.getErrorCode();
        return code == ErrorCode.AI_CLIENT_CALL_FAILED
                || code == ErrorCode.AI_CLIENT_EMPTY_RESPONSE
                || code == ErrorCode.AI_CLIENT_CALL_BUDGET_EXHAUSTED;
    }

    /** 검증 위반을 다음 시도 프롬프트에 실을 문자열 목록으로 만든다. */
    private List<String> violationMessages(RuntimeException exception) {
        if (exception instanceof SnapshotValidationException validation) {
            return validation.violations().stream().limit(10).map(this::truncate).toList();
        }
        return List.of(truncate(exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage()));
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
