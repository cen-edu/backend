package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;

class ProblemModificationSnapshotMergerTest {
    @Test
    void 요청하지_않은_정답과_학습안내는_원본을_유지한다() {
        QuestionSnapshotV1 base = ProblemSnapshotFixtures.shortInput();
        QuestionSnapshotV1 candidate = new QuestionSnapshotV1(1, base.metadata(),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0, "수정 문장", null, null)),
                base.assets(), base.choices(), base.steps(),
                List.of(new SnapshotAnswerUnit("MAIN", null, 0, "999", "999",
                        com.cenedu.backend.global.common.enums.CompareMethod.VALUE, null, null)),
                "변경 해설", new SnapshotLearningGuide("변경", "변경", List.of("변경")), base.rubricItems());
        ProblemEditExecutionPlan plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 1L,
                EditAction.MODIFY, ReplacementSourcePolicy.NONE, null, List.of(),
                List.of(new ProblemEditTargetRef(EditTargetType.QUESTION_BODY, null)),
                List.of(new ProblemEditTargetRef(EditTargetType.EXPLANATION, null)),
                List.of(new ProblemEditTargetRef(EditTargetType.ANSWER_UNIT, "MAIN"),
                        new ProblemEditTargetRef(EditTargetType.LEARNING_GUIDE, null)), null);

        QuestionSnapshotV1 merged = new ProblemModificationSnapshotMerger().merge(plan, base, candidate);

        assertThat(merged.contentBlocks().getFirst().text()).isEqualTo("수정 문장");
        assertThat(merged.explanation()).isEqualTo("변경 해설");
        assertThat(merged.answerUnits()).isEqualTo(base.answerUnits());
        assertThat(merged.learningGuide()).isEqualTo(base.learningGuide());
    }
}
