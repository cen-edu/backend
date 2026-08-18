package com.cenedu.backend.domain.problem.repository;

import java.util.Collection;
import java.util.List;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 여러 서술형 문항의 루브릭을 문항·표시 순서대로 조회한다. */
public interface ProblemRubricItemRepository extends JpaRepository<ProblemRubricItem, Long> {
    @Query("""
        select rubric from ProblemRubricItem rubric
        where rubric.question.id in :questionIds
        order by rubric.question.id, rubric.displayOrder, rubric.id
        """)
    List<ProblemRubricItem> findAllByQuestionIds(@Param("questionIds") Collection<Long> questionIds);
}
