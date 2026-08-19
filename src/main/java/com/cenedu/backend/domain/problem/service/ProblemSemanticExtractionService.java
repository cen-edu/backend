package com.cenedu.backend.domain.problem.service;

import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticExtractionPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.*;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.ProblemSemanticDocumentCodec;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.SemanticModelStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** 기존 문항의 semantic model을 요청 시 한 번만 추출하고 원본 snapshot은 보존한다. */
@Service
public class ProblemSemanticExtractionService {
    private final ProblemQuestionRepository questionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final ProblemSemanticExtractionPort extractionPort;
    private final ProblemSemanticMaterializer materializer;
    private final ProblemSemanticDocumentCodec codec;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public ProblemSemanticExtractionService(ProblemQuestionRepository questionRepository,
            ProblemAuthoringVersionRepository versionRepository,
            ProblemSemanticExtractionPort extractionPort,
            ProblemSemanticMaterializer materializer,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.extractionPort = extractionPort;
        this.materializer = materializer;
        this.codec = new ProblemSemanticDocumentCodec(objectMapper);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Version의 원본 question을 확인한 뒤 semantic extraction 결과를 짧은 transaction으로 저장한다. */
    public SemanticExtractionResult ensureVersionSemantic(long ownerTeacherId, long sessionId,
            long versionId, CurriculumScope curriculum) {
        ProblemAuthoringVersion version = versionRepository.findByIdAndSessionId(versionId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("authoring version을 찾을 수 없습니다."));
        if (version.getSourceQuestionId() == null) {
            return new SemanticExtractionResult(SemanticExtractionStatus.UNSUPPORTED, null,
                    java.util.List.of("source question이 없습니다."));
        }
        return ensureQuestionSemantic(version.getSourceQuestionId(), curriculum,
                readSnapshot(version.getSnapshot()));
    }

    /** 저장된 상태를 먼저 확인하고 필요한 경우에만 provider를 호출한다. */
    public SemanticExtractionResult ensureQuestionSemantic(long questionId,
            CurriculumScope curriculum, QuestionSnapshotV1 snapshot) {
        ProblemQuestion stored = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("question을 찾을 수 없습니다."));
        if (stored.getSemanticModelStatus() == SemanticModelStatus.UNSUPPORTED) {
            return new SemanticExtractionResult(SemanticExtractionStatus.UNSUPPORTED, null, java.util.List.of());
        }
        if (stored.getSemanticModelStatus() == SemanticModelStatus.READY
                && stored.getSemanticModel() != null) {
            try {
                var model = codec.readSemanticModel(stored.getSemanticModel());
                materializer.materialize(model);
                return new SemanticExtractionResult(SemanticExtractionStatus.EXTRACTED, model, java.util.List.of());
            } catch (RuntimeException exception) {
                // 손상된 READY document는 재추출 가능한 FAILED 상태로 되돌린다.
                markFailed(questionId);
            }
        }
        SemanticExtractionResult extracted;
        try {
            extracted = extractionPort.extract(new SemanticExtractionCommand(
                    UUID.randomUUID(), questionId, curriculum, snapshot));
        } catch (RuntimeException exception) {
            extracted = new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR,
                    null, java.util.List.of("provider 호출 실패"));
        }
        return persistResult(questionId, snapshot, extracted);
    }

    private SemanticExtractionResult persistResult(long questionId, QuestionSnapshotV1 source,
            SemanticExtractionResult result) {
        if (result == null) result = new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR,
                null, java.util.List.of("empty extraction result"));
        SemanticExtractionResult finalResult = result;
        if (result.status() == SemanticExtractionStatus.EXTRACTED && result.semanticModel() != null) {
            try {
                MaterializedProblem materialized = materializer.materialize(result.semanticModel());
                if (!answerCompatible(materialized, source)) {
                    finalResult = new SemanticExtractionResult(SemanticExtractionStatus.INVALID_SOURCE, null,
                            java.util.List.of("materialized snapshot이 원본과 일치하지 않습니다."));
                }
            } catch (RuntimeException exception) {
                finalResult = new SemanticExtractionResult(SemanticExtractionStatus.INVALID_SOURCE, null,
                        java.util.List.of("semantic model이 원본 snapshot과 일치하지 않습니다."));
            }
        }
        SemanticExtractionResult toStore = finalResult;
        transactionTemplate.executeWithoutResult(status -> {
            ProblemQuestion question = questionRepository.findByIdForUpdate(questionId)
                    .orElseThrow(() -> new IllegalArgumentException("question을 찾을 수 없습니다."));
            switch (toStore.status()) {
                case EXTRACTED -> question.attachSemanticModel(codec.semanticModel(toStore.semanticModel()));
                case UNSUPPORTED -> question.markSemanticModelUnsupported();
                case INVALID_SOURCE, TECHNICAL_ERROR -> question.markSemanticModelFailed();
            }
        });
        return toStore;
    }

    private boolean answerCompatible(MaterializedProblem materialized, QuestionSnapshotV1 source) {
        if (materialized.snapshot().metadata().questionType() != source.metadata().questionType()) return false;
        var generated = materialized.snapshot().answerUnits();
        var original = source.answerUnits();
        if (generated.size() != original.size()) return false;
        for (int i = 0; i < generated.size(); i++) {
            if (!java.util.Objects.equals(generated.get(i).answerNormalized(), original.get(i).answerNormalized())
                    && !java.util.Objects.equals(generated.get(i).answerRaw(), original.get(i).answerRaw())) return false;
        }
        return true;
    }

    private void markFailed(long questionId) {
        transactionTemplate.executeWithoutResult(status -> questionRepository.findByIdForUpdate(questionId)
                .ifPresent(ProblemQuestion::markSemanticModelFailed));
    }

    private QuestionSnapshotV1 readSnapshot(String json) {
        try { return new tools.jackson.databind.ObjectMapper().readValue(json, QuestionSnapshotV1.class); }
        catch (Exception exception) { throw new IllegalArgumentException("snapshot을 읽을 수 없습니다.", exception); }
    }
}
