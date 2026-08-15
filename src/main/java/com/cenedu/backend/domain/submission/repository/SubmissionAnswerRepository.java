package com.cenedu.backend.domain.submission.repository;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {

    /** 배정-학생의 저장된 답안을 전부 읽는다. 상세 조회의 "이어 풀기" 복원과 진행률 재계산에 쓴다. */
    List<SubmissionAnswer> findByAssignmentStudentId(long assignmentStudentId);

    /** 칸 하나의 저장된 답안. upsert 대상을 찾는 데 쓴다(유니크 키 assignment_student_id+answer_unit_id). */
    Optional<SubmissionAnswer> findByAssignmentStudentIdAndAnswerUnitId(long assignmentStudentId, long answerUnitId);
}
