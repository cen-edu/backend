package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegmentType;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotValidationException;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** 최종 Snapshot의 시각 자료 의존성과 semantic 자산 계약을 함께 검증한다. */
@Component
public final class VisualSnapshotConsistencyValidator {

    private static final Pattern DEICTIC_VISUAL_REFERENCE = Pattern.compile(
            "(?:다음|아래|위|오른쪽|왼쪽|주어진)\\s*(?:의\\s*)?(?:그림|그래프|좌표평면|표)"
                    + "|(?:그림|그래프|좌표평면|표)\\s*(?:을|를)\\s*보고"
                    + "|(?:그림|그래프|좌표평면|표)\\s*에서"
                    + "|다음은[^.!?]{0,120}(?:그림|그래프|좌표평면|표)(?:이다|입니다)"
                    + "|(?:알맞은|옳은|해당하는)\\s*(?:그림|그래프|표)\\s*(?:을|를)\\s*(?:고르|찾)");
    private static final Pattern CIRCLED_LABEL = Pattern.compile("[㉠-㉻]");
    private static final Pattern VIEW_REFERENCE = Pattern.compile(
            "보기\\s*(?:에서|중|의|를|로부터)|보기\\s*$");
    private static final Pattern VIEW_LABEL_DEFINITION = Pattern.compile(
            "([ㄱ-ㅎᄀ-ᄒ①-⑩])\\s*[.．:：)]\\s*\\S");
    private static final Set<Character> NON_DEFINITION_PREFIXES = Set.of(
            ',', '，', '、', ')', '）', ']', '］', '}', '｝', '/', '·', 'ㆍ');

    /** 시각 자료가 필요한 semantic 후보의 표시 계약을 검증한다. */
    public void validate(ProblemSemanticModelV1 model, QuestionSnapshotV1 snapshot,
                         List<GeneratedAssetPlan> plans) {
        boolean required = model != null && model.intent() != null && model.intent().visualRequired();
        var actualPlans = plans == null ? List.<GeneratedAssetPlan>of() : plans;
        var assets = snapshot.assets() == null ? List.<com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference>of() : snapshot.assets();
        if (required && (assets.isEmpty() || actualPlans.isEmpty())) {
            throw new IllegalArgumentException("시각 필수 문항에는 자산 계획과 Snapshot 자산이 필요합니다.");
        }
        if (!required && !assets.isEmpty()) {
            throw new IllegalArgumentException("시각 비필수 문항에는 자산을 포함할 수 없습니다.");
        }
        var keys = assets.stream().map(a -> a.assetKey()).toList();
        var planKeys = actualPlans.stream().map(GeneratedAssetPlan::assetKey).toList();
        if (!keys.equals(planKeys) || new HashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("Snapshot 자산과 계획의 key가 일치하지 않습니다.");
        }
        if (required && snapshot.contentBlocks().stream().noneMatch(block ->
                block.blockKind() == com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind.FIGURE
                        && block.assetRef() != null)) {
            throw new IllegalArgumentException("시각 필수 문항에는 FIGURE assetRef가 필요합니다.");
        }
        validateFinal(snapshot);
    }

    /** 이미지·표 부착이 끝난 Snapshot이 학생에게 제공된 정보만으로 해석 가능한지 검증한다. */
    public void validateFinal(QuestionSnapshotV1 snapshot) {
        List<String> violations = violations(snapshot);
        if (!violations.isEmpty()) {
            throw new SnapshotValidationException(violations);
        }
    }

