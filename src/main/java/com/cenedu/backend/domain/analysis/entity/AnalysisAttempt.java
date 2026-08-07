package com.cenedu.backend.domain.analysis.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 문항 단위 풀이 시도.
 *
 * <p>{@code eventId} 가 멱등 키다. 같은 이벤트가 두 번 들어와도 한 번만 저장한다.
 *
 * <p>보기와 단계 응답은 jsonb 로 두고 문자열로 들고 있는다. 이 값들의 정본 스키마는 problem
 * 도메인 소관이라 여기서 타입을 박으면 남의 도메인 스키마를 여기서 정하는 셈이 된다.
 */
@Entity
@Getter
@Table(name = "analysis_attempt",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_attempt_event", columnNames = "event_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "assessment_id", nullable = false, length = 64)
    private String assessmentId;

    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;

    @Column(name = "problem_number", nullable = false)
    private int problemNumber;

    @Column(name = "problem_id", length = 64)
    private String problemId;

    @Column(name = "problem_title")
    private String problemTitle;

    @Column(name = "concept_id", length = 64)
    private String conceptId;

    /** 풀이 단계. 평가 영역과는 다른 축이며 합치지 않는다. 원본에 없으면 비워 둔다. */
    @Column(name = "step_id", length = 64)
    private String stepId;

    /**
     * 이 응답을 무엇을 보려고 냈는지.
     *
     * <p>지금은 제출 경로가 값을 채우지 않아 전부 진단으로 들어온다. 재출제가 붙으면 문항을 왜
     * 냈는지 아는 쪽이 재출제라서, 클라이언트가 아니라 서버가 이 값을 정하게 된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private AttemptPurpose purpose;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "hint_used", nullable = false)
    private boolean hintUsed;

    /** 제출 자체가 기록되지 않은 문항. 보고서와 집계에서 제외한다. */
    @Column(name = "submission_failed", nullable = false)
    private boolean submissionFailed;

    @Column(name = "source_dataset", length = 64)
    private String sourceDataset;

    /** 평가 영역. 풀이 단계와는 다른 축이며 합치지 않는다. 원본에 없으면 비워 둔다. */
    @Column(name = "evaluation_area", length = 100)
    private String evaluationArea;

    @Column(name = "topic")
    private String topic;

    /** 원본이 주는 참고 정답률. 비율(0~1)이 아니라 백분율(0~100)이다. */
    @Column(name = "reference_success_rate", precision = 5, scale = 2)
    private BigDecimal referenceSuccessRate;

    @Column(name = "difficulty_band", length = 20)
    private String difficultyBand;

    @Column(name = "source_difficulty", length = 40)
    private String sourceDifficulty;

    @Column(name = "difficulty_basis")
    private String difficultyBasis;

    @Column(name = "problem_text", columnDefinition = "text")
    private String problemText;

    @Column(name = "problem_image_url", columnDefinition = "text")
    private String problemImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices_json", nullable = false)
    private String choicesJson;

    @Column(name = "response_type", length = 20)
    private String responseType;

    @Column(name = "student_answer", columnDefinition = "text")
    private String studentAnswer;

    @Column(name = "correct_answer", columnDefinition = "text")
    private String correctAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_responses_json", nullable = false)
    private String stepResponsesJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Builder
    private AnalysisAttempt(String eventId, String assessmentId, String studentId,
                            int problemNumber, String problemId, String problemTitle,
                            String conceptId, String stepId, AttemptPurpose purpose,
                            boolean correct, boolean hintUsed,
                            boolean submissionFailed, String sourceDataset, String evaluationArea,
                            String topic, BigDecimal referenceSuccessRate, String difficultyBand,
                            String sourceDifficulty, String difficultyBasis, String problemText,
                            String problemImageUrl, String choicesJson, String responseType,
                            String studentAnswer, String correctAnswer, String stepResponsesJson,
                            Instant occurredAt) {
        this.eventId = eventId;
        this.assessmentId = assessmentId;
        this.studentId = studentId;
        this.problemNumber = problemNumber;
        this.problemId = problemId;
        this.problemTitle = problemTitle;
        this.conceptId = conceptId;
        this.stepId = stepId;
        // 목적을 밝히지 않은 응답은 진단으로 본다. dto.AttemptResult 와 같은 규칙이다.
        this.purpose = purpose == null ? AttemptPurpose.DIAGNOSTIC : purpose;
        this.correct = correct;
        this.hintUsed = hintUsed;
        this.submissionFailed = submissionFailed;
        this.sourceDataset = sourceDataset;
        this.evaluationArea = evaluationArea;
        this.topic = topic;
        this.referenceSuccessRate = referenceSuccessRate;
        this.difficultyBand = difficultyBand;
        this.sourceDifficulty = sourceDifficulty;
        this.difficultyBasis = difficultyBasis;
        this.problemText = problemText;
        this.problemImageUrl = problemImageUrl;
        this.choicesJson = choicesJson == null ? "[]" : choicesJson;
        this.responseType = responseType;
        this.studentAnswer = studentAnswer;
        this.correctAnswer = correctAnswer;
        this.stepResponsesJson = stepResponsesJson == null ? "[]" : stepResponsesJson;
        this.occurredAt = occurredAt;
    }
}
