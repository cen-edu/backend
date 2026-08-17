package com.cenedu.backend.domain.grading.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.grading.entity.GradingRubricResult;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 서술형 채점 결과 조회와, 채점 화면이 읽어야 하는 problem 도메인 데이터의 배치 조회.
 *
 * <p>domain/problem 쪽 파일은 만들거나 고치지 않는다 — 읽기 전용 쿼리만 내 패키지 안에 둔다.
 * 채점 화면이 문항·보기·채점 칸·채점 기준을 한 화면에 같이 그리므로 조회를 한자리에 모은다.
 */
public interface GradingRubricResultRepository extends JpaRepository<GradingRubricResult, Long> {

    /** 답안 여러 개의 루브릭 판정 결과를 한 번에 읽는다. */
    List<GradingRubricResult> findByStudentAnswerIdIn(Collection<Long> studentAnswerIds);

    /** 문항 여러 개의 서술형 채점 기준을 한 번에 읽는다. */
    @Query("select r from ProblemRubricItem r where r.question.id in :questionIds")
    List<ProblemRubricItem> findRubricItemsByQuestionIdIn(@Param("questionIds") Collection<Long> questionIds);

    /** 문항 여러 개를 한 번에 읽는다. 채점 화면의 발문·해설·유형·난이도가 여기서 나온다. */
    @Query("select q from ProblemQuestion q where q.id in :questionIds")
    List<ProblemQuestion> findQuestionsByIdIn(@Param("questionIds") Collection<Long> questionIds);

    /** 문항 여러 개의 보기를 한 번에 읽는다. 객관식 정답·학생 답을 텍스트로 푸는 데 쓴다. */
    @Query("select c from ProblemChoice c where c.question.id in :questionIds "
            + "order by c.question.id, c.displayOrder")
    List<ProblemChoice> findChoicesByQuestionIdIn(@Param("questionIds") Collection<Long> questionIds);

    /** 문항 여러 개의 채점 칸을 표시 순서대로 한 번에 읽는다. */
    @Query("select u from ProblemAnswerUnit u where u.question.id in :questionIds "
            + "order by u.question.id, u.displayOrder, u.id")
    List<ProblemAnswerUnit> findAnswerUnitsByQuestionIdIn(@Param("questionIds") Collection<Long> questionIds);
}
