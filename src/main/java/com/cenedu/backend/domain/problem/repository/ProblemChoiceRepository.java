package com.cenedu.backend.domain.problem.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemChoiceRepository
    extends JpaRepository<ProblemChoice, Long> {

    // 여러 문항의 객관식 보기를 문항별 표시 순서로 일괄 조회한다.
    @Query("""
        select choice
        from ProblemChoice choice
        where choice.question.id in :questionIds
        order by choice.question.id, choice.displayOrder
        """)
    List<ProblemChoice> findAllByQuestionIds(
        @Param("questionIds") Collection<Long> questionIds
    );
}
