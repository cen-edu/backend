package com.cenedu.backend.domain.problem.ai.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cenedu.backend.domain.problem.ai.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.ai.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.ai.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.ai.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.ai.model.SnapshotChoice;
import com.cenedu.backend.domain.problem.ai.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.ai.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.ai.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.ai.model.SnapshotRubricItem;
import com.cenedu.backend.domain.problem.ai.model.SnapshotSegment;
import com.cenedu.backend.domain.problem.ai.model.SnapshotSegmentType;
import com.cenedu.backend.domain.problem.ai.model.SnapshotStep;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

import org.springframework.stereotype.Component;

/** AI 출력 직후 문항 스냅샷의 구조와 참조 무결성을 검사한다. */
@Component
public class SnapshotStructuralValidator {

    private static final Set<String> DIFFICULTIES = Set.of("low", "mid", "high");
    private static final Pattern BLOCK_KEY = Pattern.compile("CB[1-9][0-9]*");
    private static final Pattern ASSET_KEY = Pattern.compile("F[1-9][0-9]*");
    private static final Pattern CHOICE_KEY = Pattern.compile("C[1-9][0-9]*");
    private static final Pattern STEP_KEY = Pattern.compile("ST[1-9][0-9]*");
    private static final Pattern BLANK_KEY = Pattern.compile("B[1-9][0-9]*");
    private static final Pattern RUBRIC_KEY = Pattern.compile("R[1-9][0-9]*");
    private static final Pattern HTML_TAG = Pattern.compile(
            "<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9]*)\\b");
    private static final Pattern EVENT_ATTRIBUTE = Pattern.compile(
            "\\bon[a-zA-Z]+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNAL_ATTRIBUTE = Pattern.compile(
            "\\b(?:href|src)\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_CSS = Pattern.compile(
            "(?:url\\s*\\(|expression\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SAFE_TABLE_TAGS = Set.of(
            "table", "caption", "colgroup", "col", "thead", "tbody", "tfoot",
            "tr", "th", "td", "br", "span", "div");

    /** 위반이 하나라도 있으면 모든 위반을 담은 예외를 던진다. */
    public void validate(QuestionSnapshotV1 snapshot) {
        List<String> violations = violations(snapshot);
        if (!violations.isEmpty()) {
            throw new SnapshotValidationException(violations);
        }
    }

    /** AI 재시도 사유로 사용할 구조 위반 목록을 반환한다. */
    public List<String> violations(QuestionSnapshotV1 snapshot) {
        List<String> violations = new ArrayList<>();
        if (snapshot == null) {
            return List.of("snapshot: null일 수 없습니다.");
        }

        if (snapshot.schemaVersion() != QuestionSnapshotV1.CURRENT_SCHEMA_VERSION) {
            violations.add("schemaVersion: 지원하는 값은 1입니다.");
        }

        validateMetadata(snapshot.metadata(), violations);
        validateRequiredLists(snapshot, violations);
        validateExplanation(snapshot.explanation(), violations);
        validateLearningGuide(snapshot.learningGuide(), violations);

        validateContentBlocks(snapshot.contentBlocks(), violations);
        validateAssets(snapshot.assets(), violations);
        validateAssetReferences(snapshot.contentBlocks(), snapshot.assets(), violations);
        validateChoices(snapshot.choices(), violations);
        validateSteps(snapshot.steps(), violations);
        validateAnswerUnits(snapshot.answerUnits(), violations);
        validateRubrics(snapshot.rubricItems(), violations);

        validatePresentation(snapshot.metadata(), snapshot.contentBlocks(), violations);
        validateQuestionType(snapshot, violations);
        validateStepReferences(snapshot, violations);

        return List.copyOf(new LinkedHashSet<>(violations));
    }

    private void validateMetadata(SnapshotMetadata metadata, List<String> violations) {
        if (metadata == null) {
            violations.add("metadata: 필수입니다.");
            return;
        }
        if (metadata.questionType() == null) {
            violations.add("metadata.questionType: 필수입니다.");
        }
        if (metadata.presentation() == null) {
            violations.add("metadata.presentation: 필수입니다.");
        }
        if (isBlank(metadata.difficulty()) || !DIFFICULTIES.contains(metadata.difficulty())) {
            violations.add("metadata.difficulty: low, mid, high 중 하나여야 합니다.");
        }
        if (metadata.subUnitId() == null || metadata.subUnitId() <= 0) {
            violations.add("metadata.subUnitId: 양수 ID가 필요합니다.");
        }
        if (metadata.topicCode() != null && isBlank(metadata.topicCode())) {
            violations.add("metadata.topicCode: 값이 있으면 공백일 수 없습니다.");
        }
        if (metadata.derivedFromQuestionId() != null
                && metadata.derivedFromQuestionId() <= 0) {
            violations.add("metadata.derivedFromQuestionId: 값이 있으면 양수여야 합니다.");
        }
    }

