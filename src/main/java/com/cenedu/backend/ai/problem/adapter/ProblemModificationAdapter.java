package com.cenedu.backend.ai.problem.adapter;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.authoring.port.ProblemModificationPort;
import com.cenedu.backend.domain.problem.authoring.validation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 확정 수정 계획을 공통 LlmClient로 실행하고 S1 후보로 반환한다. */
@Component
public class ProblemModificationAdapter implements ProblemModificationPort {
    private static final Logger log = LoggerFactory.getLogger(ProblemModificationAdapter.class);
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
            String response = llmClient.completeStructured(promptStrategy.create(command),
                    List.of(ChatMessage.user("확정된 수정 계획을 실행하라.")),
                    ProblemStructuredOutputSchemas.CANDIDATE).text();
            ProblemGenerationOutput output = withProtectedBaseValues(command,
                    objectMapper.readValue(response, ProblemGenerationOutput.class));
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
            log.warn("문제 수정 후보 변환 실패 — requestId={}, action={}, errorType={}, message={}",
                    command.requestId(), command.plan().action(),
                    exception.getClass().getSimpleName(), safeMessage(exception));
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("문제 수정 결과를 해석할 수 없습니다.", exception);
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "(no-message)";
        return message.replaceAll("\\s+", " ");
    }

    /**
     * 모델이 수정 대상이 아닌 필드를 비워도 매퍼가 병합 전에 실패하지 않게 한다.
     * 보호 영역은 애초에 기준 Snapshot 값으로 대체하므로 모델의 변조가 병합기까지 전파되지 않는다.
     */
    private ProblemGenerationOutput withProtectedBaseValues(
            ProblemModificationCommand command,
            ProblemGenerationOutput output
    ) {
        QuestionSnapshotV1 base = command.baseSnapshot();
        ProblemEditExecutionPlan plan = command.plan();
        boolean bodyEditable = editable(plan, EditTargetType.QUESTION_BODY)
                || editable(plan, EditTargetType.CONTENT_BLOCK);
        return new ProblemGenerationOutput(
                bodyEditable ? output.question() : firstQuestionText(base),
                bodyEditable ? output.contentBlocks() : contentBlocks(base),
                editable(plan, EditTargetType.CHOICE) ? output.choices() : choices(base),
                editable(plan, EditTargetType.STEP) ? output.steps() : steps(base),
                editable(plan, EditTargetType.ANSWER_UNIT) ? output.answerUnits() : answers(base),
                editable(plan, EditTargetType.EXPLANATION) ? output.explanation() : base.explanation(),
                editable(plan, EditTargetType.LEARNING_GUIDE)
                        ? output.learningGuide() : learningGuide(base),
                editable(plan, EditTargetType.RUBRIC_ITEM)
                        ? output.rubricItems() : rubrics(base),
                output.assets());
    }

    private boolean editable(ProblemEditExecutionPlan plan, EditTargetType type) {
        return java.util.stream.Stream.concat(
                        plan.requestedTargets().stream(), plan.dependentTargets().stream())
                .anyMatch(target -> target.targetType() == type);
    }

    private String firstQuestionText(QuestionSnapshotV1 base) {
        return base.contentBlocks().stream()
                .filter(block -> block.blockKind() == SnapshotBlockKind.TEXT)
                .map(SnapshotContentBlock::text)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse("문제");
    }

    private List<ProblemGenerationOutput.ContentBlockOutput> contentBlocks(QuestionSnapshotV1 base) {
        return base.contentBlocks().stream().map(block ->
                new ProblemGenerationOutput.ContentBlockOutput(block.blockKind().name(),
                        block.text(), block.assetRef(), block.markup())).toList();
    }

    private List<ProblemGenerationOutput.ChoiceOutput> choices(QuestionSnapshotV1 base) {
        return base.choices().stream()
                .map(choice -> new ProblemGenerationOutput.ChoiceOutput(choice.content()))
                .toList();
    }

    private List<ProblemGenerationOutput.StepOutput> steps(QuestionSnapshotV1 base) {
        return base.steps().stream().map(step -> new ProblemGenerationOutput.StepOutput(
                step.label(), step.segments().stream().map(segment ->
                        new ProblemGenerationOutput.SegmentOutput(segment.type().name(),
                                segment.text(), logicalIndex(segment.unitKey(), "B"))).toList()))
                .toList();
    }

    private List<ProblemGenerationOutput.AnswerUnitOutput> answers(QuestionSnapshotV1 base) {
        return base.answerUnits().stream().map(unit ->
                new ProblemGenerationOutput.AnswerUnitOutput(
                        logicalIndex(unit.stepKey(), "ST"), unit.answerRaw(),
                        unit.compareMethod().name(),
                        unit.diagnosticType() == null ? null : unit.diagnosticType().name(),
                        unit.displayUnit())).toList();
    }

    private ProblemGenerationOutput.LearningGuideOutput learningGuide(QuestionSnapshotV1 base) {
        SnapshotLearningGuide guide = base.learningGuide();
        return new ProblemGenerationOutput.LearningGuideOutput(
                guide.conceptTitle(), guide.summary(), guide.keyPoints());
    }

    private List<ProblemGenerationOutput.RubricOutput> rubrics(QuestionSnapshotV1 base) {
        return base.rubricItems().stream().map(rubric ->
                new ProblemGenerationOutput.RubricOutput(
                        rubric.criterion(), rubric.weightPercent())).toList();
    }

    /** B1/ST1 형태의 1부터 시작하는 키를 모델 계약의 0부터 인덱스로 되돌린다. */
    private Integer logicalIndex(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) return null;
        try {
            return Integer.parseInt(key.substring(prefix.length())) - 1;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
