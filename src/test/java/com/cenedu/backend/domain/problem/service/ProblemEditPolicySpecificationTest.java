package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.edit.ConfirmedProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.edit.RequestedProblemSpecification;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticPatch;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticPatchOperation;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticPatchOperationType;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;

class ProblemEditPolicySpecificationTest {

    private final ProblemEditPolicy policy = new ProblemEditPolicy();

    @Test
    void 난이도_변경은_파라메트릭_패치로_와도_문항_전체_재생성이_된다() {
        var plan = policy.plan(command(parametricPatch(), difficulty("low")), snapshot(), null);

        assertThat(plan.action()).isEqualTo(EditAction.REPLACE);
        assertThat(plan.semanticPatch().mode()).isEqualTo(SemanticEditMode.STRUCTURAL_REGENERATION);
        assertThat(plan.semanticPatch().operations()).isEmpty();
        assertThat(plan.requestedSpecification().difficulty()).isEqualTo("low");
    }

    @Test
    void 정규화된_패치도_교사_요청_문장을_그대로_들고_간다() {
        var plan = policy.plan(command(parametricPatch(), difficulty("high")), snapshot(), null);

        assertThat(plan.semanticPatch().assistantMessage()).isEqualTo("난이도를 올려 주세요");
        assertThat(plan.semanticPatch().requestId()).isEqualTo(plan.requestId());
    }

    @Test
    void 스펙_요청이_없으면_파라메트릭_패치는_부분_수정으로_남는다() {
        var plan = policy.plan(command(parametricPatch(), null), snapshot(), null);

        assertThat(plan.action()).isEqualTo(EditAction.MODIFY);
        assertThat(plan.semanticPatch().mode()).isEqualTo(SemanticEditMode.PARAMETRIC_PATCH);
        assertThat(plan.semanticPatch().operations()).hasSize(1);
    }

    private ConfirmedProblemEditCommand command(ProblemSemanticPatch patch,
            RequestedProblemSpecification specification) {
        return new ConfirmedProblemEditCommand(REQUEST_ID, UUID.randomUUID(), 1L, 2L, List.of(),
                patch, specification, null,
                specification == null ? ReplacementSourcePolicy.NONE : ReplacementSourcePolicy.GENERATE_ONLY);
    }

    private static final UUID REQUEST_ID = UUID.randomUUID();

    private ProblemSemanticPatch parametricPatch() {
        return new ProblemSemanticPatch(ProblemSemanticPatch.CURRENT_SCHEMA_VERSION, REQUEST_ID, 2L,
                SemanticEditMode.PARAMETRIC_PATCH,
                List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE,
                        "/parameters/RADIUS/value", "3", "5")),
                "난이도를 올려 주세요");
    }

    private RequestedProblemSpecification difficulty(String difficulty) {
        return new RequestedProblemSpecification(null, difficulty);
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY,
                        "mid", 10L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "반지름이 3cm인 원의 넓이를 구하시오.", null, null)),
                List.of(), List.of(), List.of(), List.of(), "해설", null, List.of());
    }
}
