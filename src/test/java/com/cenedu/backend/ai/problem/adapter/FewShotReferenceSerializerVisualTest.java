package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceDescriptor;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FewShotReferenceSerializerVisualTest {

    @Test
    void 시각_참조의_종류와_대체텍스트를_few_shot에_포함한다() {
        var snapshot = new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE,
                        QuestionPresentation.WITH_FIGURE, "mid", 20L, null, null, null),
                List.of(), List.of(new SnapshotAssetReference("F1", "정비례 그래프")),
                List.of(), List.of(), List.of(), null, null, List.of());
        var visual = new VisualReferenceDescriptor("F1", VisualReferenceKind.COORDINATE_GRAPH,
                AssetRole.FIGURE, "정비례 그래프", null);
        var reference = new GenerationReference(GenerationReferenceRole.ORIGIN, 10L,
                snapshot, null, visual);

        String json = new FewShotReferenceSerializer().serialize(scope(), List.of(reference));

        assertThat(json).contains("\"visualReference\"")
                .contains("\"visualKind\":\"COORDINATE_GRAPH\"")
                .contains("정비례 그래프")
                .contains("\"directCopyForbidden\":true");
    }

    private CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 20L,
                "변화와 관계", "좌표와 그래프", "좌표평면과 그래프");
    }
}
