package com.cenedu.backend.ai.problem.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.problem.authoring.asset.*;
import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.entity.enums.*;
import com.cenedu.backend.global.common.enums.CompareMethod;
import org.springframework.stereotype.Component;

/** 유형별 AI 출력을 서버 소유 키와 메타데이터가 적용된 S1 후보로 변환한다. */
@Component
public class ProblemGenerationOutputMapper {
    /** command의 메타데이터를 정본으로 사용해 후보와 자산 계획을 만든다. */
    public ProblemCandidateDraft map(ProblemGenerationCommand command, ProblemGenerationOutput output) {
        if (output == null || output.learningGuide() == null) {
            throw new IllegalArgumentException("문제 생성 출력과 learningGuide는 필수입니다.");
        }
        List<SnapshotAssetReference> assets = mapAssets(output.assets());
        List<SnapshotContentBlock> blocks = mapBlocks(output, assets);
        List<SnapshotStep> steps = mapSteps(output.steps());
        SnapshotMetadata metadata = new SnapshotMetadata(command.specification().questionType(),
                presentation(blocks), command.specification().difficulty(), command.curriculumContext().subUnitId(),
                null, command.specification().targetEvaluationArea(), null);
        QuestionSnapshotV1 snapshot = new QuestionSnapshotV1(QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                metadata, blocks, assets, mapChoices(output.choices()), steps,
                mapAnswers(output.answerUnits(), steps), required(output.explanation(), "explanation"),
                new SnapshotLearningGuide(required(output.learningGuide().conceptTitle(), "conceptTitle"),
                        required(output.learningGuide().summary(), "learningGuide.summary"),
                        copy(output.learningGuide().keyPoints())), mapRubrics(output.rubricItems()));
        return new ProblemCandidateDraft(command.requestId(), snapshot, mapAssetPlans(output.assets()),
                new CandidateProvenance(CandidateSourceType.AI_GENERATE, null,
                        command.references() == null ? List.of() : command.references().stream()
                                .map(reference -> reference.sourceQuestionId()).toList()));
    }

