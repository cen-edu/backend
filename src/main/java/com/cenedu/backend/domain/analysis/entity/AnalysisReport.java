package com.cenedu.backend.domain.analysis.entity;

import java.time.Instant;
import java.util.UUID;

import com.cenedu.backend.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 생성된 학생 보고서.
 *
 * <p>AI 초안 → 교사 수정 → 교사 확정 순으로 상태가 바뀐다. edu-sen 은 version 을 SQL 에서 직접
 * 올리며 잠금을 걸었는데, 여기서는 {@code @Version} 으로 JPA 에 맡긴다. 교사 두 명이 같은 보고서를
 * 동시에 저장하면 나중 쪽이 실패해야 한다.
 *
 * <p>생성된 초안({@code generatedSectionsJson})과 교사가 고친 본문({@code editedSectionsJson})을
 * 따로 든다. 초안을 남겨 두지 않으면 되돌리기가 불가능하다.
 */
@Entity
@Getter
@Table(name = "analysis_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_report_report_id", columnNames = "report_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부에 노출하는 식별자. 대리 키 id 를 URL 에 싣지 않는다. */
    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "assessment_id", nullable = false, length = 64)
    private String assessmentId;

    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;

    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 30)
    private ReportStatus reportStatus;

    /** 보고서 첫머리에 뽑는 상태 이름. facts 안의 값을 목록 조회용으로 꺼내 둔 것이다. */
    @Column(name = "status_name", length = 100)
    private String statusName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts_json", nullable = false)
    private String factsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "narrative_json", nullable = false)
    private String narrativeJson;

    /** LLM 호출 요청·응답 기록. 제공자를 바꿔도 컬럼 이름이 흔들리지 않게 중립 이름을 쓴다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_call_json", nullable = false)
    private String llmCallJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generated_sections_json", nullable = false)
    private String generatedSectionsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edited_sections_json", nullable = false)
    private String editedSectionsJson;

    @Column(name = "html_path", columnDefinition = "text")
    private String htmlPath;

    @Column(name = "pdf_path", columnDefinition = "text")
    private String pdfPath;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Builder
    private AnalysisReport(UUID reportId, String assessmentId, String studentId,
                           String reportType, String statusName, String factsJson,
                           String narrativeJson, String llmCallJson, String sectionsJson,
                           String htmlPath, String pdfPath) {
        this.reportId = reportId;
        this.assessmentId = assessmentId;
        this.studentId = studentId;
        this.reportType = reportType;
        this.statusName = statusName;
        this.factsJson = factsJson == null ? "{}" : factsJson;
        this.narrativeJson = narrativeJson == null ? "{}" : narrativeJson;
        this.llmCallJson = llmCallJson == null ? "{}" : llmCallJson;
        this.generatedSectionsJson = sectionsJson == null ? "[]" : sectionsJson;
        this.editedSectionsJson = this.generatedSectionsJson;
        this.htmlPath = htmlPath;
        this.pdfPath = pdfPath;
        this.reportStatus = ReportStatus.AI_DRAFT;
    }

    /** 교사가 본문을 고친다. 확정 상태였더라도 다시 수정 상태로 돌아간다. */
    public void edit(String reportType, String editedSectionsJson) {
        this.reportType = reportType;
        this.editedSectionsJson = editedSectionsJson;
        this.reportStatus = ReportStatus.TEACHER_EDITED;
        this.confirmedBy = null;
        this.confirmedAt = null;
    }

    /** 교사 수정분을 버리고 AI 초안으로 되돌린다. */
    public void reset() {
        this.editedSectionsJson = this.generatedSectionsJson;
        this.reportStatus = ReportStatus.AI_DRAFT;
        this.confirmedBy = null;
        this.confirmedAt = null;
    }

    public void confirm(String teacherId) {
        this.reportStatus = ReportStatus.TEACHER_CONFIRMED;
        this.confirmedBy = teacherId;
        this.confirmedAt = Instant.now();
    }

    public boolean confirmedStudentDetail() {
        return reportStatus == ReportStatus.TEACHER_CONFIRMED;
    }
}
