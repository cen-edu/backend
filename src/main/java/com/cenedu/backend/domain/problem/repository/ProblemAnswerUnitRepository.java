package com.cenedu.backend.domain.problem.repository;

import java.util.Optional;

import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemAnswerUnitRepository extends JpaRepository<ProblemAnswerUnit, Long> {

    /** 답안 칸 ID에 대응하는 문항 ID를 반환한다. */
    @Query("SELECT answerUnit.question.id FROM ProblemAnswerUnit answerUnit WHERE answerUnit.id = :id")
    Optional<Long> findQuestionIdById(@Param("id") Long id);
}
