package com.cenedu.backend.domain.problem.support;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

/** S2 상태 테스트가 같은 정상 S1 문항을 재사용하게 한다. */
public final class ProblemSnapshotFixtures {

    private ProblemSnapshotFixtures() {
    }

    public static QuestionSnapshotV1 shortInput() {
        return new QuestionSnapshotV1(
                QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                new SnapshotMetadata(
                        QuestionType.SHORT_INPUT,
                        QuestionPresentation.TEXT_ONLY,
                        "mid", 1L, null, null, null),
                List.of(new SnapshotContentBlock(
                        "CB1", SnapshotBlockKind.TEXT, 0,
                        "12를 구하시오.", null, null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, "12", "12",
                        CompareMethod.VALUE, null, null)),
                "식을 계산하면 12이다.",
                new SnapshotLearningGuide(
                        "사칙연산", "사칙연산을 사용해 문제를 해결한다.",
                        List.of("연산 순서를 확인한다.")),
                List.of());
    }
}
