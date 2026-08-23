package com.cenedu.backend.ai.problem.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentPayload;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** expectedOldValue를 채우려면 LLM이 현재 semantic 값을 실제로 봐야 한다는 계약을 검증한다. */
class ProblemEditPromptFactoryTest {

    @SuppressWarnings("unchecked")
    private ProblemEditPromptFactory factory() {
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        return new ProblemEditPromptFactory(provider);
    }

    @Test
    void semantic_model이_있으면_현재_파라미터_값을_프롬프트에_그대로_노출한다() {
        var parameter = new SemanticParameter("RADIUS", SemanticValueType.INTEGER, "3", "cm", true, null);
        var intent = new SemanticProblemIntent(QuestionType.SHORT_INPUT, "mid", null, "identity", "R", 1, false);
        var presentation = new SemanticPresentationPlan("{{RADIUS}}", List.of(), List.of(), "", null, List.of());
        var model = new ProblemSemanticModelV1(1,
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "a", "b", "c"),
                intent, List.of(parameter), List.of(), List.of(), presentation, List.of(), List.of());
        var payload = new ProblemEditAgentPayload(2, UUID.randomUUID(), 1L, 20L,
                AuthoringInteractionStatus.COLLECTING, null, ProblemSnapshotFixtures.shortInput(), model, List.of());

        String prompt = factory().create(payload);

        assertThat(prompt).contains("RADIUS").contains("\"value\":\"3\"").contains("currentSemanticValues");
    }

    @Test
    void editable이_false인_파라미터는_PARAMETRIC_PATCH_대신_STRUCTURAL_REGENERATION을_쓰도록_안내한다() {
        var payload = new ProblemEditAgentPayload(1, 1L, 2L,
                AuthoringInteractionStatus.COLLECTING, null, ProblemSnapshotFixtures.shortInput(), List.of());

        String prompt = factory().create(payload);

        assertThat(prompt).contains("editable").contains("STRUCTURAL_REGENERATION으로 분류한다");
    }

    @Test
    void semantic_model이_없으면_빈_currentSemanticValues를_반환한다() {
        var payload = new ProblemEditAgentPayload(1, 1L, 2L,
                AuthoringInteractionStatus.COLLECTING, null, ProblemSnapshotFixtures.shortInput(), List.of());

        String prompt = factory().create(payload);

        assertThat(prompt).contains("currentSemanticValues").contains("{}");
    }
}