    private List<SnapshotContentBlock> mapBlocks(ProblemGenerationOutput output,
                                                  List<SnapshotAssetReference> assets) {
        List<ProblemGenerationOutput.ContentBlockOutput> source = copy(output.contentBlocks());
        if (source.isEmpty()) return List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                required(output.question(), "question"), null, null));
        List<SnapshotContentBlock> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            var block = source.get(index);
            // 모델이 일반 발문 블록의 blockKind를 생략하는 경우가 있어 TEXT로 보정한다.
            // 자산/마크업 블록까지 무조건 TEXT로 바꾸면 구조가 왜곡될 수 있으므로
            // 명시된 값은 그대로 검증한다.
            String blockKind = block.blockKind();
            SnapshotBlockKind kind = blockKind == null || blockKind.isBlank()
                    ? SnapshotBlockKind.TEXT
                    : SnapshotBlockKind.valueOf(blockKind);
            String assetRef = block.assetRef();
            if (kind == SnapshotBlockKind.FIGURE && assetRef == null && assets.size() == 1) assetRef = "F1";
            result.add(new SnapshotContentBlock("CB" + (index + 1), kind, index,
                    block.text(), assetRef, block.markup()));
        }
        return List.copyOf(result);
    }

    private List<SnapshotChoice> mapChoices(List<ProblemGenerationOutput.ChoiceOutput> source) {
        List<ProblemGenerationOutput.ChoiceOutput> values = copy(source); List<SnapshotChoice> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) result.add(new SnapshotChoice("C" + (i + 1), i,
                required(values.get(i).content(), "choice.content")));
        return List.copyOf(result);
    }

    private List<SnapshotStep> mapSteps(List<ProblemGenerationOutput.StepOutput> source) {
        List<ProblemGenerationOutput.StepOutput> values = copy(source); List<SnapshotStep> result = new ArrayList<>();
        int nextBlankIndex = 0;
        for (int stepIndex = 0; stepIndex < values.size(); stepIndex++) {
            var step = values.get(stepIndex);
            List<SnapshotSegment> segments = new ArrayList<>();
            for (var segment : copy(step.segments())) {
                SnapshotSegmentType type = SnapshotSegmentType.valueOf(
                        required(segment.type(), "segment.type"));
                String unitKey = switch (type) {
                    // 생성 모델이 같은 answerUnitIndex를 반복하거나 TEXT에도 인덱스를 넣는 경우가 있다.
                    // BLANK는 화면에 나타나는 순서가 곧 answerUnits 순서라는 생성 계약으로 정규화한다.
                    case BLANK -> "B" + (++nextBlankIndex);
                    case ANSWER_REF -> segment.answerUnitIndex() == null
                            ? null : "B" + (segment.answerUnitIndex() + 1);
                    case TEXT -> null;
                };
                segments.add(new SnapshotSegment(type, segment.text(), unitKey));
            }
            result.add(new SnapshotStep("ST" + (stepIndex + 1), stepIndex,
                    required(step.label(), "step.label"), List.copyOf(segments)));
        }
        return List.copyOf(result);
    }

    private List<SnapshotAnswerUnit> mapAnswers(
            List<ProblemGenerationOutput.AnswerUnitOutput> source,
            List<SnapshotStep> steps
    ) {
        List<ProblemGenerationOutput.AnswerUnitOutput> values = copy(source); List<SnapshotAnswerUnit> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            var unit = values.get(index); boolean step = !steps.isEmpty();
            CompareMethod method = CompareMethod.valueOf(required(unit.compareMethod(), "compareMethod"));
            String normalized = switch (method) {
                case VALUE, EXACT, SET -> required(unit.answerRaw(), "answerRaw").trim();
                case CHOICE, SUBST, RUBRIC -> null;
            };
            String unitKey = step ? "B" + (index + 1) : "MAIN";
            result.add(new SnapshotAnswerUnit(step ? "B" + (index + 1) : "MAIN",
                    step ? stepKeyFor(steps, unitKey) : null, index, unit.answerRaw(), normalized,
                    method,
                    unit.diagnosticType() == null ? null : DiagnosticType.valueOf(unit.diagnosticType()), unit.displayUnit()));
        }
        return List.copyOf(result);
    }

    /** BLANK에 서버 키를 부여한 결과를 정본으로 answerUnit의 소속 단계를 찾는다. */
    private String stepKeyFor(List<SnapshotStep> steps, String unitKey) {
        return steps.stream()
                .filter(step -> step.segments().stream()
                        .anyMatch(segment -> segment.type() == SnapshotSegmentType.BLANK
                                && unitKey.equals(segment.unitKey())))
                .map(SnapshotStep::stepKey)
                .findFirst()
                .orElse(null);
    }

    private List<SnapshotRubricItem> mapRubrics(List<ProblemGenerationOutput.RubricOutput> source) {
        List<ProblemGenerationOutput.RubricOutput> values = copy(source); List<SnapshotRubricItem> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) result.add(new SnapshotRubricItem("R" + (i + 1), i,
                required(values.get(i).criterion(), "rubric.criterion"), values.get(i).weightPercent()));
        return List.copyOf(result);
    }

    private List<SnapshotAssetReference> mapAssets(List<ProblemGenerationOutput.AssetOutput> source) {
        List<ProblemGenerationOutput.AssetOutput> values = copy(source); List<SnapshotAssetReference> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) result.add(new SnapshotAssetReference("F" + (i + 1),
                required(values.get(i).altText(), "asset.altText")));
        return List.copyOf(result);
    }

    private List<GeneratedAssetPlan> mapAssetPlans(List<ProblemGenerationOutput.AssetOutput> source) {
        List<ProblemGenerationOutput.AssetOutput> values = copy(source); List<GeneratedAssetPlan> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            var asset = values.get(i);
            result.add(new GeneratedAssetPlan("F" + (i + 1), AssetRole.valueOf(required(asset.role(), "asset.role")),
                    AssetProductionMode.STRUCTURED_RENDER, AssetOutputFormat.valueOf(required(asset.outputFormat(), "asset.outputFormat")),
                    required(asset.altText(), "asset.altText"), new AssetGenerationSpecification(1,
                    required(asset.visualDescription(), "asset.visualDescription"), copy(asset.requiredElements()),
                    copy(asset.forbiddenElements()), asset.renderData() == null ? Map.of() : Map.copyOf(asset.renderData()))));
        }
        return List.copyOf(result);
    }

    private QuestionPresentation presentation(List<SnapshotContentBlock> blocks) {
        if (blocks.stream().anyMatch(block -> block.blockKind() == SnapshotBlockKind.FIGURE)) return QuestionPresentation.WITH_FIGURE;
        if (blocks.stream().anyMatch(block -> block.blockKind() == SnapshotBlockKind.TABLE)) return QuestionPresentation.WITH_TABLE;
        return QuestionPresentation.TEXT_ONLY;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("필수 AI 필드 누락: " + field);
        return value;
    }
    private static <T> List<T> copy(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
}
