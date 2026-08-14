package com.cenedu.backend.domain.submission.repository;

import java.util.Optional;

import com.cenedu.backend.domain.submission.entity.SubmissionQuestionTime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionQuestionTimeRepository extends JpaRepository<SubmissionQuestionTime, Long> {

    /** 문항 하나의 누적 풀이 시간. upsert 대상을 찾는 데 쓴다(유니크 키 assignment_student_id+worksheet_item_id). */
    Optional<SubmissionQuestionTime> findByAssignmentStudentIdAndWorksheetItemId(
            long assignmentStudentId, long worksheetItemId);
}
