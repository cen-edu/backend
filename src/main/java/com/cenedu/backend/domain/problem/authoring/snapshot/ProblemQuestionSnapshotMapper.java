package com.cenedu.backend.domain.problem.authoring.snapshot;

import java.util.List;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegment;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegmentType;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotStep;
import com.cenedu.backend.domain.problem.dto.response.ProblemAnswerUnitResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemAssetResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemChoiceResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemContentBlockResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemLearningGuideResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemStepResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemStepSegmentResponse;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotRubricItem;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

/** 문제 Entity의 영속화 필드만 S1 스냅샷 계약으로 변환하는 경계 Mapper다. */
@Component
public class ProblemQuestionSnapshotMapper {

    private final ObjectMapper objectMapper;

    public ProblemQuestionSnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 문제 본체의 분류·설명과 JSON 하위 구조를 Version 저장용 스냅샷으로 만든다. */
    public QuestionSnapshotV1 toSnapshot(ProblemQuestion question) {
        if (question == null) throw new IllegalArgumentException("문제가 필요합니다.");
        String difficulty = switch (question.getDifficulty()) {
            case 1 -> "low";
            case 2 -> "mid";
            case 3 -> "high";
            default -> throw new IllegalArgumentException("지원하지 않는 난이도입니다.");
        };
        SnapshotMetadata metadata = new SnapshotMetadata(question.getQuestionType(),
                question.getPresentation(), difficulty, question.getSubUnitId(),
                question.getTopicCode(), question.getEvaluationArea(),
                question.getDerivedFrom() == null ? null : question.getDerivedFrom().getId());
        return new QuestionSnapshotV1(QuestionSnapshotV1.CURRENT_SCHEMA_VERSION, metadata,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                question.getExplanation(), null, List.of());
    }

