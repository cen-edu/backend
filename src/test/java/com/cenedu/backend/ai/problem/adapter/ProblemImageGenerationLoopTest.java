package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotValidationException;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.authoring.visual.VisualSnapshotConsistencyValidator;
import com.cenedu.backend.global.common.enums.QuestionType;

class ProblemImageGenerationLoopTest {

    @Test
    void 이미지_전략의_첫실패만_교정해_최대두번_시도한다() {
        ProblemCandidateDraft input = mock(ProblemCandidateDraft.class);
        ProblemCandidateDraft output = mock(ProblemCandidateDraft.class);
        VisualSnapshotConsistencyValidator consistency = mock(VisualSnapshotConsistencyValidator.class);
        AtomicInteger calls = new AtomicInteger();
        ProblemImageGenerator generator = new ProblemImageGenerator() {
            @Override
            public VisualReferenceKind kind() {
                return VisualReferenceKind.COORDINATE_GRAPH;
            }

            @Override
            public ProblemCandidateDraft generate(ProblemCandidateDraft candidate, String description,
                    ProblemGenerationCommand command, int attempt, RuntimeException previousFailure) {
                if (calls.getAndIncrement() == 0) throw new IllegalStateException("좌표 범위 오류");
                assertThat(previousFailure).hasMessage("좌표 범위 오류");
                return output;
            }
        };
        var loop = new ProblemImageGenerationLoop(List.of(generator), consistency);

        assertThat(loop.generate(input, visualOutput(true, "COORDINATE_GRAPH", "직선 y=2x"), command()))
                .isSameAs(output);
        assertThat(calls).hasValue(2);
    }

    @Test
    void 이미지가_필요하지_않으면_종류와_설명을_허용하지_않는다() {
        var loop = new ProblemImageGenerationLoop(List.of(),
                mock(VisualSnapshotConsistencyValidator.class));

        assertThatThrownBy(() -> loop.generate(mock(ProblemCandidateDraft.class),
                visualOutput(false, "COORDINATE_GRAPH", "직선"), command()))
                .isInstanceOf(SnapshotValidationException.class)
                .hasMessageContaining("visualRequired=false");
    }

    private ProblemGenerationOutput visualOutput(boolean required, String kind, String description) {
        return new ProblemGenerationOutput("문제", List.of(), List.of(), List.of(), List.of(),
                "해설", new ProblemGenerationOutput.LearningGuideOutput(
                        "개념", "요약", List.of("핵심")),
                List.of(), List.of(), required, kind, description);
    }

    private ProblemGenerationCommand command() {
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE,
                new GenerationSpecification(QuestionType.MULTIPLE_CHOICE, "mid", null,
                        List.of(), false, VisualGenerationRequirement.none()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                        null, 20L, "변화와 관계", "좌표와 그래프", "좌표평면과 그래프"),
                List.of(), List.of());
    }
}
