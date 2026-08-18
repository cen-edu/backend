package com.cenedu.backend.domain.problem.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.entity.*;
import com.cenedu.backend.domain.problem.entity.enums.*;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** 검증된 S1 Snapshot을 문제은행의 단방향 Entity 그래프로 변환한다. */
@Component
public class ProblemSnapshotEntityMapper {
    private final ObjectMapper objectMapper;

    public ProblemSnapshotEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Snapshot을 저장 가능한 본체·하위 Entity 묶음으로 변환한다. */
    public ProblemQuestionPersistenceBundle map(QuestionSnapshotV1 snapshot,
                                                Map<String, String> finalAssetKeys) {
        requireValid(snapshot);
        SnapshotMetadata metadata = snapshot.metadata();
        String contentBlocks = write(snapshot.contentBlocks());
        String learningGuide = snapshot.learningGuide() == null ? null : write(snapshot.learningGuide());
        String promptText = snapshot.contentBlocks().stream()
                .map(SnapshotContentBlock::text).filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("\n"));
        ProblemQuestion question = ProblemQuestion.create(QuestionSourceType.GENERATED, null, null, null,
                metadata.subUnitId(), metadata.topicCode(), difficulty(metadata.difficulty()), metadata.questionType(),
                metadata.presentation(), contentBlocks, promptText, snapshot.explanation(), learningGuide, null,
                VerificationStatus.PASSED);

        List<ProblemChoice> choices = snapshot.choices().stream()
                .map(c -> ProblemChoice.create(question, (short) c.displayOrder(), c.content())).toList();
        Map<String, ProblemStep> stepsByKey = new java.util.HashMap<>();
        List<ProblemStep> steps = snapshot.steps().stream().map(s -> {
            ProblemStep step = ProblemStep.create(question, (short) s.displayOrder(), s.label(), write(s.segments()));
            stepsByKey.put(s.stepKey(), step);
            return step;
        }).toList();
        List<ProblemAnswerUnit> answers = snapshot.answerUnits().stream().map(a ->
                ProblemAnswerUnit.create(question, stepsByKey.get(a.stepKey()), a.unitKey(), a.displayOrder(), null,
                        a.answerRaw(), a.answerNormalized(), a.compareMethod(), a.diagnosticType(), a.displayUnit()))
                .toList();
        List<ProblemRubricItem> rubrics = snapshot.rubricItems().stream()
                .map(r -> ProblemRubricItem.create(question, r.displayOrder(), r.criterion(), (short) r.weightPercent(), null, null))
                .toList();
        List<ProblemAsset> assets = snapshot.assets().stream().map((a) ->
                ProblemAsset.create(question, a.assetKey(), AssetRole.FIGURE, (short) snapshot.assets().indexOf(a),
                        requiredAssetKey(finalAssetKeys, a.assetKey()), 0, 0, a.altText())).toList();
        return new ProblemQuestionPersistenceBundle(question, choices, steps, answers, rubrics, assets);
    }

    private void requireValid(QuestionSnapshotV1 snapshot) {
        if (snapshot == null || snapshot.metadata() == null) throw new IllegalArgumentException("Snapshot이 필요합니다.");
        if (snapshot.schemaVersion() != QuestionSnapshotV1.CURRENT_SCHEMA_VERSION) throw new IllegalArgumentException("지원하지 않는 Snapshot 버전입니다.");
        if (snapshot.metadata().questionType() == QuestionType.ESSAY && snapshot.rubricItems().isEmpty()) throw new IllegalArgumentException("서술형 Rubric이 필요합니다.");
        if (snapshot.metadata().questionType() == QuestionType.MULTIPLE_CHOICE && snapshot.choices().isEmpty()) throw new IllegalArgumentException("객관식 보기가 필요합니다.");
    }

    private short difficulty(String value) {
        return switch (value) { case "low" -> 1; case "mid" -> 2; case "high" -> 3; default -> throw new IllegalArgumentException("지원하지 않는 난이도입니다."); };
    }

    private String requiredAssetKey(Map<String, String> keys, String assetKey) {
        String value = keys == null ? null : keys.get(assetKey);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("최종 자산 키가 없습니다: " + assetKey);
        return value;
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Snapshot JSON을 저장할 수 없습니다.", e); }
    }
}