    /** 화면용 상세 조회 결과의 하위 구조를 S1 논리 키로 변환한다. */
    public QuestionSnapshotV1 toSnapshot(ProblemQuestion question,
                                         ProblemQuestionDetailResponse detail) {
        QuestionSnapshotV1 base = toSnapshot(question);
        if (detail == null) return base;
        List<SnapshotContentBlock> blocks = new java.util.ArrayList<>();
        for (int i = 0; i < detail.contentBlocks().size(); i++) {
            ProblemContentBlockResponse block = detail.contentBlocks().get(i);
            blocks.add(new SnapshotContentBlock("CB" + (i + 1),
                    SnapshotBlockKind.valueOf(block.blockKind()), i,
                    block.text(), block.assetRef(), block.markup()));
        }
        List<SnapshotAssetReference> assets = detail.assets().stream()
                .map(asset -> new SnapshotAssetReference(asset.assetKey(), asset.altText())).toList();
        List<SnapshotChoice> choices = new java.util.ArrayList<>();
        for (int i = 0; i < detail.choices().size(); i++) {
            choices.add(new SnapshotChoice("C" + (i + 1), i, detail.choices().get(i).content()));
        }
        List<SnapshotStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < detail.steps().size(); i++) steps.add(step(detail.steps().get(i), i));
        List<SnapshotAnswerUnit> units = detail.answerUnits().stream().map(unit -> {
            String normalized = unit.compareMethod() == com.cenedu.backend.global.common.enums.CompareMethod.CHOICE
                    || unit.compareMethod() == com.cenedu.backend.global.common.enums.CompareMethod.RUBRIC
                    ? null : unit.answer();
            return new SnapshotAnswerUnit(unit.unitKey(),
                    unit.stepId() == null ? null : "ST" + (findStepIndex(detail.steps(), unit.stepId()) + 1),
                    unit.displayOrder(), unit.answer(), normalized, unit.compareMethod(),
                    unit.diagnosticType(), unit.displayUnit());
        }).toList();
        SnapshotLearningGuide guide = guide(detail.learningGuide());
        return new QuestionSnapshotV1(1, base.metadata(), blocks, assets, choices, steps, units,
                detail.explanation(), guide, List.of());
    }

    /** 문제 Entity와 하위 Entity를 S1 스냅샷으로 변환한다. */
    public QuestionSnapshotV1 toSnapshot(ProblemSnapshotSource source) {
        ProblemQuestion question = source.question();
        List<ProblemContentBlockResponse> blocks = readList(question.getContentBlocks(),
                new TypeReference<List<ProblemContentBlockResponse>>() {});
        List<ProblemStepResponse> steps = source.steps().stream().map(step -> new ProblemStepResponse(
                step.getId(), step.getDisplayOrder(), step.getLabel(),
                readList(step.getSegments(), new TypeReference<List<ProblemStepSegmentResponse>>() {}))).toList();
        List<ProblemAnswerUnitResponse> units = source.answerUnits().stream()
                .map(unit -> new ProblemAnswerUnitResponse(unit.getId(),
                        unit.getStep() == null ? null : unit.getStep().getId(), unit.getUnitKey(),
                        unit.getDisplayOrder(), unit.getLabel(), unit.getAnswerRaw(),
                        unit.getCompareMethod(), unit.getDiagnosticType(), unit.getDisplayUnit())).toList();
        List<ProblemAssetResponse> assets = source.assets().stream()
                .map(asset -> new ProblemAssetResponse(asset.getAssetKey(), asset.getRole(),
                        asset.getDisplayOrder(), null, asset.getWidthPx(), asset.getHeightPx(), asset.getAltText())).toList();
        ProblemLearningGuideResponse guide = readGuide(question.getLearningGuide());
        ProblemQuestionDetailResponse detail = new ProblemQuestionDetailResponse(question.getId(), null,
                question.getDifficulty(), question.getQuestionType(), question.getPresentation(), blocks,
                assets, source.choices().stream().map(com.cenedu.backend.domain.problem.dto.response.ProblemChoiceResponse::from).toList(),
                steps, units, question.getExplanation(), guide, question.getHintText());
        QuestionSnapshotV1 snapshot = toSnapshot(question, detail);
        List<SnapshotAnswerUnit> entityUnits = source.answerUnits().stream().map(unit -> {
            String raw = unit.getCompareMethod() == com.cenedu.backend.global.common.enums.CompareMethod.CHOICE
                    && unit.getAnswerRaw() != null && !unit.getAnswerRaw().startsWith("C")
                    ? "C" + unit.getAnswerRaw() : unit.getAnswerRaw();
            return new SnapshotAnswerUnit(unit.getUnitKey(), unit.getStep() == null ? null
                    : "ST" + (source.steps().indexOf(unit.getStep()) + 1), unit.getDisplayOrder(),
                    raw, unit.getCompareMethod() == com.cenedu.backend.global.common.enums.CompareMethod.CHOICE
                    || unit.getCompareMethod() == com.cenedu.backend.global.common.enums.CompareMethod.RUBRIC
                    ? null : unit.getAnswerNormalized(), unit.getCompareMethod(),
                    unit.getDiagnosticType(), unit.getDisplayUnit());
        }).toList();
        return new QuestionSnapshotV1(snapshot.schemaVersion(), snapshot.metadata(), snapshot.contentBlocks(),
                snapshot.assets(), snapshot.choices(), snapshot.steps(), entityUnits,
                snapshot.explanation(), snapshot.learningGuide(), rubrics(source.rubricItems()));
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, type); }
        catch (Exception exception) { throw new IllegalArgumentException("문제 JSON을 읽을 수 없습니다.", exception); }
    }

    private ProblemLearningGuideResponse readGuide(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, ProblemLearningGuideResponse.class); }
        catch (Exception exception) { throw new IllegalArgumentException("학습 안내 JSON을 읽을 수 없습니다.", exception); }
    }

    private SnapshotStep step(ProblemStepResponse step, int normalizedOrder) {
        List<SnapshotSegment> segments = step.segments().stream().map(segment -> {
            SnapshotSegmentType type = SnapshotSegmentType.valueOf(segment.type());
            return new SnapshotSegment(type, type == SnapshotSegmentType.TEXT ? segment.value() : null,
                    type == SnapshotSegmentType.TEXT ? null : segment.unitKey());
        }).toList();
        return new SnapshotStep("ST" + (normalizedOrder + 1), normalizedOrder, step.label(), segments);
    }

    private int findStepIndex(List<ProblemStepResponse> steps, Long stepId) {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).id().equals(stepId)) return i;
        throw new IllegalArgumentException("답안 단위의 STEP을 찾을 수 없습니다.");
    }

    private SnapshotLearningGuide guide(ProblemLearningGuideResponse guide) {
        return guide == null ? null : new SnapshotLearningGuide(guide.conceptTitle(), guide.summary(), guide.keyPoints());
    }

    /** 서술형 채점 기준을 버전 간 안정적인 R 논리 키로 변환한다. */
    public List<SnapshotRubricItem> rubrics(List<ProblemRubricItem> rubricItems) {
        if (rubricItems == null) return List.of();
        List<SnapshotRubricItem> result = new java.util.ArrayList<>();
        for (int i = 0; i < rubricItems.size(); i++) {
            ProblemRubricItem item = rubricItems.get(i);
            result.add(new SnapshotRubricItem("R" + (i + 1), i, item.getLabel(), item.getWeight()));
        }
        return List.copyOf(result);
    }
}
