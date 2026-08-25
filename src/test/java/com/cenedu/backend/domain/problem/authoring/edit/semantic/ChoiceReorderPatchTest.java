package com.cenedu.backend.domain.problem.authoring.edit.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * 보기 순서만 바꾸는 수정이 AI 재생성 없이 성립하는지 고정한다.
 *
 * <p>순서 변경은 정답이 가리키는 보기 키를 반드시 바꾸므로 "정답 단위가 그대로여야 한다"는
 * 표현 수정의 불변식을 쓸 수 없다. 대신 보기 집합이 그대로이고 displayOrder가 올바른 순열인지를
 * 불변식으로 삼는데, 그 경계가 무너지면 순서 변경을 빌미로 보기 내용까지 바뀔 수 있다.
 */
class ChoiceReorderPatchTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();

    private final ProblemSemanticPatchApplier applier = new ProblemSemanticPatchApplier();
    private final ProblemSemanticPatchClassifier classifier = new ProblemSemanticPatchClassifier();
    private final DefaultProblemSemanticMaterializer materializer = new DefaultProblemSemanticMaterializer();

    @Test
    void 순서_변경_operation만_있으면_CHOICE_REORDER로_분류한다() {
        assertThat(classifier.classify(swapPatch(SemanticEditMode.CHOICE_REORDER)))
                .isEqualTo(SemanticEditMode.CHOICE_REORDER);
        assertThat(ProblemSemanticPatchPath.isChoiceOrder("/presentation/choices/right/displayOrder")).isTrue();
        assertThat(ProblemSemanticPatchPath.isAllowed("/presentation/choices/right/displayOrder")).isTrue();
    }

    /** 값·표현 수정과 섞이면 어느 불변식으로 검증할지 정할 수 없어 거부해야 한다. */
    @Test
    void 순서_변경과_표현_수정이_섞이면_거부한다() {
        var patch = new ProblemSemanticPatch(ProblemSemanticPatch.CURRENT_SCHEMA_VERSION, REQUEST_ID, 2L,
                SemanticEditMode.CHOICE_REORDER, List.of(
                order("right", "1", "0"),
                new SemanticPatchOperation(SemanticPatchOperationType.SET_TEMPLATE_TEXT,
                        "/presentation/explanationTemplate", "2와 3을 더하면 {{LEFT}}이다.", "{{LEFT}}이다.")),
                "보기 순서를 바꿔 주세요");

        assertThat(classifier.classify(patch)).isEqualTo(SemanticEditMode.REJECTED);
    }

    @Test
    void 보기_순서를_바꾸면_정답_키는_바뀌고_정답_내용은_그대로다() {
        var before = materializer.materialize(model());
        assertThat(before.snapshot().choices()).extracting(SnapshotChoice::content).containsExactly("7", "5");
        assertThat(before.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("C2");

        var reordered = applier.apply(model(), swapPatch(SemanticEditMode.CHOICE_REORDER));
        var after = materializer.materialize(reordered);

        assertThat(after.snapshot().choices()).extracting(SnapshotChoice::content).containsExactly("5", "7");
        assertThat(after.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("C1");
        assertThat(correctContent(after.snapshot().choices(),
                after.snapshot().answerUnits().getFirst().answerRaw())).isEqualTo("5");
    }

    @Test
    void 순서_변경으로_보기_내용을_바꿀_수는_없다() {
        var patch = new ProblemSemanticPatch(ProblemSemanticPatch.CURRENT_SCHEMA_VERSION, REQUEST_ID, 2L,
                SemanticEditMode.CHOICE_REORDER, List.of(
                new SemanticPatchOperation(SemanticPatchOperationType.SET_CHOICE_ORDER,
                        "/presentation/choices/right/contentTemplate", "{{LEFT}}", "{{OTHER}}")),
                "보기를 바꿔 주세요");

        assertThatThrownBy(() -> applier.apply(model(), patch))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayOrder가_연속된_순열이_아니면_거부한다() {
        var patch = new ProblemSemanticPatch(ProblemSemanticPatch.CURRENT_SCHEMA_VERSION, REQUEST_ID, 2L,
                SemanticEditMode.CHOICE_REORDER, List.of(order("right", "1", "5")),
                "보기 순서를 바꿔 주세요");

        assertThatThrownBy(() -> applier.apply(model(), patch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("연속");
    }

    /** 순서가 그대로면 아무것도 바뀌지 않은 새 버전이 생겨 교사가 수정된 것으로 오해한다. */
    @Test
    void 지금과_같은_순서를_요청하면_거부한다() {
        var patch = new ProblemSemanticPatch(ProblemSemanticPatch.CURRENT_SCHEMA_VERSION, REQUEST_ID, 2L,
                SemanticEditMode.CHOICE_REORDER, List.of(order("right", "1", "1")),
                "보기 순서를 바꿔 주세요");

        assertThatThrownBy(() -> applier.apply(model(), patch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재와 같습니다");
    }

    private String correctContent(List<SnapshotChoice> choices, String choiceKey) {
        return choices.stream().filter(choice -> choice.choiceKey().equals(choiceKey))
                .map(SnapshotChoice::content).findFirst().orElseThrow();
    }

    private ProblemSemanticPatch swapPatch(SemanticEditMode mode) {
        return new ProblemSemanticPatch(ProblemSemanticPatch.CURRENT_SCHEMA_VERSION, REQUEST_ID, 2L, mode,
                List.of(order("wrong", "0", "1"), order("right", "1", "0")), "보기 순서를 바꿔 주세요");
    }

    private SemanticPatchOperation order(String choiceKey, String from, String to) {
        return new SemanticPatchOperation(SemanticPatchOperationType.SET_CHOICE_ORDER,
                "/presentation/choices/" + choiceKey + "/displayOrder", from, to);
    }

    private ProblemSemanticModelV1 model() {
        var presentation = new SemanticPresentationPlan("2 + 3의 값은?",
                List.of(new SemanticChoiceTemplate("wrong", 0, "{{OTHER}}", "OTHER"),
                        new SemanticChoiceTemplate("right", 1, "{{LEFT}}", "LEFT")),
                List.of(), "2와 3을 더하면 {{LEFT}}이다.",
                new SemanticLearningGuideTemplate("자연수의 덧셈", "두 수를 더하는 방법을 확인한다.",
                        List.of("두 수를 자리에 맞추어 더한다.")),
                List.of());
        return new ProblemSemanticModelV1(ProblemSemanticModelV1.CURRENT_SCHEMA_VERSION,
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 10L, "수와 연산", "자연수", "덧셈"),
                new SemanticProblemIntent(QuestionType.MULTIPLE_CHOICE, "low", EvaluationArea.CALCULATION,
                        "덧셈", "LEFT", 1, false),
                List.of(new SemanticParameter("LEFT", SemanticValueType.INTEGER, "5", null, true, null),
                        new SemanticParameter("OTHER", SemanticValueType.INTEGER, "7", null, true, null)),
                List.of(), List.of(), presentation, List.of(), List.of());
    }
}
