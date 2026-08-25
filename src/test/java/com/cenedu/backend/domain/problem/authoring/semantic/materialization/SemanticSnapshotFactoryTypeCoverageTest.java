package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegmentType;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;

/**
 * 네 문항 유형이 모두 semantic model에서 문항으로 복원되는지 고정한다.
 *
 * <p>빈칸형·서술형은 오랫동안 steps·rubricItems가 항상 빈 값으로 나와 semantic 편집에서
 * 제외돼 있었다. 값 하나만 바꾸는 수정이 유형에 따라 되거나 안 되는 상태로 돌아가지 않도록,
 * 만들어진 Snapshot을 실제 구조 검증기에 통과시켜 확인한다.
 */
class SemanticSnapshotFactoryTypeCoverageTest {

    private final SemanticSnapshotFactory factory = new SemanticSnapshotFactory();
    private final SnapshotStructuralValidator validator = new SnapshotStructuralValidator();

    @Test
    void 빈칸형은_단계와_빈칸별_답안을_복원한다() {
        var snapshot = factory.create(stepFillModel(), values());

        assertThat(validator.violations(snapshot)).isEmpty();
        assertThat(snapshot.steps()).extracting(step -> step.stepKey()).containsExactly("ST1", "ST2");
        assertThat(snapshot.steps().getFirst().label()).isEqualTo("1단계: 좌변을 계산한다");
        assertThat(snapshot.answerUnits()).extracting(SnapshotAnswerUnit::unitKey).containsExactly("B1", "B2");
        assertThat(snapshot.answerUnits()).extracting(SnapshotAnswerUnit::stepKey).containsExactly("ST1", "ST2");
        assertThat(snapshot.answerUnits()).extracting(SnapshotAnswerUnit::answerRaw).containsExactly("5", "10");
        assertThat(snapshot.rubricItems()).isEmpty();
        assertThat(snapshot.choices()).isEmpty();
    }

    /** model이 정한 자유 키를 쓰면 구조 검증의 논리 키 형식에 걸리므로 여기서 다시 매긴다. */
    @Test
    void 빈칸형_논리_키를_ST1_B1_형식으로_정규화한다() {
        var snapshot = factory.create(stepFillModel(), values());

        assertThat(snapshot.steps().get(1).segments())
                .filteredOn(segment -> segment.type() == SnapshotSegmentType.ANSWER_REF)
                .extracting(segment -> segment.unitKey())
                .containsExactly("B1");
    }

    @Test
    void 서술형은_채점_기준과_루브릭_답안_단위를_복원한다() {
        var snapshot = factory.create(essayModel(List.of(
                new SemanticRubricTemplate("criterion-a", 0, "식을 바르게 세웠다", 60),
                new SemanticRubricTemplate("criterion-b", 1, "계산 과정을 설명했다", 40))), values());

        assertThat(validator.violations(snapshot)).isEmpty();
        assertThat(snapshot.rubricItems()).extracting(item -> item.rubricKey()).containsExactly("R1", "R2");
        assertThat(snapshot.rubricItems()).extracting(item -> item.weightPercent()).containsExactly(60, 40);
        var main = snapshot.answerUnits().getFirst();
        assertThat(main.compareMethod()).isEqualTo(CompareMethod.RUBRIC);
        assertThat(main.answerRaw()).isNull();
        assertThat(main.answerNormalized()).isNull();
    }

    /**
     * 채점 기준이 없는 서술형은 문항으로 성립하지 않으므로 빈 값을 내보내지 않고 실패해야 한다.
     *
     * <p>문제은행에는 채점 기준 없이 주관식을 서술형으로 재분류해 적재한 문항이 있다. 이런
     * 문항을 조용히 통과시키면 추출이 지어낸 채점 기준이 그대로 저장된다. 예외 메시지의
     * "지원하지"는 추출 경계가 UNSUPPORTED로 분류해 재시도하지 않게 하는 신호다.
     */
    @Test
    void 채점_기준이_없는_서술형은_지원하지_않는_구성으로_실패한다() {
        assertThatThrownBy(() -> factory.create(essayModel(List.of()), values()))
                .isInstanceOf(SemanticMaterializationException.class)
                .hasMessageContaining("지원하지");
    }

