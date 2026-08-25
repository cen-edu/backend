package com.cenedu.backend.ai.problem.adapter;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.authoring.port.ProblemModificationPort;
import com.cenedu.backend.domain.problem.authoring.validation.*;
import com.cenedu.backend.domain.problem.authoring.visual.*;
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
    private final ProblemImageGenerationLoop imageGenerationLoop;

    public ProblemModificationAdapter(LlmClient llmClient, ObjectProvider<ObjectMapper> objectMapper,
            ModificationPromptStrategy promptStrategy, ProblemGenerationOutputMapper outputMapper,
            SnapshotStructuralValidator structuralValidator, SnapshotNormalizedValidator normalizedValidator,
            ProblemModificationSnapshotMerger snapshotMerger,
            ObjectProvider<ProblemImageGenerationLoop> imageGenerationLoop) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper.getIfAvailable(ObjectMapper::new);
        this.promptStrategy = promptStrategy;
        this.outputMapper = outputMapper;
        this.structuralValidator = structuralValidator;
        this.normalizedValidator = normalizedValidator;
        this.snapshotMerger = snapshotMerger;
        this.imageGenerationLoop = imageGenerationLoop.getIfAvailable();
    }

    /** 수정 JSON을 생성하고 command의 requestId와 AI_MODIFY 출처를 적용한다. */
    @Override
    public ProblemCandidateDraft modify(ProblemModificationCommand command) {
        try {
            java.util.Set<EditTargetType> targets = java.util.stream.Stream.concat(
                    command.plan().requestedTargets().stream(), command.plan().dependentTargets().stream())
                    .map(com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef::targetType)
                    .collect(java.util.stream.Collectors.toSet());
            String response = llmClient.completeStructured(promptStrategy.create(command),
                    List.of(ChatMessage.user("확정된 수정 계획을 실행하라.")),
                    ProblemStructuredOutputSchemas.modificationDeltaFor(targets)).text();
            ProblemGenerationOutput modelOutput = objectMapper.readValue(response, ProblemGenerationOutput.class);
            boolean regenerateVisual = shouldRegenerateCoordinateGraph(command, modelOutput);
            ProblemGenerationOutput output = withProtectedBaseValues(
                    command, modelOutput, regenerateVisual);
            var requested = command.plan().requestedSpecification();
            ProblemGenerationCommand generationCommand =
                    new ProblemGenerationCommand(
                            command.requestId(),
                            null,
                            GenerationPurpose.PERSONALIZED_APPLICATION,
                            new GenerationSpecification(
                                    requested != null && requested.questionType() != null
                                            ? requested.questionType() : command.baseSnapshot().metadata().questionType(),
                                    requested != null && requested.difficulty() != null
                                            ? requested.difficulty() : command.baseSnapshot().metadata().difficulty(),
                                    command.baseSnapshot().metadata().evaluationArea(), List.of(), false,
                                    regenerateVisual
                                            ? new VisualGenerationRequirement(VisualGenerationMode.REQUIRED,
                                                    VisualReferenceKind.COORDINATE_GRAPH)
                                            : VisualGenerationRequirement.none()),
                            new CurriculumScope(
                                "2022_REVISED", "MIDDLE", 1, null, null,
                                command.baseSnapshot().metadata().subUnitId(), "대단원", "중단원", "소단원"),
                            List.of(), List.of());
            ProblemCandidateDraft mapped = outputMapper.map(generationCommand, output);
            if (regenerateVisual) {
                if (imageGenerationLoop == null) {
                    throw new IllegalStateException("그래프 수정에 필요한 이미지 생성 루프가 없습니다.");
                }
                mapped = imageGenerationLoop.generate(mapped, output, generationCommand);
            }
            var mergedSnapshot = snapshotMerger.merge(
                    command.plan(), command.baseSnapshot(), mapped.snapshot());
            if (regenerateVisual && command.plan().action() != EditAction.REPLACE) {
                mergedSnapshot = withRegeneratedAssets(mergedSnapshot, mapped.snapshot());
            }
            var assetPlans = regenerateVisual || command.baseAssetPlans().isEmpty()
                    ? mapped.assetPlans() : command.baseAssetPlans();
            ProblemCandidateDraft candidate = new ProblemCandidateDraft(
                    command.requestId(), mergedSnapshot, assetPlans,
                    command.baseSemanticModel(),
                    new CandidateProvenance(CandidateSourceType.AI_MODIFY, null, List.of()));
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
     *
     * <p>action이 REPLACE면 requestedTargets가 항상 WHOLE_QUESTION 하나뿐이라, 필드별
     * targetType 일치를 요구하는 {@link #editable} 검사로는 어떤 구체 필드도 editable로
     * 판정되지 않는다. {@link ProblemModificationSnapshotMerger#merge}가 REPLACE를
     * 무조건 통과시키는 것과 같은 의도로, REPLACE일 때는 이 필드들도 모델 출력을 그대로 쓴다.
     *
     * <p>assets는 예외다. 수정이 좌표그래프에 영향을 주면 기존 FIGURE를 제거해 이미지 생성
     * 루프가 새 구조화 자산을 붙이게 하고, 영향이 없으면 기준 Snapshot의 자산을 유지한다.
     */
    private ProblemGenerationOutput withProtectedBaseValues(
            ProblemModificationCommand command,
            ProblemGenerationOutput output,
            boolean regenerateVisual
    ) {
        QuestionSnapshotV1 base = command.baseSnapshot();
        ProblemEditExecutionPlan plan = command.plan();
        boolean replace = plan.action() == EditAction.REPLACE;
        boolean bodyEditable = replace || editable(plan, EditTargetType.QUESTION_BODY)
                || editable(plan, EditTargetType.CONTENT_BLOCK);
        List<ProblemGenerationOutput.ContentBlockOutput> selectedBlocks = bodyEditable
                ? output.contentBlocks() : contentBlocks(base);
        if (regenerateVisual) {
            selectedBlocks = selectedBlocks == null ? List.of() : selectedBlocks.stream()
                    .filter(block -> !"FIGURE".equals(block.blockKind()))
                    .toList();
        }
        return new ProblemGenerationOutput(
                bodyEditable ? output.question() : firstQuestionText(base),
                selectedBlocks,
                replace || editable(plan, EditTargetType.CHOICE) ? output.choices() : choices(base),
                replace || editable(plan, EditTargetType.STEP) ? output.steps() : steps(base),
                replace || editable(plan, EditTargetType.ANSWER_UNIT) ? output.answerUnits() : answers(base),
                replace || editable(plan, EditTargetType.EXPLANATION) ? output.explanation() : base.explanation(),
                replace || editable(plan, EditTargetType.LEARNING_GUIDE)
                        ? output.learningGuide() : learningGuide(base),
                replace || editable(plan, EditTargetType.RUBRIC_ITEM)
                        ? output.rubricItems() : rubrics(base),
                regenerateVisual ? List.of() : assets(base), regenerateVisual,
                regenerateVisual ? VisualReferenceKind.COORDINATE_GRAPH.name() : null,
                regenerateVisual ? visualDescription(command, output) : null);
    }

    boolean shouldRegenerateCoordinateGraph(
            ProblemModificationCommand command,
            ProblemGenerationOutput output
    ) {
        if (command.baseSnapshot().assets().isEmpty()) return false;
        boolean affectsVisual = command.plan().action() == EditAction.REPLACE
                || editable(command.plan(), EditTargetType.ASSET)
                || editable(command.plan(), EditTargetType.QUESTION_BODY)
                || editable(command.plan(), EditTargetType.CONTENT_BLOCK);
        boolean missingReusablePlan = command.baseAssetPlans().isEmpty();
        if (!affectsVisual && !missingReusablePlan) return false;
        boolean structuredCoordinateGraph = command.baseAssetPlans().stream()
                .anyMatch(plan -> plan.specification() != null
                        && plan.specification().diagramSpec() != null
                        && plan.specification().diagramSpec().kind()
                        == com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind.COORDINATE_GRAPH);
        return structuredCoordinateGraph || containsCoordinateGraphHint(command, output);
    }

    private boolean containsCoordinateGraphHint(
            ProblemModificationCommand command,
            ProblemGenerationOutput output
    ) {
        String context = (firstQuestionText(command.baseSnapshot()) + " "
                + (output.question() == null ? "" : output.question()) + " "
                + command.plan().instructions()).toLowerCase(java.util.Locale.ROOT);
        return java.util.stream.Stream.of("좌표", "그래프", "기울기", "직선", "정비례", "반비례",
                        "coordinate", "slope", "y=")
                .anyMatch(context::contains);
    }

    private String visualDescription(
            ProblemModificationCommand command,
            ProblemGenerationOutput output
    ) {
        String question = output.question() == null || output.question().isBlank()
                ? firstQuestionText(command.baseSnapshot()) : output.question();
        String description = "수정 지시=" + command.plan().instructions() + "; 수정된 문제=" + question;
        return description.length() > 1500 ? description.substring(0, 1500) : description;
    }

    private QuestionSnapshotV1 withRegeneratedAssets(
            QuestionSnapshotV1 merged,
            QuestionSnapshotV1 generated
    ) {
        return new QuestionSnapshotV1(merged.schemaVersion(), merged.metadata(), merged.contentBlocks(),
                generated.assets(), merged.choices(), merged.steps(), merged.answerUnits(),
                merged.explanation(), merged.learningGuide(), merged.rubricItems());
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

    /** 자산을 수정하지 않는 편집에서는 기존 이미지 자산을 LLM 응답에 다시 포함한다. */
    private List<ProblemGenerationOutput.AssetOutput> assets(QuestionSnapshotV1 base) {
        return base.assets().stream()
                .map(asset -> new ProblemGenerationOutput.AssetOutput(
                        "FIGURE", "SVG", asset.altText(),
                        asset.altText() == null || asset.altText().isBlank()
                                ? "기존 이미지 자산을 그대로 유지한다."
                                : asset.altText(),
                        List.of(), List.of(), null))
                .toList();
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
