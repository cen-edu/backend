package com.cenedu.backend.domain.problem.authoring.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.entity.enums.QuestionSourceType;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProblemBankSnapshotCompatibilityTest {

    private final ProblemQuestionSnapshotMapper mapper =
            new ProblemQuestionSnapshotMapper(new ObjectMapper());
    private final SnapshotStructuralValidator validator = new SnapshotStructuralValidator();

    @Test
    void 은행_그림_표현을_정본_스냅샷으로_정규화한다() {
        ProblemQuestion question = figureQuestion();
        ProblemAsset asset = ProblemAsset.create(question, "F1", AssetRole.FIGURE,
                (short) 0, "problem/figure.png", 640, 480, " ");

        var snapshot = mapper.toSnapshot(source(question, List.of(asset)));

        assertThat(snapshot.contentBlocks().get(1).text()).isNull();
        assertThat(snapshot.assets().getFirst().altText()).isEqualTo("문제 그림 F1");
        assertThat(validator.violations(snapshot)).isEmpty();
    }

    @Test
    void 없는_자산을_참조하는_은행_문항은_계속_차단한다() {
        ProblemQuestion question = figureQuestion();

        var snapshot = mapper.toSnapshot(source(question, List.of()));

        assertThat(validator.violations(snapshot))
                .contains("contentBlocks[1].assetRef: assets에 없는 키를 참조합니다: F1");
    }

    private ProblemSnapshotSource source(ProblemQuestion question, List<ProblemAsset> assets) {
        List<ProblemChoice> choices = List.of(
                ProblemChoice.create(question, (short) 0, "3"),
                ProblemChoice.create(question, (short) 1, "4"));
        ProblemAnswerUnit answer = ProblemAnswerUnit.create(question, null, "MAIN", 0,
                null, "1", null, CompareMethod.CHOICE, null, null);
        return new ProblemSnapshotSource(question, choices, List.of(), List.of(answer), assets,
                List.of());
    }

    private ProblemQuestion figureQuestion() {
        return ProblemQuestion.create(QuestionSourceType.IMPORTED, "bank:figure-1", "BANK",
                null, 1L, null, (short) 2, QuestionType.MULTIPLE_CHOICE,
                QuestionPresentation.WITH_FIGURE,
                "[{\"blockId\":\"T1\",\"blockKind\":\"TEXT\",\"displayOrder\":0,"
                        + "\"text\":\"그림을 보고 알맞은 답을 고르시오.\"},"
                        + "{\"blockId\":\"Q-IMG-1\",\"blockKind\":\"FIGURE\","
                        + "\"displayOrder\":1,\"text\":\"\",\"assetRef\":\"F1\"}]",
                "그림을 보고 알맞은 답을 고르시오.", "그림의 수를 세면 3이다.",
                "{\"conceptTitle\":\"수와 연산\",\"summary\":\"그림의 수를 센다.\","
                        + "\"keyPoints\":[\"대상을 하나씩 센다.\"]}",
                null, null);
    }
}