    private void validateRequiredLists(QuestionSnapshotV1 snapshot, List<String> violations) {
        requireList(snapshot.contentBlocks(), "contentBlocks", violations);
        requireList(snapshot.assets(), "assets", violations);
        requireList(snapshot.choices(), "choices", violations);
        requireList(snapshot.steps(), "steps", violations);
        requireList(snapshot.answerUnits(), "answerUnits", violations);
        requireList(snapshot.rubricItems(), "rubricItems", violations);
    }

    private void validateExplanation(String explanation, List<String> violations) {
        if (isBlank(explanation)) {
            violations.add("explanation: 모든 문제 유형에서 필수입니다.");
        }
    }

    private void validateLearningGuide(
            SnapshotLearningGuide guide, List<String> violations
    ) {
        if (guide == null) {
            violations.add("learningGuide: 모든 문제 유형에서 필수입니다.");
            return;
        }
        requireNonBlank(guide.conceptTitle(), "learningGuide.conceptTitle", violations);
        requireNonBlank(guide.summary(), "learningGuide.summary", violations);
        if (guide.keyPoints() == null) {
            violations.add("learningGuide.keyPoints: 빈 목록 대신 1~3개 값이 필요합니다.");
            return;
        }
        if (guide.keyPoints().size() < 1 || guide.keyPoints().size() > 3) {
            violations.add("learningGuide.keyPoints: 1개 이상 3개 이하여야 합니다.");
        }

        Set<String> unique = new HashSet<>();
        for (int index = 0; index < guide.keyPoints().size(); index++) {
            String keyPoint = guide.keyPoints().get(index);
            String path = "learningGuide.keyPoints[" + index + "]";
            if (isBlank(keyPoint)) {
                violations.add(path + ": 공백일 수 없습니다.");
            } else if (!unique.add(keyPoint.trim())) {
                violations.add(path + ": 중복된 핵심 정보입니다.");
            }
        }
    }

    private void validateContentBlocks(
            List<SnapshotContentBlock> blocks, List<String> violations
    ) {
        if (blocks == null) {
            return;
        }
        if (blocks.isEmpty()) {
            violations.add("contentBlocks: 최소 1개의 TEXT 블록이 필요합니다.");
            return;
        }

        validateOrderedKeys(
                blocks,
                "contentBlocks",
                SnapshotContentBlock::blockKey,
                SnapshotContentBlock::displayOrder,
                BLOCK_KEY,
                violations
        );

        SnapshotContentBlock first = blocks.stream()
                .filter(block -> block != null && block.displayOrder() == 0)
                .findFirst()
                .orElse(null);
        if (first == null || first.blockKind() != SnapshotBlockKind.TEXT) {
            violations.add("contentBlocks: displayOrder 0은 반드시 TEXT여야 합니다.");
        }

        for (int index = 0; index < blocks.size(); index++) {
            SnapshotContentBlock block = blocks.get(index);
            String path = "contentBlocks[" + index + "]";
            if (block == null) {
                continue;
            }
            if (block.blockKind() == null) {
                violations.add(path + ".blockKind: 필수입니다.");
                continue;
            }
            switch (block.blockKind()) {
                case TEXT -> {
                    requireNonBlank(block.text(), path + ".text", violations);
                    requireNull(block.assetRef(), path + ".assetRef", violations);
                    requireNull(block.markup(), path + ".markup", violations);
                }
                case FIGURE -> {
                    requireNonBlank(block.assetRef(), path + ".assetRef", violations);
                    requireNull(block.text(), path + ".text", violations);
                    requireNull(block.markup(), path + ".markup", violations);
                }
                case TABLE -> {
                    requireNonBlank(block.markup(), path + ".markup", violations);
                    requireNull(block.text(), path + ".text", violations);
                    requireNull(block.assetRef(), path + ".assetRef", violations);
                    if (!isBlank(block.markup())) {
                        validateTableMarkup(block.markup(), path + ".markup", violations);
                    }
                }
            }
        }
    }

