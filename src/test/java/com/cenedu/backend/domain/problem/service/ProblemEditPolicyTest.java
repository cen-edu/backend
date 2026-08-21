package com.cenedu.backend.domain.problem.service;

import static com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures.shortInput;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.edit.ConfirmedProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditChangeNature;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.edit.RequestedProblemSpecification;
import com.cenedu.backend.domain.problem.authoring.edit.RestoreReference;
import com.cenedu.backend.domain.problem.authoring.edit.RestoreReferenceType;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemEditPolicyTest {

    private final ProblemEditPolicy policy = new ProblemEditPolicy();

    @Test
    @DisplayName("문제 본문의 의미 수정은 정답·해설·학습 안내를 의존 대상으로 지정한다")
    void computesDependentAndProtectedTargets() {
        ConfirmedProblemEditCommand command = command(
                List.of(new ProblemEditInstruction(
                        EditTargetType.QUESTION_BODY, null,
                        EditChangeNature.SEMANTIC, "수치를 바꾸어 주세요")),
                null, null, ReplacementSourcePolicy.NONE);

        ProblemEditExecutionPlan plan = policy.plan(command, shortInput(), null);

        assertThat(plan.action()).isEqualTo(EditAction.MODIFY);
        assertThat(plan.dependentTargets()).contains(
                new ProblemEditTargetRef(EditTargetType.CONTENT_BLOCK, "CB1"),
                new ProblemEditTargetRef(EditTargetType.ANSWER_UNIT, "MAIN"),
                new ProblemEditTargetRef(EditTargetType.EXPLANATION, null),
                new ProblemEditTargetRef(EditTargetType.LEARNING_GUIDE, null));
        assertThat(plan.protectedTargets()).contains(
                new ProblemEditTargetRef(EditTargetType.DIFFICULTY, null));
        assertThat(plan.protectedTargets()).doesNotContain(
                new ProblemEditTargetRef(EditTargetType.CONTENT_BLOCK, "CB1"));
    }

    @Test
    @DisplayName("발문 표현 수정도 실제 저장 필드인 본문 블록을 의존 대상으로 둔다")
    void questionBodyPresentationAllowsContentBlockChange() {
        ConfirmedProblemEditCommand command = command(
                List.of(new ProblemEditInstruction(
                        EditTargetType.QUESTION_BODY, null,
                        EditChangeNature.PRESENTATIONAL, "발문을 다듬어 주세요")),
                null, null, ReplacementSourcePolicy.NONE);

        ProblemEditExecutionPlan plan = policy.plan(command, shortInput(), null);

        assertThat(plan.dependentTargets()).contains(
                new ProblemEditTargetRef(EditTargetType.CONTENT_BLOCK, "CB1"));
        assertThat(plan.protectedTargets()).doesNotContain(
                new ProblemEditTargetRef(EditTargetType.CONTENT_BLOCK, "CB1"));
    }

    @Test
    @DisplayName("오답 보기의 의미 수정은 정답 단위를 보호하고 해설만 의존 대상으로 둔다")
    void semanticDistractorChoiceProtectsAnswerUnit() {
        ConfirmedProblemEditCommand command = command(
                List.of(new ProblemEditInstruction(
                        EditTargetType.CHOICE, "C2",
                        EditChangeNature.SEMANTIC, "2번 오답 보기를 수정해 주세요")),
                null, null, ReplacementSourcePolicy.NONE);

        ProblemEditExecutionPlan plan = policy.plan(command, multipleChoice(), null);

        assertThat(plan.dependentTargets()).contains(
                new ProblemEditTargetRef(EditTargetType.EXPLANATION, null));
        assertThat(plan.dependentTargets()).doesNotContain(
                new ProblemEditTargetRef(EditTargetType.ANSWER_UNIT, "MAIN"));
        assertThat(plan.protectedTargets()).contains(
                new ProblemEditTargetRef(EditTargetType.ANSWER_UNIT, "MAIN"),
                new ProblemEditTargetRef(EditTargetType.CHOICE, "C1"),
                new ProblemEditTargetRef(EditTargetType.CHOICE, "C3"));
    }

    @Test
    @DisplayName("유사 유형 교체는 문제은행을 먼저 보는 BANK_FIRST 전체 교체다")
    void plansBankFirstReplacement() {
        ConfirmedProblemEditCommand command = command(
                List.of(),
                new RequestedProblemSpecification(QuestionType.MULTIPLE_CHOICE, "mid"),
                null,
                ReplacementSourcePolicy.BANK_FIRST);

        ProblemEditExecutionPlan plan = policy.plan(command, shortInput(), null);

        assertThat(plan.action()).isEqualTo(EditAction.REPLACE);
        assertThat(plan.sourcePolicy()).isEqualTo(ReplacementSourcePolicy.BANK_FIRST);
        assertThat(plan.requestedTargets()).containsExactly(
                new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null));
    }

    @Test
    @DisplayName("이전 PASSED Version 복원은 AI 수정 대신 RESTORE로 분류한다")
    void plansRestore() {
        ConfirmedProblemEditCommand command = command(
                List.of(), null,
                new RestoreReference(RestoreReferenceType.PREVIOUS, null),
                ReplacementSourcePolicy.NONE);

        ProblemEditExecutionPlan plan = policy.plan(command, shortInput(), 8L);

        assertThat(plan.action()).isEqualTo(EditAction.RESTORE);
        assertThat(plan.restoreVersionId()).isEqualTo(8L);
    }

    private ConfirmedProblemEditCommand command(
            List<ProblemEditInstruction> instructions,
            RequestedProblemSpecification specification,
            RestoreReference restore,
            ReplacementSourcePolicy sourcePolicy
    ) {
        return new ConfirmedProblemEditCommand(
                UUID.randomUUID(), UUID.randomUUID(), 3L, 10L,
                instructions, specification, restore, sourcePolicy);
    }

    private QuestionSnapshotV1 multipleChoice() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE,
                        QuestionPresentation.TEXT_ONLY, "low", 13L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "옳은 것을 고르시오.", null, null)),
                List.of(),
                List.of(new SnapshotChoice("C1", 0, "1"),
                        new SnapshotChoice("C2", 1, "2"),
                        new SnapshotChoice("C3", 2, "3")),
                List.of(),
                List.of(new SnapshotAnswerUnit("MAIN", null, 0, "C3", "C3",
                        CompareMethod.CHOICE, null, null)),
                "정답은 C3이다.",
                new SnapshotLearningGuide("선택", "조건을 확인한다.", List.of()),
                List.of());
    }
}
