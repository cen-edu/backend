package com.cenedu.backend.domain.problem.authoring.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;

import org.springframework.stereotype.Component;

/** 서버 정답 정규화 이후 비교 가능한 값이 모두 채워졌는지 검사한다. */
@Component
public class SnapshotNormalizedValidator {

    private final SnapshotStructuralValidator structuralValidator;

    public SnapshotNormalizedValidator(SnapshotStructuralValidator structuralValidator) {
        this.structuralValidator = structuralValidator;
    }

    /** 구조 및 정규화 위반이 하나라도 있으면 모든 위반을 담은 예외를 던진다. */
    public void validate(QuestionSnapshotV1 snapshot) {
        List<String> violations = violations(snapshot);
        if (!violations.isEmpty()) {
            throw new SnapshotValidationException(violations);
        }
    }

    /** 검증 에이전트 호출 전 보완해야 할 위반 목록을 반환한다. */
    public List<String> violations(QuestionSnapshotV1 snapshot) {
        LinkedHashSet<String> violations = new LinkedHashSet<>(
                structuralValidator.violations(snapshot));
        if (snapshot == null || snapshot.answerUnits() == null) {
            return List.copyOf(violations);
        }

        for (int index = 0; index < snapshot.answerUnits().size(); index++) {
            SnapshotAnswerUnit unit = snapshot.answerUnits().get(index);
            if (unit == null || unit.compareMethod() == null) {
                continue;
            }
            switch (unit.compareMethod()) {
                case VALUE, EXACT, SET -> {
                    if (unit.answerNormalized() == null
                            || unit.answerNormalized().isBlank()) {
                        violations.add("answerUnits[" + index
                                + "].answerNormalized: 정규화 후 필수입니다.");
                    }
                }
                case CHOICE, SUBST, RUBRIC -> {
                    // CHOICE/RUBRIC은 정규화 대상이 아니고 SUBST는 팀 협의 전까지 선택값이다.
                }
            }
        }
        return new ArrayList<>(violations);
    }
}