    /** 문제은행 격리와 생성 교정 피드백에 사용할 최종 시각 위반을 반환한다. */
    public List<String> violations(QuestionSnapshotV1 snapshot) {
        if (snapshot == null) return List.of("snapshot: null일 수 없습니다.");

        List<String> problemTexts = studentProblemTexts(snapshot);
        List<String> definitionTexts = new ArrayList<>(problemTexts);
        if (snapshot.assets() != null) {
            snapshot.assets().stream().filter(java.util.Objects::nonNull)
                    .map(asset -> asset.altText()).filter(this::hasText)
                    .forEach(definitionTexts::add);
        }
        if (snapshot.contentBlocks() != null) {
            snapshot.contentBlocks().stream().filter(java.util.Objects::nonNull)
                    .filter(block -> block.blockKind() == SnapshotBlockKind.TABLE)
                    .map(block -> block.markup()).filter(this::hasText)
                    .forEach(definitionTexts::add);
        }

        boolean hasVisualMaterial = snapshot.contentBlocks() != null
                && snapshot.contentBlocks().stream().filter(java.util.Objects::nonNull)
                .anyMatch(block -> block.blockKind() == SnapshotBlockKind.FIGURE
                        || block.blockKind() == SnapshotBlockKind.TABLE);
        List<String> violations = new ArrayList<>();
        if (!hasVisualMaterial && problemTexts.stream().anyMatch(this::hasDeicticVisualReference)) {
            violations.add("visualDependency: 실제 그림·그래프·표 없이 시각 자료를 참조할 수 없습니다.");
        }

        Set<String> referencedLabels = new LinkedHashSet<>();
        for (String text : problemTexts) {
            Matcher matcher = CIRCLED_LABEL.matcher(text);
            while (matcher.find()) referencedLabels.add(matcher.group());
        }
        List<String> unresolved = referencedLabels.stream()
                .filter(label -> definitionTexts.stream().noneMatch(text -> definesLabel(text, label)))
                .toList();
        if (!unresolved.isEmpty()) {
            violations.add("visualDependency: 의미가 정의되지 않은 참조 기호가 있습니다: "
                    + String.join(", ", unresolved));
        }

        boolean referencesView = problemTexts.stream().anyMatch(this::hasViewReference);
        boolean hasStructuredChoices = snapshot.choices() != null
                && snapshot.choices().stream().filter(java.util.Objects::nonNull).count() >= 2;
        long definedViewLabels = definitionTexts.stream()
                .flatMap(text -> viewLabelDefinitions(text).stream())
                .distinct().count();
        if (referencesView && !hasStructuredChoices && definedViewLabels < 2) {
            violations.add("referenceDependency: 보기 참조에 필요한 보기 내용이 없습니다.");
        }

        if (snapshot.metadata() != null
                && snapshot.metadata().presentation() == QuestionPresentation.TEXT_ONLY
                && hasVisualMaterial) {
            // 구조 검증이 먼저 잡는 조건이지만 최종 검증을 단독 호출하는 경계도 같은 결론을 낸다.
            violations.add("visualDependency: TEXT_ONLY에는 실제 시각 자료를 포함할 수 없습니다.");
        }
        return List.copyOf(new LinkedHashSet<>(violations));
    }

    /** 학생이 문제를 풀 때 직접 읽는 본문·보기·단계 텍스트만 수집한다. */
    private List<String> studentProblemTexts(QuestionSnapshotV1 snapshot) {
        List<String> texts = new ArrayList<>();
        if (snapshot.contentBlocks() != null) {
            snapshot.contentBlocks().stream().filter(java.util.Objects::nonNull)
                    .filter(block -> block.blockKind() == SnapshotBlockKind.TEXT)
                    .map(block -> block.text()).filter(this::hasText).forEach(texts::add);
        }
        if (snapshot.choices() != null) {
            snapshot.choices().stream().filter(java.util.Objects::nonNull)
                    .map(choice -> choice.content()).filter(this::hasText).forEach(texts::add);
        }
        if (snapshot.steps() != null) {
            snapshot.steps().stream().filter(java.util.Objects::nonNull).forEach(step -> {
                if (hasText(step.label())) texts.add(step.label());
                if (step.segments() != null) {
                    step.segments().stream().filter(java.util.Objects::nonNull)
                            .filter(segment -> segment.type() == SnapshotSegmentType.TEXT)
                            .map(segment -> segment.text()).filter(this::hasText).forEach(texts::add);
                }
            });
        }
        return List.copyOf(texts);
    }

    private boolean hasDeicticVisualReference(String text) {
        return hasText(text) && DEICTIC_VISUAL_REFERENCE.matcher(text).find();
    }

    private boolean hasViewReference(String text) {
        return hasText(text) && VIEW_REFERENCE.matcher(text).find();
    }

    /** 본문·표·altText에서 실제 내용이 뒤따르는 ㄱ·ㄴ 또는 ①·② 보기 라벨을 찾는다. */
    private Set<String> viewLabelDefinitions(String text) {
        if (!hasText(text)) return Set.of();
        Set<String> definitions = new LinkedHashSet<>();
        Matcher matcher = VIEW_LABEL_DEFINITION.matcher(text);
        while (matcher.find()) definitions.add(matcher.group(1));
        return definitions;
    }

    /** 기호 바로 뒤에 쉼표·닫는 괄호가 아닌 실제 내용이 있으면 텍스트 정의로 본다. */
    private boolean definesLabel(String text, String label) {
        if (!hasText(text)) return false;
        int from = 0;
        while (from < text.length()) {
            int index = text.indexOf(label, from);
            if (index < 0) return false;
            int cursor = index + label.length();
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
            if (cursor >= text.length()) return false;

            if (text.startsWith("은", cursor) || text.startsWith("는", cursor)) cursor++;
            else if (text.startsWith(":", cursor) || text.startsWith("：", cursor)
                    || text.startsWith("=", cursor)) cursor++;
            else if (text.startsWith("과", cursor) || text.startsWith("와", cursor)
                    || text.startsWith("및", cursor) || text.startsWith("또는", cursor)
                    || text.startsWith("중", cursor) || text.startsWith("만", cursor)) {
                from = index + label.length();
                continue;
            }
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
            if (cursor < text.length() && !NON_DEFINITION_PREFIXES.contains(text.charAt(cursor))
                    && !CIRCLED_LABEL.matcher(String.valueOf(text.charAt(cursor))).matches()) {
                return true;
            }
            from = index + label.length();
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
