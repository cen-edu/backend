package com.cenedu.backend.domain.problem.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemAnswerUnitRepository
    extends JpaRepository<ProblemAnswerUnit, Long> {

    // 여러 문항의 정답 단위를 문항별 표시 순서로 일괄 조회한다.
    @Query("""
        select answerUnit
        from ProblemAnswerUnit answerUnit
        where answerUnit.question.id in :questionIds
        order by answerUnit.question.id,
                 answerUnit.displayOrder,
                 answerUnit.id
        """)
    List<ProblemAnswerUnit> findAllByQuestionIds(
        @Param("questionIds") Collection<Long> questionIds
    );
    /** 답안 칸 ID에 대응하는 문항 ID를 반환한다. */
    @Query("SELECT answerUnit.question.id FROM ProblemAnswerUnit answerUnit WHERE answerUnit.id = :id")
    Optional<Long> findQuestionIdById(@Param("id") Long id);
}
