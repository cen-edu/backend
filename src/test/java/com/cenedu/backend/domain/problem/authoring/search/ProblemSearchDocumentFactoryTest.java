package com.cenedu.backend.domain.problem.authoring.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.curriculum.entity.enums.UnitLevel;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemSearchDocumentFactoryTest {
    private final ProblemSearchDocumentFactory factory = new ProblemSearchDocumentFactory();

    @Test
    void normalizesNoiseAndIgnoresAnswers() {
        QuestionSnapshotV1 first = snapshot("합을 3 구하시오.\r\n", "ANSWER_SENTINEL");
        QuestionSnapshotV1 second = snapshot(" 합을 3 구하시오. ", "999999");
        var left = factory.create(command(first));
        var right = factory.create(command(second));
        assertThat(left.documentHash()).isEqualTo(right.documentHash());
        assertThat(left.documentText()).doesNotContain("ANSWER_SENTINEL", "999999");
    }

    @Test
    void visiblePromptChangesDocumentHashButNumericReplacementKeepsDuplicateCluster() {
        var first = factory.create(command(snapshot("사과 3개의 합", "1")));
        var second = factory.create(command(snapshot("사과 9개의 합", "2")));
        var changed = factory.create(command(snapshot("사과 3개의 차", "1")));
        assertThat(first.duplicateClusterKey()).isEqualTo(second.duplicateClusterKey());
        assertThat(first.documentHash()).isNotEqualTo(changed.documentHash());
    }

    @Test
    void createsStableSourceFamilies() {
        assertThat(ProblemSearchDocumentFactory.sourceFamily("110:11319_11635", 1L))
                .isEqualTo("110:11319");
        assertThat(ProblemSearchDocumentFactory.sourceFamily("110:11319_27047", 1L))
                .isEqualTo("110:11319");
        assertThat(ProblemSearchDocumentFactory.sourceFamily(null, 42L)).isEqualTo("authored:42");
    }

    @Test
    void queryUsesSameAnswerFreeLabelsWithoutOriginSnapshot() {
        var query = new ProblemReferenceQuery(UUID.randomUUID(), GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                scope(), QuestionType.SHORT_INPUT, "mid", null, null, 40, 3, Set.of());
        String text = factory.createQuery(query);
        assertThat(text).contains("[교육과정]", "[성취기준]", "[발문]", "[풀이전략]", "[풀이요약]", "[표현]");
        assertThat(text).contains("동일 교육과정 범위의 새 문제");
    }

    private static SearchIndexingCommand command(QuestionSnapshotV1 snapshot) {
        return new SearchIndexingCommand(UUID.randomUUID(), 10L, null, scope(),
                "110:11319_11635", snapshot, Set.of());
    }

    private static CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 30L,
                "수와 연산", "정수와 유리수", "정수");
    }

    private static QuestionSnapshotV1 snapshot(String prompt, String answer) {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY,
                        "mid", 30L, null, null, null),
                List.of(new SnapshotContentBlock("P", SnapshotBlockKind.TEXT, 1, prompt, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SnapshotAnswerUnit("MAIN", null, 1, answer, answer, null, null, null)),
                "정답은 설명에 넣지 않는다", null, List.of());
    }
}
