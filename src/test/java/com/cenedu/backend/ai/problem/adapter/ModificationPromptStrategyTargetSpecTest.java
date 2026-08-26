package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.edit.RequestedProblemSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.global.common.enums.CompareMethod;

import tools.jackson.databind.ObjectMapper;

class ModificationPromptStrategyTargetSpecTest {

    private final ModificationPromptStrategy strategy =
            new ModificationPromptStrategy(new ObjectMapper());

    @Test
    void 목표_난이도와_문항_유형을_프롬프트에_드러낸다() {
        String prompt = strategy.create(command(
                new RequestedProblemSpecification(QuestionType.ESSAY, "high")));

        assertThat(prompt).contains("\"questionType\":\"ESSAY\"");
        assertThat(prompt).contains("\"difficulty\":\"high\"");
        assertThat(prompt).contains("targetSpecification이 우선한다");
    }

    @Test
    void 교체_요청이_없으면_현재_분류를_유지하라고_알린다() {
        String prompt = strategy.create(command(null));

        assertThat(prompt).contains("targetSpecification=null (현재 분류 유지)");
    }

    @Test
    void 유사_문항은_EXAMPLE로_넣되_정답과_해설은_제거한다() {
        ProblemEditExecutionPlan plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L,
                EditAction.REPLACE, ReplacementSourcePolicy.GENERATE_ONLY, null, List.of(), null,
                List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), new RequestedProblemSpecification(QuestionType.SHORT_INPUT, "mid"));
        var reference = new GenerationReference(GenerationReferenceRole.EXAMPLE, 91L,
                referenceSnapshot());
        var command = new ProblemModificationCommand(plan.requestId(), plan, snapshot(), null,
                List.of(), curriculum(), List.of(reference), List.of());

        String prompt = strategy.create(command);

        assertThat(prompt).contains("fewShotExamples=")
                .contains("유사 문항 본문 91")
                .contains("\"role\":\"EXAMPLE\"")
                .doesNotContain("SECRET_ANSWER_91")
                .doesNotContain("SECRET_EXPLANATION_91");
    }

    private ProblemModificationCommand command(RequestedProblemSpecification specification) {
        ProblemEditExecutionPlan plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L,
                EditAction.REPLACE, ReplacementSourcePolicy.GENERATE_ONLY, null, List.of(), null,
                List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), specification);
        return new ProblemModificationCommand(plan.requestId(), plan, snapshot());
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE, QuestionPresentation.TEXT_ONLY,
                        "low", 10L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "3+4를 계산하시오.", null, null)),
                List.of(), List.of(), List.of(), List.of(), "7이다.", null, List.of());
    }

    private QuestionSnapshotV1 referenceSnapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY,
                        "mid", 10L, null, null, 91L),
                List.of(new SnapshotContentBlock("CB91", SnapshotBlockKind.TEXT, 0,
                        "유사 문항 본문 91", null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SnapshotAnswerUnit("MAIN", null, 0, "SECRET_ANSWER_91",
                        "SECRET_ANSWER_91", CompareMethod.EXACT, null, null)),
                "SECRET_EXPLANATION_91", null, List.of());
    }

    private CurriculumScope curriculum() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                null, 10L, "수와 연산", "정수와 유리수", "유리수의 계산");
    }
}
