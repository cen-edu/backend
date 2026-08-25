package com.cenedu.backend.domain.problem.authoring.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;

class SnapshotStructuralValidatorTextIntegrityTest {

    private final SnapshotStructuralValidator validator = new SnapshotStructuralValidator();

    @Test
    void JSON_이스케이프가_제어문자로_손상된_LaTeX를_차단한다() {
        String brokenFrac = "$" + '\f' + "rac{3}{4}$";
        String brokenTimes = "$2" + '\t' + "imes3$";

        var violations = validator.violations(snapshot(brokenFrac, brokenTimes));

        assertThat(violations)
                .anyMatch(value -> value.contains("contentBlocks[0].text")
                        && value.contains("U+000C"))
                .anyMatch(value -> value.contains("explanation")
                        && value.contains("U+0009"));
    }

    @Test
    void 올바르게_파싱된_LaTeX와_일반_줄바꿈은_허용한다() {
        var violations = validator.violations(snapshot(
                "$\\frac{3}{4}$의 값을 구하시오.",
                "첫 줄에서 $2\\times3=6$을 계산한다.\n따라서 정답은 $6$이다."));

        assertThat(violations).isEmpty();
    }

    private QuestionSnapshotV1 snapshot(String prompt, String explanation) {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY,
                        "low", 19L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        prompt, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SnapshotAnswerUnit("MAIN", null, 0,
                        "3/4", "3/4", CompareMethod.VALUE, null, null)),
                explanation,
                new SnapshotLearningGuide("분수의 계산", "분수의 값을 계산한다.",
                        List.of("분자와 분모를 확인한다.")),
                List.of());
    }
}
