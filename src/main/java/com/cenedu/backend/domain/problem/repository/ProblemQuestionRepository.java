package com.cenedu.backend.domain.problem.repository;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemQuestionRepository extends JpaRepository<ProblemQuestion, Long> {
    // 소단원-난이도-문항 유형 에 해당하는 삭제되지 않은 문제를 조회한다.
    List<ProblemQuestion> findAllBySubUnitIdAndDifficultyAndQuestionTypeAndDeletedAtIsNull(
        Long subUnitId,
        short difficulty,
        QuestionType questionType
    );
}
