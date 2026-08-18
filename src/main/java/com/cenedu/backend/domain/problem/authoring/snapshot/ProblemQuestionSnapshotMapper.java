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

/** 문제 Entity의 영속화 필드만 S1 스냅샷 계약으로 변환하는 경계 Mapper다. */
@Component
public class ProblemQuestionSnapshotMapper {

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
        List<SnapshotContentBlock> blocks = detail.contentBlocks().stream().map(this::contentBlock).toList();
        List<SnapshotAssetReference> assets = detail.assets().stream()
                .map(asset -> new SnapshotAssetReference(asset.assetKey(), asset.altText())).toList();
        List<SnapshotChoice> choices = detail.choices().stream()
                .map(choice -> new SnapshotChoice("C" + choice.displayOrder(), choice.displayOrder(), choice.content())).toList();
        List<SnapshotStep> steps = detail.steps().stream().map(this::step).toList();
        List<SnapshotAnswerUnit> units = detail.answerUnits().stream().map(unit -> {
            String normalized = unit.compareMethod() == com.cenedu.backend.global.common.enums.CompareMethod.CHOICE
                    || unit.compareMethod() == com.cenedu.backend.global.common.enums.CompareMethod.RUBRIC
                    ? null : unit.answer();
            return new SnapshotAnswerUnit(unit.unitKey(),
                    unit.stepId() == null ? null : "S" + findStepOrder(detail.steps(), unit.stepId()),
                    unit.displayOrder(), unit.answer(), normalized, unit.compareMethod(),
                    unit.diagnosticType(), unit.displayUnit());
        }).toList();
        SnapshotLearningGuide guide = guide(detail.learningGuide());
        return new QuestionSnapshotV1(1, base.metadata(), blocks, assets, choices, steps, units,
                detail.explanation(), guide, List.of());
    }

    private SnapshotContentBlock contentBlock(ProblemContentBlockResponse block) {
        return new SnapshotContentBlock(block.blockId(), SnapshotBlockKind.valueOf(block.blockKind()),
                block.displayOrder(), block.text(), block.assetRef(), block.markup());
    }

    private SnapshotStep step(ProblemStepResponse step) {
        List<SnapshotSegment> segments = step.segments().stream().map(segment -> {
            SnapshotSegmentType type = SnapshotSegmentType.valueOf(segment.type());
            return new SnapshotSegment(type, type == SnapshotSegmentType.TEXT ? segment.value() : null,
                    type == SnapshotSegmentType.TEXT ? null : segment.unitKey());
        }).toList();
        return new SnapshotStep("S" + step.displayOrder(), step.displayOrder(), step.label(), segments);
    }

    private int findStepOrder(List<ProblemStepResponse> steps, Long stepId) {
        return steps.stream().filter(step -> step.id().equals(stepId)).findFirst()
                .map(ProblemStepResponse::displayOrder).orElseThrow();
    }

    private SnapshotLearningGuide guide(ProblemLearningGuideResponse guide) {
        return guide == null ? null : new SnapshotLearningGuide(guide.conceptTitle(), guide.summary(), guide.keyPoints());
    }

    /** 서술형 채점 기준을 버전 간 안정적인 R 논리 키로 변환한다. */
    public List<SnapshotRubricItem> rubrics(List<ProblemRubricItem> rubricItems) {
        return rubricItems == null ? List.of() : rubricItems.stream()
                .map(item -> new SnapshotRubricItem("R" + item.getDisplayOrder(),
                        item.getDisplayOrder(), item.getLabel(), item.getWeight()))
                .toList();
    }
}