    private void validateTableMarkup(
            String markup, String path, List<String> violations
    ) {
        String lower = markup.toLowerCase();
        if (!lower.contains("<table")) {
            violations.add(path + ": table 요소가 필요합니다.");
        }
        if (lower.contains("<script") || lower.contains("<style")
                || lower.contains("javascript:")) {
            violations.add(path + ": 실행 가능한 script/style 내용을 포함할 수 없습니다.");
        }
        if (EVENT_ATTRIBUTE.matcher(markup).find()) {
            violations.add(path + ": 이벤트 속성을 포함할 수 없습니다.");
        }
        if (EXTERNAL_ATTRIBUTE.matcher(markup).find()) {
            violations.add(path + ": href/src 외부 참조를 포함할 수 없습니다.");
        }
        if (DANGEROUS_CSS.matcher(markup).find()) {
            violations.add(path + ": 외부 자원 또는 실행형 CSS를 포함할 수 없습니다.");
        }

        Matcher matcher = HTML_TAG.matcher(markup);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase();
            if (!SAFE_TABLE_TAGS.contains(tag)) {
                violations.add(path + ": 허용되지 않은 HTML 태그입니다: " + tag);
            }
        }
    }

    private void validateAssets(
            List<SnapshotAssetReference> assets, List<String> violations
    ) {
        if (assets == null) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < assets.size(); index++) {
            SnapshotAssetReference asset = assets.get(index);
            String path = "assets[" + index + "]";
            if (asset == null) {
                violations.add(path + ": null일 수 없습니다.");
                continue;
            }
            validateKey(asset.assetKey(), path + ".assetKey", ASSET_KEY, keys, violations);
            requireNonBlank(asset.altText(), path + ".altText", violations);
        }
    }

    private void validateAssetReferences(
            List<SnapshotContentBlock> blocks,
            List<SnapshotAssetReference> assets,
            List<String> violations
    ) {
        if (blocks == null || assets == null) {
            return;
        }
        Set<String> assetKeys = new HashSet<>();
        for (SnapshotAssetReference asset : assets) {
            if (asset != null && !isBlank(asset.assetKey())) {
                assetKeys.add(asset.assetKey());
            }
        }

        Set<String> referenced = new HashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            SnapshotContentBlock block = blocks.get(index);
            if (block == null || block.blockKind() != SnapshotBlockKind.FIGURE
                    || isBlank(block.assetRef())) {
                continue;
            }
            referenced.add(block.assetRef());
            if (!assetKeys.contains(block.assetRef())) {
                violations.add("contentBlocks[" + index
                        + "].assetRef: assets에 없는 키를 참조합니다: " + block.assetRef());
            }
        }
        for (String assetKey : assetKeys) {
            if (!referenced.contains(assetKey)) {
                violations.add("assets: FIGURE에서 참조하지 않는 asset입니다: " + assetKey);
            }
        }
    }

    private void validateChoices(List<SnapshotChoice> choices, List<String> violations) {
        if (choices == null) {
            return;
        }
        validateOrderedKeys(
                choices,
                "choices",
                SnapshotChoice::choiceKey,
                SnapshotChoice::displayOrder,
                CHOICE_KEY,
                violations
        );
        for (int index = 0; index < choices.size(); index++) {
            SnapshotChoice choice = choices.get(index);
            if (choice != null) {
                requireNonBlank(
                        choice.content(), "choices[" + index + "].content", violations);
            }
        }
    }

    private void validateSteps(List<SnapshotStep> steps, List<String> violations) {
        if (steps == null) {
            return;
        }
        validateOrderedKeys(
                steps,
                "steps",
                SnapshotStep::stepKey,
                SnapshotStep::displayOrder,
                STEP_KEY,
                violations
        );
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            SnapshotStep step = steps.get(stepIndex);
            String stepPath = "steps[" + stepIndex + "]";
            if (step == null) {
                continue;
            }
            requireNonBlank(step.label(), stepPath + ".label", violations);
            if (step.segments() == null || step.segments().isEmpty()) {
                violations.add(stepPath + ".segments: 최소 1개가 필요합니다.");
                continue;
            }

            int blankCount = 0;
            for (int segmentIndex = 0; segmentIndex < step.segments().size(); segmentIndex++) {
                SnapshotSegment segment = step.segments().get(segmentIndex);
                String path = stepPath + ".segments[" + segmentIndex + "]";
                if (segment == null) {
                    violations.add(path + ": null일 수 없습니다.");
                    continue;
                }
                if (segment.type() == null) {
                    violations.add(path + ".type: 필수입니다.");
                    continue;
                }
                switch (segment.type()) {
                    case TEXT -> {
                        requireNonBlank(segment.text(), path + ".text", violations);
                        requireNull(segment.unitKey(), path + ".unitKey", violations);
                    }
                    case BLANK -> {
                        blankCount++;
                        requireNonBlank(segment.unitKey(), path + ".unitKey", violations);
                        requireNull(segment.text(), path + ".text", violations);
                    }
                    case ANSWER_REF -> {
                        requireNonBlank(segment.unitKey(), path + ".unitKey", violations);
                        requireNull(segment.text(), path + ".text", violations);
                    }
                }
            }
            if (blankCount == 0) {
                violations.add(stepPath + ": 최소 1개의 BLANK가 필요합니다.");
            }
        }
    }

    private void validateAnswerUnits(
            List<SnapshotAnswerUnit> units, List<String> violations
    ) {
        if (units == null) {
            return;
        }
        validateOrderedKeys(
                units,
                "answerUnits",
                SnapshotAnswerUnit::unitKey,
                SnapshotAnswerUnit::displayOrder,
                null,
                violations
        );

        for (int index = 0; index < units.size(); index++) {
            SnapshotAnswerUnit unit = units.get(index);
            String path = "answerUnits[" + index + "]";
            if (unit == null) {
                continue;
            }
            if (unit.compareMethod() == null) {
                violations.add(path + ".compareMethod: 필수입니다.");
                continue;
            }
            if (unit.answerRaw() != null && isBlank(unit.answerRaw())) {
                violations.add(path + ".answerRaw: 값이 있으면 공백일 수 없습니다.");
            }
            if (unit.answerNormalized() != null && isBlank(unit.answerNormalized())) {
                violations.add(path + ".answerNormalized: 값이 있으면 공백일 수 없습니다.");
            }
            if (unit.displayUnit() != null && isBlank(unit.displayUnit())) {
                violations.add(path + ".displayUnit: 값이 있으면 공백일 수 없습니다.");
            }

            switch (unit.compareMethod()) {
                case CHOICE -> {
                    requireNonBlank(unit.answerRaw(), path + ".answerRaw", violations);
                    requireNull(
                            unit.answerNormalized(), path + ".answerNormalized", violations);
                }
                case VALUE, EXACT, SET, SUBST ->
                        requireNonBlank(unit.answerRaw(), path + ".answerRaw", violations);
                case RUBRIC -> {
                    requireNull(unit.answerRaw(), path + ".answerRaw", violations);
                    requireNull(
                            unit.answerNormalized(), path + ".answerNormalized", violations);
                }
            }
        }
    }

    private void validateRubrics(
            List<SnapshotRubricItem> rubrics, List<String> violations
    ) {
        if (rubrics == null) {
            return;
        }
        validateOrderedKeys(
                rubrics,
                "rubricItems",
                SnapshotRubricItem::rubricKey,
                SnapshotRubricItem::displayOrder,
                RUBRIC_KEY,
                violations
        );
        int weightSum = 0;
        for (int index = 0; index < rubrics.size(); index++) {
            SnapshotRubricItem rubric = rubrics.get(index);
            String path = "rubricItems[" + index + "]";
            if (rubric == null) {
                continue;
            }
            requireNonBlank(rubric.criterion(), path + ".criterion", violations);
            if (rubric.weightPercent() <= 0) {
                violations.add(path + ".weightPercent: 양수여야 합니다.");
            }
            weightSum += rubric.weightPercent();
        }
        if (!rubrics.isEmpty() && weightSum != 100) {
            violations.add("rubricItems.weightPercent: 전체 합은 100이어야 합니다.");
        }
    }

    private void validatePresentation(
            SnapshotMetadata metadata,
            List<SnapshotContentBlock> blocks,
            List<String> violations
    ) {
        if (metadata == null || metadata.presentation() == null || blocks == null) {
            return;
        }
        boolean hasFigure = blocks.stream()
                .anyMatch(block -> block != null
                        && block.blockKind() == SnapshotBlockKind.FIGURE);
        boolean hasTable = blocks.stream()
                .anyMatch(block -> block != null
                        && block.blockKind() == SnapshotBlockKind.TABLE);

        QuestionPresentation presentation = metadata.presentation();
        switch (presentation) {
            case TEXT_ONLY -> {
                if (hasFigure || hasTable) {
                    violations.add("metadata.presentation: TEXT_ONLY에는 FIGURE/TABLE이 없습니다.");
                }
            }
            case WITH_FIGURE -> {
                if (!hasFigure) {
                    violations.add("metadata.presentation: WITH_FIGURE에는 FIGURE가 필요합니다.");
                }
            }
            case WITH_TABLE -> {
                if (!hasTable) {
                    violations.add("metadata.presentation: WITH_TABLE에는 TABLE이 필요합니다.");
                }
            }
        }
    }

    private void validateQuestionType(
            QuestionSnapshotV1 snapshot, List<String> violations
    ) {
        if (snapshot.metadata() == null || snapshot.metadata().questionType() == null
                || snapshot.choices() == null || snapshot.steps() == null
                || snapshot.answerUnits() == null || snapshot.rubricItems() == null) {
            return;
        }
        QuestionType type = snapshot.metadata().questionType();
        switch (type) {
            case MULTIPLE_CHOICE -> validateMultipleChoice(snapshot, violations);
            case SHORT_INPUT -> validateShortInput(snapshot, violations);
            case STEP_FILL -> validateStepFill(snapshot, violations);
            case ESSAY -> validateEssay(snapshot, violations);
        }
    }

    private void validateMultipleChoice(
            QuestionSnapshotV1 snapshot, List<String> violations
    ) {
        if (snapshot.choices().size() < 2) {
            violations.add("choices: 객관식은 최소 2개의 보기가 필요합니다.");
        }
        requireEmpty(snapshot.steps(), "steps", QuestionType.MULTIPLE_CHOICE, violations);
        requireEmpty(
                snapshot.rubricItems(), "rubricItems", QuestionType.MULTIPLE_CHOICE, violations);
        SnapshotAnswerUnit unit = requireMainUnit(
                snapshot.answerUnits(), CompareMethod.CHOICE, violations);
        validateNonStepUnit(unit, violations);

        if (unit != null && !isBlank(unit.answerRaw())) {
            Set<String> choiceKeys = new HashSet<>();
            for (SnapshotChoice choice : snapshot.choices()) {
                if (choice != null && !isBlank(choice.choiceKey())) {
                    choiceKeys.add(choice.choiceKey());
                }
            }
            if (!choiceKeys.contains(unit.answerRaw())) {
                violations.add("answerUnits[0].answerRaw: 실제 choiceKey를 참조해야 합니다.");
            }
        }
    }

    private void validateShortInput(
            QuestionSnapshotV1 snapshot, List<String> violations
    ) {
        requireEmpty(snapshot.choices(), "choices", QuestionType.SHORT_INPUT, violations);
        requireEmpty(snapshot.steps(), "steps", QuestionType.SHORT_INPUT, violations);
        requireEmpty(snapshot.rubricItems(), "rubricItems", QuestionType.SHORT_INPUT, violations);
        SnapshotAnswerUnit unit = requireMainUnit(snapshot.answerUnits(), null, violations);
        validateNonStepUnit(unit, violations);
        if (unit != null && !isAnswerComparison(unit.compareMethod())) {
            violations.add("answerUnits[0].compareMethod: SHORT_INPUT은 VALUE/EXACT/SET/SUBST만 허용합니다.");
        }
    }

    private void validateStepFill(
            QuestionSnapshotV1 snapshot, List<String> violations
    ) {
        requireEmpty(snapshot.choices(), "choices", QuestionType.STEP_FILL, violations);
        requireEmpty(snapshot.rubricItems(), "rubricItems", QuestionType.STEP_FILL, violations);
        if (snapshot.steps().size() < 1 || snapshot.steps().size() > 4) {
            violations.add("steps: STEP_FILL은 1개 이상 4개 이하여야 합니다.");
        }
        if (snapshot.answerUnits().size() < 1 || snapshot.answerUnits().size() > 8) {
            violations.add("answerUnits: STEP_FILL은 1개 이상 8개 이하여야 합니다.");
        }

        Set<String> stepKeys = new HashSet<>();
        for (SnapshotStep step : snapshot.steps()) {
            if (step != null && !isBlank(step.stepKey())) {
                stepKeys.add(step.stepKey());
            }
        }
        for (int index = 0; index < snapshot.answerUnits().size(); index++) {
            SnapshotAnswerUnit unit = snapshot.answerUnits().get(index);
            String path = "answerUnits[" + index + "]";
            if (unit == null) {
                continue;
            }
            if (!matches(BLANK_KEY, unit.unitKey())) {
                violations.add(path + ".unitKey: B1, B2 형식이어야 합니다.");
            }
            if (isBlank(unit.stepKey()) || !stepKeys.contains(unit.stepKey())) {
                violations.add(path + ".stepKey: 실제 STEP의 논리 키를 참조해야 합니다.");
            }
            if (unit.diagnosticType() == null) {
                violations.add(path + ".diagnosticType: STEP_FILL에서는 필수입니다.");
            }
            if (!isAnswerComparison(unit.compareMethod())) {
                violations.add(path + ".compareMethod: STEP_FILL은 VALUE/EXACT/SET/SUBST만 허용합니다.");
            }
        }
    }

    private void validateEssay(QuestionSnapshotV1 snapshot, List<String> violations) {
        requireEmpty(snapshot.choices(), "choices", QuestionType.ESSAY, violations);
        requireEmpty(snapshot.steps(), "steps", QuestionType.ESSAY, violations);
        if (snapshot.rubricItems().size() < 2 || snapshot.rubricItems().size() > 5) {
            violations.add("rubricItems: ESSAY는 2개 이상 5개 이하여야 합니다.");
        }
        SnapshotAnswerUnit unit = requireMainUnit(
                snapshot.answerUnits(), CompareMethod.RUBRIC, violations);
        validateNonStepUnit(unit, violations);
    }

    private SnapshotAnswerUnit requireMainUnit(
            List<SnapshotAnswerUnit> units,
            CompareMethod expectedMethod,
            List<String> violations
    ) {
        if (units.size() != 1) {
            violations.add("answerUnits: MAIN 답안 단위가 정확히 1개 필요합니다.");
            return null;
        }
        SnapshotAnswerUnit unit = units.getFirst();
        if (unit == null) {
            return null;
        }
        if (!"MAIN".equals(unit.unitKey())) {
            violations.add("answerUnits[0].unitKey: MAIN이어야 합니다.");
        }
        if (expectedMethod != null && unit.compareMethod() != expectedMethod) {
            violations.add("answerUnits[0].compareMethod: " + expectedMethod + "이어야 합니다.");
        }
        return unit;
    }

    private void validateNonStepUnit(
            SnapshotAnswerUnit unit, List<String> violations
    ) {
        if (unit == null) {
            return;
        }
        if (unit.stepKey() != null) {
            violations.add("answerUnits[0].stepKey: STEP_FILL이 아니면 null이어야 합니다.");
        }
        if (unit.diagnosticType() != null) {
            violations.add("answerUnits[0].diagnosticType: STEP_FILL이 아니면 null이어야 합니다.");
        }
    }

    private void validateStepReferences(
            QuestionSnapshotV1 snapshot, List<String> violations
    ) {
        if (snapshot.metadata() == null
                || snapshot.metadata().questionType() != QuestionType.STEP_FILL
                || snapshot.steps() == null || snapshot.answerUnits() == null) {
            return;
        }

        Map<String, String> blankStepByUnit = new HashMap<>();
        Set<String> seenBlanks = new HashSet<>();
        List<SnapshotStep> orderedSteps = snapshot.steps().stream()
                .filter(step -> step != null)
                .sorted(Comparator.comparingInt(SnapshotStep::displayOrder))
                .toList();

        for (SnapshotStep step : orderedSteps) {
            if (step.segments() == null) {
                continue;
            }
            for (int index = 0; index < step.segments().size(); index++) {
                SnapshotSegment segment = step.segments().get(index);
                if (segment == null || segment.type() == null || isBlank(segment.unitKey())) {
                    continue;
                }
                String path = "step(" + step.stepKey() + ").segments[" + index + "]";
                if (segment.type() == SnapshotSegmentType.BLANK) {
                    String previous = blankStepByUnit.putIfAbsent(
                            segment.unitKey(), step.stepKey());
                    if (previous != null) {
                        violations.add(path + ": 같은 unitKey의 BLANK가 두 번 존재합니다.");
                    }
                    seenBlanks.add(segment.unitKey());
                } else if (segment.type() == SnapshotSegmentType.ANSWER_REF
                        && !seenBlanks.contains(segment.unitKey())) {
                    violations.add(path + ": 앞에서 등장한 BLANK만 참조할 수 있습니다.");
                }
            }
        }

        Map<String, SnapshotAnswerUnit> unitByKey = new HashMap<>();
        for (SnapshotAnswerUnit unit : snapshot.answerUnits()) {
            if (unit != null && !isBlank(unit.unitKey())) {
                unitByKey.putIfAbsent(unit.unitKey(), unit);
            }
        }
        for (Map.Entry<String, String> blank : blankStepByUnit.entrySet()) {
            SnapshotAnswerUnit unit = unitByKey.get(blank.getKey());
            if (unit == null) {
                violations.add("BLANK(" + blank.getKey() + "): answerUnit이 없습니다.");
            } else if (!blank.getValue().equals(unit.stepKey())) {
                violations.add("answerUnit(" + blank.getKey()
                        + ").stepKey: BLANK가 속한 stepKey와 같아야 합니다.");
            }
        }
        for (String unitKey : unitByKey.keySet()) {
            if (!blankStepByUnit.containsKey(unitKey)) {
                violations.add("answerUnit(" + unitKey + "): 연결된 BLANK가 없습니다.");
            }
        }
    }

    private <T> void validateOrderedKeys(
            List<T> items,
            String path,
            Function<T, String> keyExtractor,
            ToIntFunction<T> orderExtractor,
            Pattern keyPattern,
            List<String> violations
    ) {
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        boolean validOrder = true;
        for (int index = 0; index < items.size(); index++) {
            T item = items.get(index);
            if (item == null) {
                violations.add(path + "[" + index + "]: null일 수 없습니다.");
                validOrder = false;
                continue;
            }
            validateKey(
                    keyExtractor.apply(item),
                    path + "[" + index + "].key",
                    keyPattern,
                    keys,
                    violations
            );
            int order = orderExtractor.applyAsInt(item);
            if (order < 0 || !orders.add(order)) {
                validOrder = false;
            }
        }
        for (int expected = 0; expected < items.size(); expected++) {
            if (!orders.contains(expected)) {
                validOrder = false;
            }
        }
        if (!validOrder) {
            violations.add(path + ".displayOrder: 0부터 중복 없이 연속이어야 합니다.");
        }
    }

    private void validateKey(
            String key,
            String path,
            Pattern pattern,
            Set<String> keys,
            List<String> violations
    ) {
        if (isBlank(key)) {
            violations.add(path + ": 필수입니다.");
            return;
        }
        if (pattern != null && !pattern.matcher(key).matches()) {
            violations.add(path + ": 허용된 논리 키 형식이 아닙니다.");
        }
        if (!keys.add(key)) {
            violations.add(path + ": 중복된 논리 키입니다.");
        }
    }

    private boolean isAnswerComparison(CompareMethod method) {
        return method == CompareMethod.VALUE
                || method == CompareMethod.EXACT
                || method == CompareMethod.SET
                || method == CompareMethod.SUBST;
    }

    private boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private void requireList(List<?> list, String path, List<String> violations) {
        if (list == null) {
            violations.add(path + ": null 대신 빈 목록을 사용해야 합니다.");
        }
    }

    private void requireEmpty(
            List<?> list,
            String path,
            QuestionType type,
            List<String> violations
    ) {
        if (!list.isEmpty()) {
            violations.add(path + ": " + type + "에서는 빈 목록이어야 합니다.");
        }
    }

    private void requireNonBlank(String value, String path, List<String> violations) {
        if (isBlank(value)) {
            violations.add(path + ": 필수이며 공백일 수 없습니다.");
        }
    }

    private void requireNull(Object value, String path, List<String> violations) {
        if (value != null) {
            violations.add(path + ": 사용하지 않는 필드이므로 null이어야 합니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
