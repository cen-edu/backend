package com.cenedu.backend.domain.analysis.repository;

import java.util.List;

import com.cenedu.backend.domain.analysis.entity.AnalysisReportItemMessage;
import org.springframework.data.jpa.repository.JpaRepository;

/** 문항별 AI 분석 문장을 저장하는 analysis 소유 Repository. */
public interface AnalysisReportItemMessageRepository
        extends JpaRepository<AnalysisReportItemMessage, Long> {

    /** 보고서의 문항별 문장을 문항 ID 순으로 조회한다. */
    List<AnalysisReportItemMessage> findByAnalysisReportIdOrderByWorksheetItemIdAsc(
            long analysisReportId);

    /** 보고서의 문항별 문장을 모두 지운다. 재생성은 지우고 다시 넣는다. */
    void deleteByAnalysisReportId(long analysisReportId);
}
