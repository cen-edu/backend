package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.snapshot.ProblemQuestionSnapshotMapper;
import com.cenedu.backend.domain.problem.authoring.snapshot.ProblemSnapshotSource;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.authoring.visual.VisualSnapshotConsistencyValidator;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.repository.ProblemAnswerUnitRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.domain.problem.repository.ProblemChoiceRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemRubricItemRepository;
import com.cenedu.backend.domain.problem.repository.ProblemStepRepository;
import com.cenedu.backend.global.common.enums.QuestionType;

class ProblemBankVisualIsolationTest {

    @Test
    void 자료가_없는_그래프_참조_문항은_은행_재사용에서_격리한다() {
        ProblemQuestionRepository questions = mock(ProblemQuestionRepository.class);
        ProblemQuestionSnapshotMapper mapper = mock(ProblemQuestionSnapshotMapper.class);
        SnapshotStructuralValidator structural = mock(SnapshotStructuralValidator.class);
        ProblemChoiceRepository choices = mock(ProblemChoiceRepository.class);
        ProblemStepRepository steps = mock(ProblemStepRepository.class);
        ProblemAnswerUnitRepository answers = mock(ProblemAnswerUnitRepository.class);
        ProblemAssetRepository assets = mock(ProblemAssetRepository.class);
        ProblemRubricItemRepository rubrics = mock(ProblemRubricItemRepository.class);
        ProblemQuestion question = mock(ProblemQuestion.class);
        QuestionSnapshotV1 snapshot = missingGraphSnapshot();

        when(question.getId()).thenReturn(4862L);
        when(questions.findAllById(List.of(4862L))).thenReturn(List.of(question));
        when(choices.findAllByQuestionIds(List.of(4862L))).thenReturn(List.of());
        when(steps.findAllByQuestionIds(List.of(4862L))).thenReturn(List.of());
        when(answers.findAllByQuestionIds(List.of(4862L))).thenReturn(List.of());
        when(assets.findAllByQuestionIds(List.of(4862L))).thenReturn(List.of());
        when(rubrics.findAllByQuestionIds(List.of(4862L))).thenReturn(List.of());
        when(mapper.toSnapshot(any(ProblemSnapshotSource.class))).thenReturn(snapshot);
        when(structural.violations(snapshot)).thenReturn(List.of());

        var service = new ProblemBankSnapshotQueryService(questions, mapper, structural,
                choices, steps, answers, assets, rubrics,
                new VisualSnapshotConsistencyValidator());

        assertThat(service.getSnapshots(List.of(4862L))).singleElement().satisfies(result -> {
            assertThat(result.reusable()).isFalse();
            assertThat(result.violations()).contains(
                    "visualDependency: 실제 그림·그래프·표 없이 시각 자료를 참조할 수 없습니다.");
        });
    }

    private QuestionSnapshotV1 missingGraphSnapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE,
                        QuestionPresentation.TEXT_ONLY, "low", 20L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "다음 그래프에서 욕조의 물의 양이 변하는 모습을 고르시오. (㉠, ㉡, ㉢)",
                        null, null)),
                List.of(), List.of(), List.of(), List.of(), "해설", null, List.of());
    }
}
