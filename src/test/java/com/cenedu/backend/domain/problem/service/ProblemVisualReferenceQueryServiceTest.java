package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProblemVisualReferenceQueryServiceTest {
    @Test
    void text_only는_시각_정본을_생성하지_않는다() {
        var question = mock(ProblemQuestion.class);
        when(question.getPresentation()).thenReturn(QuestionPresentation.TEXT_ONLY);
        var questions = mock(ProblemQuestionRepository.class);
        var assets = mock(ProblemAssetRepository.class);
        when(questions.findById(1L)).thenReturn(Optional.of(question));
        when(assets.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of());

        var descriptor = new ProblemVisualReferenceQueryService(questions, assets, new ObjectMapper()).get(1L);

        assertThat(descriptor.kind()).isEqualTo(VisualReferenceKind.NONE);
    }

    @Test
    void 여러_자산은_자동생성_근거로_UNKNOWN으로_판정한다() {
        var question = mock(ProblemQuestion.class);
        when(question.getPresentation()).thenReturn(QuestionPresentation.WITH_FIGURE);
        var first = mock(ProblemAsset.class);
        var second = mock(ProblemAsset.class);
        when(first.getAssetKey()).thenReturn("F1");
        when(second.getAssetKey()).thenReturn("F2");
        var questions = mock(ProblemQuestionRepository.class);
        var assets = mock(ProblemAssetRepository.class);
        when(questions.findById(1L)).thenReturn(Optional.of(question));
        when(assets.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of(first, second));

        var descriptor = new ProblemVisualReferenceQueryService(questions, assets, new ObjectMapper()).get(1L);

        assertThat(descriptor.kind()).isEqualTo(VisualReferenceKind.UNKNOWN_FIGURE);
    }
}
