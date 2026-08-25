package com.cenedu.backend.domain.problem.authoring.visual;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;

class VisualSnapshotConsistencyValidatorTest {

    private final VisualSnapshotConsistencyValidator validator =
            new VisualSnapshotConsistencyValidator();

    @Test
    void 자료없이_풀수있는_TEXT_ONLY는_허용한다() {
        var snapshot = snapshot("함수 $y=2x$에서 $x=3$일 때 $y$를 구하시오.",
                QuestionPresentation.TEXT_ONLY, List.of(), List.of());

        assertThat(validator.violations(snapshot)).isEmpty();
    }

    @Test
    void 실제_그래프가_없는_지시형_TEXT_ONLY는_차단한다() {
        var direct = snapshot("다음 그래프를 보고 알맞은 값을 고르시오.",
                QuestionPresentation.TEXT_ONLY, List.of(), List.of());
        var bankPhrase = snapshot("다음은 시간에 따른 물의 양의 변화를 나타낸 그래프이다. "
                        + "다음 내용에 알맞은 그래프를 찾으시오.",
                QuestionPresentation.TEXT_ONLY, List.of(), List.of());

        assertThat(validator.violations(direct))
                .contains("visualDependency: 실제 그림·그래프·표 없이 시각 자료를 참조할 수 없습니다.");
        assertThat(validator.violations(bankPhrase))
                .contains("visualDependency: 실제 그림·그래프·표 없이 시각 자료를 참조할 수 없습니다.");
    }

    @Test
    void 내용이_없는_참조기호는_차단하고_텍스트로_정의하면_허용한다() {
        var unresolved = snapshot("옳은 것을 고르시오. (㉠, ㉡, ㉢)",
                QuestionPresentation.TEXT_ONLY, List.of(), List.of());
        var defined = snapshot("㉠ $2+3=5$, ㉡ $2\\times3=6$ 중 옳은 것을 고르시오.",
                QuestionPresentation.TEXT_ONLY, List.of(), List.of());

        assertThat(validator.violations(unresolved))
                .contains("visualDependency: 의미가 정의되지 않은 참조 기호가 있습니다: ㉠, ㉡, ㉢");
        assertThat(validator.violations(defined)).isEmpty();
    }

    @Test
    void 실제_FIGURE와_자산이_있으면_그래프_참조를_허용한다() {
        List<SnapshotContentBlock> blocks = List.of(
                new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "다음 그래프를 보고 기울기를 구하시오.", null, null),
                new SnapshotContentBlock("CB2", SnapshotBlockKind.FIGURE, 1,
                        null, "F1", null));
        var snapshot = snapshot(null, QuestionPresentation.WITH_FIGURE, blocks,
                List.of(new SnapshotAssetReference("F1", "원점을 지나는 직선 $y=2x$")));

        assertThat(validator.violations(snapshot)).isEmpty();
    }

    private QuestionSnapshotV1 snapshot(String text, QuestionPresentation presentation,
                                        List<SnapshotContentBlock> blocks,
                                        List<SnapshotAssetReference> assets) {
        List<SnapshotContentBlock> actualBlocks = blocks.isEmpty()
                ? List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        text, null, null)) : blocks;
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE, presentation,
                        "mid", 20L, null, null, null),
                actualBlocks, assets, List.of(), List.of(), List.of(),
                "해설", null, List.of());
    }
}
