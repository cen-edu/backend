package com.cenedu.backend.domain.analysis.entity;

import com.cenedu.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문항 하나에 대한 AI 분석 문장.
 *
 * <p>채점이 완료된 문항에만 만든다. 학생이 손대지 않은 문항은 관찰할 풀이 행동이 없어 문장을
 * 만들 근거가 없다. 미응답 사실은 보고서의 {@code overallObservation} 이 종합해서 서술한다.
 */
@Entity
@Getter
@Table(name = "analysis_report_item_message",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_report_item_message",
                columnNames = {"analysis_report_id", "worksheet_item_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisReportItemMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_report_id", nullable = false, updatable = false)
    private Long analysisReportId;

    @Column(name = "worksheet_item_id", nullable = false, updatable = false)
    private Long worksheetItemId;

    @Column(name = "observation", nullable = false, columnDefinition = "TEXT")
    private String observation;

    @Column(name = "learning_point", nullable = false, columnDefinition = "TEXT")
    private String learningPoint;

    @Column(name = "retry_guide", nullable = false, columnDefinition = "TEXT")
    private String retryGuide;

    private AnalysisReportItemMessage(
            Long analysisReportId,
            Long worksheetItemId,
            String observation,
            String learningPoint,
            String retryGuide
    ) {
        this.analysisReportId = analysisReportId;
        this.worksheetItemId = worksheetItemId;
        this.observation = observation;
        this.learningPoint = learningPoint;
        this.retryGuide = retryGuide;
    }

    /** 검증을 마친 문항별 문장을 새로 만든다. 재생성 시에는 기존 행을 지우고 다시 만든다. */
    public static AnalysisReportItemMessage create(
            Long analysisReportId,
            Long worksheetItemId,
            String observation,
            String learningPoint,
            String retryGuide
    ) {
        return new AnalysisReportItemMessage(
                analysisReportId, worksheetItemId, observation, learningPoint, retryGuide);
    }
}
