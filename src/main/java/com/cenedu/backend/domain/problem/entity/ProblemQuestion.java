package com.cenedu.backend.domain.problem.entity;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.entity.enums.QuestionSourceType;
import com.cenedu.backend.domain.problem.entity.enums.VerificationStatus;
import com.cenedu.backend.domain.problem.entity.enums.SemanticModelStatus;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.SemanticModelDocument;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 문항 본체. 적재·생성·즉석 생성 문항이 유형과 무관하게 이 한 테이블에 들어간다. */
@Entity
@Getter
@Table(name = "problem_question",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_question_source_ref", columnNames = "source_ref"),
        indexes = {
                @Index(name = "idx_problem_question_sub_unit_difficulty",
                        columnList = "sub_unit_id, difficulty"),
                @Index(name = "idx_problem_question_topic", columnList = "topic_code"),
                @Index(name = "idx_problem_question_derived_from",
                        columnList = "derived_from_question_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private QuestionSourceType sourceType;

    /** {데이터셋번호}:{원천ID}. 적재 문항에만 값이 있다. */
    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "source_dataset_code", length = 30)
    private String sourceDatasetCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "derived_from_question_id",
            foreignKey = @ForeignKey(name = "fk_problem_question_derived_from"))
    private ProblemQuestion derivedFrom;

    /** curriculum 도메인의 소단원 ID. */
    @Column(name = "sub_unit_id", nullable = false)
    private Long subUnitId;

    /** 같은 도메인이지만 자연키 참조라 코드 값으로 든다. */
    @Column(name = "topic_code", length = 50)
    private String topicCode;

    @Column(name = "difficulty", nullable = false)
    private short difficulty;

    /** AIHub 110·111 문항의 평가 영역. 해당 값이 없는 데이터셋은 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_area", length = 20)
    private EvaluationArea evaluationArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "presentation", nullable = false, length = 20)
    private QuestionPresentation presentation;

    /** 렌더링 정본. 내부 assetRef 가 problem_asset.asset_key 를 가리킨다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_blocks", nullable = false)
    private String contentBlocks;

    /** RAG 검색·임베딩 전용 원문. 화면 표시용이 아니다. */
    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "learning_guide")
    private String learningGuide;

    @Column(name = "hint_text", columnDefinition = "TEXT")
    private String hintText;

    /** null 이면 검증 대상이 아니다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "semantic_model_schema_version")
    private Short semanticModelSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "semantic_model", columnDefinition = "jsonb")
    private String semanticModel;

    @Column(name = "semantic_model_hash", length = 64)
    private String semanticModelHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_model_status", nullable = false, length = 20)
    private SemanticModelStatus semanticModelStatus = SemanticModelStatus.ABSENT;

    @Column(name = "verification_attempts", nullable = false)
    private short verificationAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private ProblemQuestion(QuestionSourceType sourceType, String sourceRef,
                            String sourceDatasetCode, ProblemQuestion derivedFrom, Long subUnitId,
                            String topicCode, short difficulty, QuestionType questionType,
                            QuestionPresentation presentation, String contentBlocks,
                            String promptText, String explanation, String learningGuide,
                            String hintText, VerificationStatus verificationStatus,
                            EvaluationArea evaluationArea) {
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.sourceDatasetCode = sourceDatasetCode;
        this.derivedFrom = derivedFrom;
        this.subUnitId = subUnitId;
        this.topicCode = topicCode;
        this.difficulty = difficulty;
        this.questionType = questionType;
        this.presentation = presentation;
        this.contentBlocks = contentBlocks;
        this.promptText = promptText;
        this.explanation = explanation;
        this.learningGuide = learningGuide;
        this.hintText = hintText;
        this.verificationStatus = verificationStatus;
        this.evaluationArea = evaluationArea;
        this.verificationAttempts = 0;
        this.createdAt = OffsetDateTime.now();
    }

    /** 문항을 생성한다. 출처별로 값이 있는 필드가 다른 것은 서비스가 판정한다. */
    public static ProblemQuestion create(QuestionSourceType sourceType, String sourceRef,
                                         String sourceDatasetCode, ProblemQuestion derivedFrom,
                                         Long subUnitId, String topicCode, short difficulty,
                                         QuestionType questionType,
                                         QuestionPresentation presentation, String contentBlocks,
                                         String promptText, String explanation,
                                         String learningGuide, String hintText,
                                         VerificationStatus verificationStatus) {
        return new ProblemQuestion(sourceType, sourceRef, sourceDatasetCode, derivedFrom, subUnitId,
                topicCode, difficulty, questionType, presentation, contentBlocks, promptText,
                explanation, learningGuide, hintText, verificationStatus, null);
    }

    /** 작성 Snapshot의 평가 영역까지 보존해 문항을 생성한다. */
    public static ProblemQuestion createAuthored(QuestionSourceType sourceType, ProblemQuestion derivedFrom,
            Long subUnitId, String topicCode, short difficulty, EvaluationArea evaluationArea,
            QuestionType questionType, QuestionPresentation presentation, String contentBlocks,
            String promptText, String explanation, String learningGuide, VerificationStatus verificationStatus) {
        return new ProblemQuestion(sourceType, null, null, derivedFrom, subUnitId, topicCode, difficulty,
                questionType, presentation, contentBlocks, promptText, explanation, learningGuide,
                null, verificationStatus, evaluationArea);
    }

    public void attachSemanticModel(SemanticModelDocument document) {
        if (document == null) throw new IllegalArgumentException("semantic model document가 필요합니다.");
        if (semanticModelHash != null && !semanticModelHash.equals(document.sha256())) {
            throw new IllegalStateException("semantic model hash를 변경할 수 없습니다.");
        }
        semanticModelSchemaVersion = (short) document.schemaVersion(); semanticModel = document.json();
        semanticModelHash = document.sha256(); semanticModelStatus = SemanticModelStatus.READY;
    }

    public void markSemanticModelUnsupported() { clearSemanticModel(SemanticModelStatus.UNSUPPORTED); }
    public void markSemanticModelFailed() { clearSemanticModel(SemanticModelStatus.FAILED); }
    private void clearSemanticModel(SemanticModelStatus status) { semanticModelSchemaVersion = null; semanticModel = null; semanticModelHash = null; semanticModelStatus = status; }
}
