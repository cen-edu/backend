package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;

class ModificationPromptStrategyTest {
    @Test
    void 수정에_필요한_영역만_전송하고_서버_ID를_제외한다() {
        var base = ProblemSnapshotFixtures.shortInput();
        var plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 98765L, 87654L,
                EditAction.MODIFY, ReplacementSourcePolicy.NONE, null, List.of(),
                List.of(new ProblemEditTargetRef(EditTargetType.QUESTION_BODY, null)),
                List.of(), List.of(new ProblemEditTargetRef(EditTargetType.ANSWER_UNIT, "MAIN")), null);

        String prompt = new ModificationPromptStrategy(new tools.jackson.databind.ObjectMapper())
                .create(new ProblemModificationCommand(plan.requestId(), plan, base));

        assertThat(prompt).contains("12를 구하시오.", "protectedTargets")
                .doesNotContain("98765", "87654", "answerRaw");
    }
}
