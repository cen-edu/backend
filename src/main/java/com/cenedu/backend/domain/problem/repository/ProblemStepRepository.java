package com.cenedu.backend.domain.problem.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemStepRepository
    extends JpaRepository<ProblemStep, Long> {

    // 여러 빈칸형 문항의 풀이 단계를 문항별 표시 순서로 일괄 조회한다.
    @Query("""
        select step
        from ProblemStep step
        where step.question.id in :questionIds
        order by step.question.id, step.displayOrder
        """)
    List<ProblemStep> findAllByQuestionIds(
        @Param("questionIds") Collection<Long> questionIds
    );
}
