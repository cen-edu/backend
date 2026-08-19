package com.cenedu.backend.domain.analysis.entity;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생 학습지 수행 한 회차의 AI 분석 문장.
 *
 * <p>점수·정답률 같은 분석 원본 수치는 담지 않는다. 그 값들은 worksheet / submission 테이블에서
 * 조회한다. 여기에는 AI가 생성한 문장과 생성 이력만 남는다.
 *
 * <p>과거 버전을 보관하지 않으므로 재생성은 같은 행을 갱신한다.
 */
@Entity
@Getter
@Table(name = "analysis_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_report_assignment_student",
                columnNames = "assignment_student_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_student_id", nullable = false, updatable = false)
    private Long assignmentStudentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false, length = 20)
    private GenerationStatus generationStatus;

    @Column(name = "summary_message", columnDefinition = "TEXT")
    private String summaryMessage;

    @Column(name = "overall_observation", columnDefinition = "TEXT")
    private String overallObservation;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "model_name", length = 50)
    private String modelName;

    @Column(name = "llm_schema_version")
    private Short llmSchemaVersion;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    /**
     * 생성이 끝나 문장을 채운다. 이전 실패 기록은 지운다.
     *
     * <p>{@code READY} 인데 문장이 비어 있으면 DB CHECK 제약이 막지만, 공백 문자열은 막지 못하므로
     * 호출 전에 검증을 마쳐야 한다.
     */
    public void markReady(
            String summaryMessage,
            String overallObservation,
            String promptVersion,
            String modelName,
            Short llmSchemaVersion,
            OffsetDateTime generatedAt
    ) {
        this.generationStatus = GenerationStatus.READY;
        this.summaryMessage = summaryMessage;
        this.overallObservation = overallObservation;
        this.promptVersion = promptVersion;
        this.modelName = modelName;
        this.llmSchemaVersion = llmSchemaVersion;
        this.generatedAt = generatedAt;
        this.lastErrorCode = null;
    }

    /**
     * 생성에 실패했음을 기록한다. 직전에 성공한 문장은 지우지 않는다.
     *
     * <p>재생성이 실패했다고 이미 교사가 보던 문장까지 사라지면 화면이 비어버린다. 상태만 실패로
     * 바꾸고 문장은 남겨 두어, 화면이 이전 분석을 계속 보여줄지 선택할 수 있게 한다.
     */
    public void markFailed(String lastErrorCode) {
        this.generationStatus = GenerationStatus.FAILED;
        this.lastErrorCode = lastErrorCode;
    }

    /** 저장된 문장이 기준 시각 이후의 채점 결과를 반영하지 못했는지 판단한다. */
    public boolean isStaleAgainst(OffsetDateTime lastGradingChangedAt) {
        if (generatedAt == null) {
            return true;
        }
        return lastGradingChangedAt != null && lastGradingChangedAt.isAfter(generatedAt);
    }
}
