package com.cenedu.backend.domain.submission.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.global.common.enums.CompareMethod;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {

    /** 배정-학생의 저장된 답안을 전부 읽는다. 상세 조회의 "이어 풀기" 복원과 진행률 재계산에 쓴다. */
    List<SubmissionAnswer> findByAssignmentStudentId(long assignmentStudentId);

    /**
     * 여러 학생의 답안을 한 번에 읽는다. 점수표는 학생 25명 × 문항 20개를 한 화면에 그리므로
     * 학생마다 조회하면 그 자리에서 N+1이 난다.
     */
    List<SubmissionAnswer> findByAssignmentStudentIdIn(Collection<Long> assignmentStudentIds);

    /** 칸 하나의 저장된 답안. upsert 대상을 찾는 데 쓴다(유니크 키 assignment_student_id+answer_unit_id). */
    Optional<SubmissionAnswer> findByAssignmentStudentIdAndAnswerUnitId(long assignmentStudentId, long answerUnitId);

    /**
     * 칸 하나의 채점 방법만 읽는다. 자동채점이 <b>트랜잭션을 열기 전에</b> 어느 경로로 갈지
     * 골라야 해서 행 전체를 싣지 않는다 — 서술형은 LLM 을 부르는 동안 트랜잭션을 쥐면 안 된다.
     */
    @Query("select a.compareMethod from SubmissionAnswer a where a.id = :id")
    Optional<CompareMethod> findCompareMethodById(@Param("id") long id);
}
