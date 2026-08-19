package com.cenedu.backend.domain.grading.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.grading.entity.GradingRubricResult;
import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import com.cenedu.backend.domain.problem.entity.ProblemStep;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 서술형 채점 결과 조회. {@code ProblemRubricItem}(problem 도메인) 배치 조회도 여기 둔다 —
 * 채점 기준(항목)과 판정 결과가 같은 화면(학생 결과 조회)에서 함께 필요해서다.
 * domain/problem 쪽 파일은 만들거나 고치지 않는다 — 읽기 전용 쿼리만 내 패키지 안에 둔다.
 *
 * <p><b>TODO(배세빈, 도메인 경계 정리):</b> 다른 도메인의 Entity를 이 Repository에서 직접
 * 조회하는 현재 방식은 AGENTS.md 3절 1·2번과 맞지 않는다. Problem 도메인의 공개 루브릭
 * Response/Service를 통해 조회하도록 교체하고, 이 Repository는 Grading Entity만 반환한다.
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

    /** 문항 여러 개의 빈칸형 풀이 단계를 표시 순서대로 한 번에 읽는다. */
    @Query("select s from ProblemStep s where s.question.id in :questionIds "
            + "order by s.question.id, s.displayOrder")
    List<ProblemStep> findStepsByQuestionIdIn(@Param("questionIds") Collection<Long> questionIds);
}
