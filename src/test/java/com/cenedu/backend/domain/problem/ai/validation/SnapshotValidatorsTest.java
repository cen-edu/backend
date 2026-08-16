package com.cenedu.backend.domain.problem.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotValidatorsTest {

    private SnapshotStructuralValidator structuralValidator;
    private SnapshotNormalizedValidator normalizedValidator;

    @BeforeEach
    void setUp() {
        structuralValidator = new SnapshotStructuralValidator();
        normalizedValidator = new SnapshotNormalizedValidator(structuralValidator);
    }

    @Test
    @DisplayName("네 문제 유형의 정상 스냅샷을 허용한다")
    void acceptsValidSnapshotsForAllQuestionTypes() {
        assertThatCode(() -> structuralValidator.validate(multipleChoiceSnapshot()))
                .doesNotThrowAnyException();
        assertThatCode(() -> structuralValidator.validate(shortInputSnapshot(
                CompareMethod.VALUE, "12")))
                .doesNotThrowAnyException();
        assertThatCode(() -> structuralValidator.validate(stepFillSnapshot(false)))
                .doesNotThrowAnyException();
        assertThatCode(() -> structuralValidator.validate(essaySnapshot()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("앞선 BLANK가 없는 ANSWER_REF를 거부한다")
    void rejectsForwardAnswerReference() {
        assertThatThrownBy(() -> structuralValidator.validate(stepFillSnapshot(true)))
                .isInstanceOfSatisfying(SnapshotValidationException.class, exception ->
                        assertThat(exception.violations())
                                .anyMatch(message -> message.contains("앞에서 등장한 BLANK")));
    }

    @Test
    @DisplayName("실행 가능한 TABLE markup을 거부한다")
    void rejectsExecutableTableMarkup() {
        QuestionSnapshotV1 snapshot = shortInputWithMarkup(
                "<table><tbody><tr><td>1</td></tr></tbody></table><script>alert(1)</script>");

        assertThatThrownBy(() -> structuralValidator.validate(snapshot))
                .isInstanceOfSatisfying(SnapshotValidationException.class, exception ->
                        assertThat(exception.violations())
                                .anyMatch(message -> message.contains("script/style")));
    }

    @Test
    @DisplayName("VALUE 답안은 정규화 이후 answerNormalized가 필요하다")
    void requiresNormalizedValueAfterNormalization() {
        QuestionSnapshotV1 snapshot = shortInputSnapshot(CompareMethod.VALUE, null);

        assertThatCode(() -> structuralValidator.validate(snapshot))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> normalizedValidator.validate(snapshot))
                .isInstanceOfSatisfying(SnapshotValidationException.class, exception ->
                        assertThat(exception.violations())
                                .anyMatch(message -> message.contains("정규화 후 필수")));
    }

    @Test
    @DisplayName("SUBST는 팀 협의 전까지 answerNormalized가 없어도 허용한다")
    void allowsSubstitutionWithoutNormalizedAnswer() {
        QuestionSnapshotV1 snapshot = shortInputSnapshot(CompareMethod.SUBST, null);

        assertThatCode(() -> normalizedValidator.validate(snapshot))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AI 출력의 null 필드를 예외 중단 없이 위반 목록으로 수집한다")
    void collectsNullFieldsAsViolations() {
        QuestionSnapshotV1 snapshot = new QuestionSnapshotV1(
                1,
                new SnapshotMetadata(
                        QuestionType.SHORT_INPUT,
                        QuestionPresentation.TEXT_ONLY,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> structuralValidator.validate(snapshot))
                .isInstanceOfSatisfying(SnapshotValidationException.class, exception ->
                        assertThat(exception.violations())
                                .anyMatch(message -> message.contains("metadata.difficulty"))
                                .anyMatch(message -> message.contains("contentBlocks"))
                                .anyMatch(message -> message.contains("learningGuide")));
    }

    private QuestionSnapshotV1 multipleChoiceSnapshot() {
        return snapshot(
                metadata(QuestionType.MULTIPLE_CHOICE, QuestionPresentation.WITH_FIGURE),
                List.of(
                        textBlock(),
                        new SnapshotContentBlock(
                                "CB2", SnapshotBlockKind.FIGURE, 1, null, "F1", null)
                ),
                List.of(new SnapshotAssetReference(
                        "F1", "직각삼각형의 두 변에 3과 4가 표시된 그림")),
                List.of(
                        new SnapshotChoice("C1", 0, "3"),
                        new SnapshotChoice("C2", 1, "5")
                ),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, "C2", null,
                        CompareMethod.CHOICE, null, null)),
                List.of()
        );
    }

    private QuestionSnapshotV1 shortInputSnapshot(
            CompareMethod method, String normalized
    ) {
        return snapshot(
                metadata(QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY),
                List.of(textBlock()),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, "12", normalized, method, null, "cm")),
                List.of()
        );
    }

    private QuestionSnapshotV1 shortInputWithMarkup(String markup) {
        return snapshot(
                metadata(QuestionType.SHORT_INPUT, QuestionPresentation.WITH_TABLE),
                List.of(
                        textBlock(),
                        new SnapshotContentBlock(
                                "CB2", SnapshotBlockKind.TABLE, 1, null, null, markup)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, "1", "1",
                        CompareMethod.VALUE, null, null)),
                List.of()
        );
    }

    private QuestionSnapshotV1 stepFillSnapshot(boolean forwardReference) {
        List<SnapshotSegment> firstSegments = forwardReference
                ? List.of(
                        new SnapshotSegment(SnapshotSegmentType.ANSWER_REF, null, "B1"),
                        new SnapshotSegment(SnapshotSegmentType.TEXT, "x = ", null),
                        new SnapshotSegment(SnapshotSegmentType.BLANK, null, "B1")
                )
                : List.of(
                        new SnapshotSegment(SnapshotSegmentType.TEXT, "x = ", null),
                        new SnapshotSegment(SnapshotSegmentType.BLANK, null, "B1")
                );
        return snapshot(
                metadata(QuestionType.STEP_FILL, QuestionPresentation.TEXT_ONLY),
                List.of(textBlock()),
                List.of(),
                List.of(),
                List.of(
                        new SnapshotStep("ST1", 0, "조건과 개념 이해하기", firstSegments),
                        new SnapshotStep(
                                "ST2",
                                1,
                                "답 확인하기",
                                List.of(
                                        new SnapshotSegment(
                                                SnapshotSegmentType.ANSWER_REF, null, "B1"),
                                        new SnapshotSegment(
                                                SnapshotSegmentType.TEXT, "에 1을 더하면 ", null),
                                        new SnapshotSegment(
                                                SnapshotSegmentType.BLANK, null, "B2")
                                ))
                ),
                List.of(
                        new SnapshotAnswerUnit(
                                "B1", "ST1", 0, "2", "2",
                                CompareMethod.VALUE, DiagnosticType.INTERPRET, null),
                        new SnapshotAnswerUnit(
                                "B2", "ST2", 1, "3", "3",
                                CompareMethod.VALUE, DiagnosticType.ANSWER, null)
                ),
                List.of()
        );
    }

    private QuestionSnapshotV1 essaySnapshot() {
        return snapshot(
                metadata(QuestionType.ESSAY, QuestionPresentation.TEXT_ONLY),
                List.of(textBlock()),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, null, null,
                        CompareMethod.RUBRIC, null, null)),
                List.of(
                        new SnapshotRubricItem("R1", 0, "조건을 식으로 표현한다.", 40),
                        new SnapshotRubricItem("R2", 1, "풀이를 논리적으로 전개한다.", 60)
                )
        );
    }

    private QuestionSnapshotV1 snapshot(
            SnapshotMetadata metadata,
            List<SnapshotContentBlock> blocks,
            List<SnapshotAssetReference> assets,
            List<SnapshotChoice> choices,
            List<SnapshotStep> steps,
            List<SnapshotAnswerUnit> answerUnits,
            List<SnapshotRubricItem> rubrics
    ) {
        return new QuestionSnapshotV1(
                QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                metadata,
                blocks,
                assets,
                choices,
                steps,
                answerUnits,
                "풀이 과정을 설명한다.",
                new SnapshotLearningGuide(
                        "비례 관계", "비례 관계를 활용하여 문제를 해결한다.",
                        List.of("두 양 사이의 관계를 파악하는 것이 중요하다.")),
                rubrics
        );
    }

    private SnapshotMetadata metadata(
            QuestionType type, QuestionPresentation presentation
    ) {
        return new SnapshotMetadata(type, presentation, "mid", 1L, null, null, null);
    }

    private SnapshotContentBlock textBlock() {
        return new SnapshotContentBlock(
                "CB1", SnapshotBlockKind.TEXT, 0,
                "다음 문제를 해결하시오.", null, null);
    }
}
