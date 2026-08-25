package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.global.common.enums.QuestionType;

/** 자산 조건이 무작위 후보 제한보다 먼저 적용되는지 검증한다. */
class ProblemQuestionSelectorAssetFilterTest {

    @Test
    void 이미지_문항이_전체_후보에_한_건뿐이어도_8건_제한_전에_선택된다() {
        ProblemQuestionRepository questions = mock(ProblemQuestionRepository.class);
        ProblemAssetRepository assets = mock(ProblemAssetRepository.class);
        List<ProblemQuestion> candidates = LongStream.rangeClosed(1, 25)
                .mapToObj(this::question).toList();
        when(questions.findAllBySubUnitIdAndDifficultyAndQuestionTypeAndDeletedAtIsNull(
                20L, (short) 2, QuestionType.MULTIPLE_CHOICE)).thenReturn(candidates);
        when(assets.findQuestionIdsWithAssets(
                LongStream.rangeClosed(1, 25).boxed().toList())).thenReturn(List.of(25L));

        var selected = new ProblemQuestionSelector(questions, assets).selectAvailable(
                20L, (short) 2, QuestionType.MULTIPLE_CHOICE, 8, Set.of(), true);

        assertThat(selected).extracting(ProblemQuestion::getId).containsExactly(25L);
    }

    @Test
    void 이미지가_없는_조건은_자산_문항을_제외한_후_제한한다() {
        ProblemQuestionRepository questions = mock(ProblemQuestionRepository.class);
        ProblemAssetRepository assets = mock(ProblemAssetRepository.class);
        List<ProblemQuestion> candidates = LongStream.rangeClosed(1, 10)
                .mapToObj(this::question).toList();
        when(questions.findAllBySubUnitIdAndDifficultyAndQuestionTypeAndDeletedAtIsNull(
                20L, (short) 2, QuestionType.MULTIPLE_CHOICE)).thenReturn(candidates);
        when(assets.findQuestionIdsWithAssets(
                LongStream.rangeClosed(1, 10).boxed().toList())).thenReturn(List.of(10L));

        var selected = new ProblemQuestionSelector(questions, assets).selectAvailable(
                20L, (short) 2, QuestionType.MULTIPLE_CHOICE, 8, Set.of(), false);

        assertThat(selected).hasSize(8).extracting(ProblemQuestion::getId).doesNotContain(10L);
    }

    private ProblemQuestion question(long id) {
        ProblemQuestion question = mock(ProblemQuestion.class);
        when(question.getId()).thenReturn(id);
        return question;
    }
}
