package com.cenedu.backend.domain.problem.authoring.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemSearchDocumentFactoryQueryHintTest {

    @Test
    void 현재_문항과_교사_수정_요청을_함께_검색_문서로_만든다() {
        var query = ProblemReferenceQuery.withQueryHint(UUID.randomUUID(),
                GenerationPurpose.PERSONALIZED_APPLICATION, scope(), QuestionType.ESSAY,
                "high", 77L, snapshot(), 40, 4, Set.of(77L),
                "  주관식으로   바꾸고 난이도를 높여줘  ");

        String document = new ProblemSearchDocumentFactory().createQuery(query);

        assertThat(document).contains("[발문] 현재 문항 본문")
                .contains("[수정요청] 주관식으로 바꾸고 난이도를 높여줘")
                .contains("[유형] ESSAY")
                .contains("[난이도] high");
    }

    @Test
    void 수정_검색은_현재_문항의_시각_종류를_유지한다() {
        var visualSnapshot = new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE, QuestionPresentation.WITH_FIGURE,
                        "mid", 10L, null, null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), "해설", null, List.of());

        var query = ProblemReferenceQuery.withQueryHint(UUID.randomUUID(),
                GenerationPurpose.PROBLEM_EDIT_REPLACEMENT, scope(), QuestionType.MULTIPLE_CHOICE,
                "mid", null, visualSnapshot, 40, 4, Set.of(), "다른 문제로 바꿔줘");

        assertThat(query.requiredVisualKind()).isEqualTo(VisualReferenceKind.UNKNOWN_FIGURE);
    }

    private CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                null, 10L, "수와 연산", "정수와 유리수", "유리수의 계산");
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE, QuestionPresentation.TEXT_ONLY,
                        "mid", 10L, null, null, 77L),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "현재 문항 본문", null, null)),
                List.of(), List.of(), List.of(), List.of(), "해설", null, List.of());
    }
}
