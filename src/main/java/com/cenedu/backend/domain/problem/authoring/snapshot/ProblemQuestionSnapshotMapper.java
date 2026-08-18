package com.cenedu.backend.domain.problem.authoring.snapshot;

import java.util.List;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import org.springframework.stereotype.Component;

/** 문제 Entity의 영속화 필드만 S1 스냅샷 계약으로 변환하는 경계 Mapper다. */
@Component
public class ProblemQuestionSnapshotMapper {

    /** 문제 본체의 분류·설명과 JSON 하위 구조를 Version 저장용 스냅샷으로 만든다. */
    public QuestionSnapshotV1 toSnapshot(ProblemQuestion question) {
        if (question == null) throw new IllegalArgumentException("문제가 필요합니다.");
        String difficulty = switch (question.getDifficulty()) {
            case 1 -> "low";
            case 2 -> "mid";
            case 3 -> "high";
            default -> throw new IllegalArgumentException("지원하지 않는 난이도입니다.");
        };
        SnapshotMetadata metadata = new SnapshotMetadata(question.getQuestionType(),
                question.getPresentation(), difficulty, question.getSubUnitId(),
                question.getTopicCode(), question.getEvaluationArea(),
                question.getDerivedFrom() == null ? null : question.getDerivedFrom().getId());
        return new QuestionSnapshotV1(QuestionSnapshotV1.CURRENT_SCHEMA_VERSION, metadata,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                question.getExplanation(), null, List.of());
    }
}
