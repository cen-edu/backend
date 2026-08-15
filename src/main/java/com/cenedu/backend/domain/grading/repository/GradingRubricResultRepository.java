package com.cenedu.backend.domain.grading.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.grading.entity.GradingRubricResult;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 서술형 채점 결과 조회. {@code ProblemRubricItem}(problem 도메인) 배치 조회도 여기 둔다 —
 * 채점 기준(항목)과 판정 결과가 같은 화면(학생 결과 조회)에서 함께 필요해서다.
 * domain/problem 쪽 파일은 만들거나 고치지 않는다 — 읽기 전용 쿼리만 내 패키지 안에 둔다.
 */
public interface GradingRubricResultRepository extends JpaRepository<GradingRubricResult, Long> {

    /** 답안 여러 개의 루브릭 판정 결과를 한 번에 읽는다. */
    List<GradingRubricResult> findByStudentAnswerIdIn(Collection<Long> studentAnswerIds);

    /** 문항 여러 개의 서술형 채점 기준을 한 번에 읽는다. */
    @Query("select r from ProblemRubricItem r where r.question.id in :questionIds")
    List<ProblemRubricItem> findRubricItemsByQuestionIdIn(@Param("questionIds") Collection<Long> questionIds);
}
