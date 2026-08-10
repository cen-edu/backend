package com.cenedu.backend.domain.analysis.repository;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.entity.AnalysisAssessment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisAssessmentRepository extends JpaRepository<AnalysisAssessment, Long> {

    Optional<AnalysisAssessment> findByAssessmentIdAndStudentId(String assessmentId,
                                                                String studentId);

    List<AnalysisAssessment> findByAssessmentIdOrderByStudentName(String assessmentId);

    /** 대시보드 회차 목록. 최근 회차가 먼저 온다. */
    List<AnalysisAssessment> findAllByOrderByAssessmentDateDescAssessmentIdAsc();
}
