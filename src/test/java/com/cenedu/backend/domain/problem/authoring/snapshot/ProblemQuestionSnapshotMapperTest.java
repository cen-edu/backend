package com.cenedu.backend.domain.problem.authoring.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.entity.enums.QuestionSourceType;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProblemQuestionSnapshotMapperTest {
    @Test
    void zeroBased_객관식_데이터를_C논리키로_정규화한다() {
        ProblemQuestion question = ProblemQuestion.create(QuestionSourceType.IMPORTED, "30:1", "30",
                null, 1L, null, (short) 2, QuestionType.MULTIPLE_CHOICE,
                QuestionPresentation.TEXT_ONLY,
                "[{\"blockId\":\"Q-1\",\"blockKind\":\"TEXT\",\"displayOrder\":1,\"text\":\"정답을 고르시오.\"}]",
                "정답을 고르시오.", "첫 번째 보기이다.",
                "{\"conceptTitle\":\"수와 연산\",\"summary\":\"수의 성질을 확인한다.\",\"keyPoints\":[\"수의 성질\"]}",
                null, null);
        List<ProblemChoice> choices = List.of(ProblemChoice.create(question, (short) 0, "1"),
                ProblemChoice.create(question, (short) 1, "2"));
        ProblemAnswerUnit answer = ProblemAnswerUnit.create(question, null, "MAIN", 0, null,
                "1", null, CompareMethod.CHOICE, null, null);

        var snapshot = new ProblemQuestionSnapshotMapper(new ObjectMapper()).toSnapshot(
                new ProblemSnapshotSource(question, choices, List.of(), List.of(answer), List.of(), List.of()));

        assertThat(snapshot.contentBlocks().getFirst().blockKey()).isEqualTo("CB1");
        assertThat(snapshot.choices()).extracting(choice -> choice.choiceKey()).containsExactly("C1", "C2");
        assertThat(snapshot.answerUnits().getFirst().answerRaw()).isEqualTo("C1");
        assertThat(new SnapshotStructuralValidator().violations(snapshot)).isEmpty();
    }
}