    @Test
    void 배점_합이_100이_아닌_서술형은_실패한다() {
        assertThatThrownBy(() -> factory.create(essayModel(List.of(
                new SemanticRubricTemplate("criterion-a", 0, "식을 세웠다", 50),
                new SemanticRubricTemplate("criterion-b", 1, "계산했다", 30))), values()))
                .isInstanceOf(SemanticMaterializationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void 객관식은_기존대로_보기와_정답만_만든다() {
        var snapshot = factory.create(multipleChoiceModel(), values());

        assertThat(validator.violations(snapshot)).isEmpty();
        assertThat(snapshot.choices()).extracting(choice -> choice.choiceKey()).containsExactly("C1", "C2");
        assertThat(snapshot.answerUnits().getFirst().answerRaw()).isEqualTo("C2");
        assertThat(snapshot.steps()).isEmpty();
        assertThat(snapshot.rubricItems()).isEmpty();
    }

    private Map<String, SemanticResolvedValue> values() {
        return Map.of(
                "LEFT", new SemanticResolvedValue(SemanticValueType.INTEGER, "5", null),
                "TOTAL", new SemanticResolvedValue(SemanticValueType.INTEGER, "10", null),
                "OTHER", new SemanticResolvedValue(SemanticValueType.INTEGER, "7", null));
    }

    private ProblemSemanticModelV1 stepFillModel() {
        var first = new SemanticStepTemplate("s-one", 0, "1단계: 좌변을 계산한다", List.of(
                new SemanticSegmentTemplate(SemanticSegmentType.TEXT, "2 + 3 = ", null, null, null, null, null),
                new SemanticSegmentTemplate(SemanticSegmentType.BLANK, null, "blank_a", "LEFT",
                        CompareMethod.VALUE, DiagnosticType.EXECUTE, null)));
        var second = new SemanticStepTemplate("s-two", 1, "2단계: 두 배를 구한다", List.of(
                new SemanticSegmentTemplate(SemanticSegmentType.ANSWER_REF, null, "blank_a", null, null, null, null),
                new SemanticSegmentTemplate(SemanticSegmentType.TEXT, " × 2 = ", null, null, null, null, null),
                new SemanticSegmentTemplate(SemanticSegmentType.BLANK, null, "blank_b", "TOTAL",
                        CompareMethod.VALUE, DiagnosticType.ANSWER, null)));
        return model(QuestionType.STEP_FILL, new SemanticPresentationPlan(
                "2 + 3의 값과 그 두 배를 차례로 구하시오.", List.of(), List.of(first, second),
                "2 + 3 = {{LEFT}}이고, 그 두 배는 {{TOTAL}}이다.", guide(), List.of()));
    }

    private ProblemSemanticModelV1 essayModel(List<SemanticRubricTemplate> rubrics) {
        return model(QuestionType.ESSAY, new SemanticPresentationPlan(
                "2 + 3의 값을 구하는 과정을 서술하시오.", List.of(), List.of(),
                "2와 3을 더하면 {{LEFT}}이다.", guide(), rubrics));
    }

    private ProblemSemanticModelV1 multipleChoiceModel() {
        return model(QuestionType.MULTIPLE_CHOICE, new SemanticPresentationPlan(
                "2 + 3의 값은?", List.of(
                        new SemanticChoiceTemplate("wrong", 0, "{{OTHER}}", "OTHER"),
                        new SemanticChoiceTemplate("right", 1, "{{LEFT}}", "LEFT")),
                List.of(), "2와 3을 더하면 {{LEFT}}이다.", guide(), List.of()));
    }

    private SemanticLearningGuideTemplate guide() {
        return new SemanticLearningGuideTemplate("자연수의 덧셈", "두 수를 더하는 방법을 확인한다.",
                List.of("두 수를 자리에 맞추어 더한다."));
    }

    private ProblemSemanticModelV1 model(QuestionType type, SemanticPresentationPlan presentation) {
        return new ProblemSemanticModelV1(ProblemSemanticModelV1.CURRENT_SCHEMA_VERSION,
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 10L, "수와 연산", "자연수", "덧셈"),
                new SemanticProblemIntent(type, "low", EvaluationArea.CALCULATION, "덧셈", "LEFT", 1, false),
                List.of(new SemanticParameter("LEFT", SemanticValueType.INTEGER, "5", null, true, null)),
                List.of(), List.of(), presentation, List.of(), List.of());
    }
}
