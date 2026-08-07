package com.cenedu.backend.domain.analysis.repository;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisAttemptRepository extends JpaRepository<AnalysisAttempt, Long> {

    Optional<AnalysisAttempt> findByEventId(String eventId);

    /**
     * 한 회차의 풀이를 문항 순서대로 읽는다.
     *
     * <p>정렬에 eventId 까지 넣는 이유: 같은 문항을 같은 시각에 두 번 제출하면 순서가 흔들려
     * 보고서가 실행마다 달라진다. 마지막 키로 순서를 고정한다.
     */
    List<AnalysisAttempt> findByAssessmentIdAndStudentIdOrderByProblemNumberAscOccurredAtAscEventIdAsc(
            String assessmentId, String studentId);

    long countByAssessmentIdAndStudentId(String assessmentId, String studentId);

    long countByAssessmentIdAndStudentIdAndCorrectTrue(String assessmentId, String studentId);
}
