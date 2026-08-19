package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FewShotReferenceSerializerTest {
    @Test
    void serializesUsefulStructureWithoutAnswersOrExplanation() {
        String json = new FewShotReferenceSerializer().serialize(scope(), List.of(
                new GenerationReference(GenerationReferenceRole.EXAMPLE, 201L, snapshot())));
        assertThat(json).contains("EXAMPLE", "보이는 발문", "보기 1", "식 세우기", "<BLANK>");
        assertThat(json).doesNotContain("ANSWER_RAW_SENTINEL", "ANSWER_NORMALIZED_SENTINEL", "EXPLANATION_SENTINEL");
        assertThat(json).contains("directCopyForbidden", "text-only");
    }

    private static CurriculumScope scope() { return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 30L, "대", "중", "소"); }
    private static QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1, new SnapshotMetadata(QuestionType.STEP_FILL, QuestionPresentation.TEXT_ONLY, "mid", 30L, null, null, null),
                List.of(new SnapshotContentBlock("p", SnapshotBlockKind.TEXT, 1, "보이는 발문", null, null)), List.of(),
                List.of(new SnapshotChoice("C1", 1, "보기 1")),
                List.of(new SnapshotStep("s", 1, "식 세우기", List.of(new SnapshotSegment(SnapshotSegmentType.TEXT, "식 세우기", null), new SnapshotSegment(SnapshotSegmentType.BLANK, null, "B1")))),
                List.of(new SnapshotAnswerUnit("B1", "s", 1, "ANSWER_RAW_SENTINEL", "ANSWER_NORMALIZED_SENTINEL", null, null, null)),
                "EXPLANATION_SENTINEL", new SnapshotLearningGuide("개념", "요약", List.of("전략")), List.of());
    }
}
