package com.cenedu.backend.domain.analysis.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cenedu.backend.domain.analysis.entity.AnalysisReport;
import com.cenedu.backend.domain.analysis.entity.ReportStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    Optional<AnalysisReport> findByReportId(UUID reportId);

    /** 같은 종류의 보고서를 여러 번 생성할 수 있다. 가장 최근 것만 쓴다. */
    Optional<AnalysisReport> findTopByStudentIdAndAssessmentIdAndReportTypeOrderByCreatedAtDesc(
            String studentId, String assessmentId, String reportType);

    List<AnalysisReport> findByStudentIdAndAssessmentIdOrderByReportType(String studentId,
                                                                         String assessmentId);

    List<AnalysisReport> findAllByOrderByCreatedAtDesc();

    boolean existsByStudentIdAndAssessmentIdAndReportTypeAndReportStatus(
            String studentId, String assessmentId, String reportType, ReportStatus reportStatus);
}
