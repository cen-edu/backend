package com.cenedu.backend.domain.problem.authoring.snapshot;

import java.util.List;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import com.cenedu.backend.domain.problem.entity.ProblemStep;

/** 문제은행 Entity와 하위 데이터를 Mapper에 전달하는 묶음이다. */
public record ProblemSnapshotSource(ProblemQuestion question,
                                    List<ProblemChoice> choices,
                                    List<ProblemStep> steps,
                                    List<ProblemAnswerUnit> answerUnits,
                                    List<ProblemAsset> assets,
                                    List<ProblemRubricItem> rubricItems) {
    public ProblemSnapshotSource {
        if (question == null) throw new IllegalArgumentException("문제가 필요합니다.");
        choices = choices == null ? List.of() : List.copyOf(choices);
        steps = steps == null ? List.of() : List.copyOf(steps);
        answerUnits = answerUnits == null ? List.of() : List.copyOf(answerUnits);
        assets = assets == null ? List.of() : List.copyOf(assets);
        rubricItems = rubricItems == null ? List.of() : List.copyOf(rubricItems);
    }
}
