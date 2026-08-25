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
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
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
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemSemanticExtractionPort extractionPort;
    private final ProblemSemanticMaterializer materializer;
    private final ProblemSemanticDocumentCodec codec;
    private final TransactionTemplate transactionTemplate;

    public ProblemSemanticExtractionService(ProblemQuestionRepository questionRepository,
            ProblemAuthoringVersionRepository versionRepository,
            ProblemSemanticExtractionPort extractionPort,
            ProblemSemanticMaterializer materializer,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this(questionRepository, versionRepository, null, extractionPort, materializer,
                transactionManager, objectMapper);
    }

    @Autowired
    public ProblemSemanticExtractionService(ProblemQuestionRepository questionRepository,
            ProblemAuthoringVersionRepository versionRepository,
            ProblemAuthoringSessionRepository sessionRepository,
            ProblemSemanticExtractionPort extractionPort,
            ProblemSemanticMaterializer materializer,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.sessionRepository = sessionRepository;
        this.extractionPort = extractionPort;
        this.materializer = materializer;
        this.codec = new ProblemSemanticDocumentCodec(objectMapper);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Version의 원본 question을 확인한 뒤 semantic extraction 결과를 짧은 transaction으로 저장한다. */
    public SemanticExtractionResult ensureVersionSemantic(long ownerTeacherId, long sessionId,
            long versionId, CurriculumScope curriculum) {
        sessionRepository.findByIdAndOwnerTeacherId(sessionId, ownerTeacherId)
                .orElseThrow(() -> new IllegalArgumentException("authoring session 소유권이 없습니다."));
        ProblemAuthoringVersion version = versionRepository.findByIdAndSessionId(versionId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("authoring version을 찾을 수 없습니다."));
        Long sourceQuestionId = version.getSourceQuestionId();
        var session = sessionRepository.findByIdAndOwnerTeacherId(sessionId, ownerTeacherId).orElse(null);
        if (sourceQuestionId == null && session != null && session.getFinalizedQuestionId() != null) {
            sourceQuestionId = session.getFinalizedQuestionId();
        }
        if (sourceQuestionId == null) {
            return new SemanticExtractionResult(SemanticExtractionStatus.UNSUPPORTED, null,
                    java.util.List.of("source question이 없습니다."));
        }
        SemanticExtractionResult result = ensureQuestionSemantic(sourceQuestionId, curriculum,
                readSnapshot(version.getSnapshot()));
        if (result.status() == SemanticExtractionStatus.EXTRACTED && result.semanticModel() != null) {
            transactionTemplate.executeWithoutResult(status -> versionRepository.findByIdAndSessionId(versionId, sessionId)
                    .ifPresent(found -> found.attachSemanticModel(codec.semanticModel(result.semanticModel()))));
        }
        return result;
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

    /**
     * 아직 저장되지 않은 후보 snapshot에서 semantic model만 추출한다.
     *
     * <p>{@link #ensureQuestionSemantic}과 달리 problem_question 행이 없어도 되고 어떤 영속 상태도
     * 바꾸지 않는다. 구조 재생성처럼 방금 만들어진 후보에 semantic model을 붙여야 하는 경로에서
     * 쓴다. 추출 결과가 원본 후보와 다른 답을 내면 EXTRACTED로 인정하지 않는다.
     */
    public SemanticExtractionResult extractForCandidate(CurriculumScope curriculum,
            QuestionSnapshotV1 snapshot) {
        SemanticExtractionResult extracted;
        try {
            extracted = extractionPort.extract(new SemanticExtractionCommand(
                    UUID.randomUUID(), null, curriculum, snapshot));
        } catch (RuntimeException exception) {
            return new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR, null,
                    java.util.List.of("provider 호출 실패"));
        }
        if (extracted == null) {
            return new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR, null,
                    java.util.List.of("empty extraction result"));
        }
        if (extracted.status() != SemanticExtractionStatus.EXTRACTED || extracted.semanticModel() == null) {
            return extracted;
        }
        try {
            MaterializedProblem materialized = materializer.materialize(extracted.semanticModel());
            if (!sourceCompatible(materialized, snapshot)) {
                return new SemanticExtractionResult(SemanticExtractionStatus.INVALID_SOURCE, null,
                        java.util.List.of("materialized snapshot이 후보와 일치하지 않습니다."));
            }
        } catch (RuntimeException exception) {
            SemanticExtractionStatus status = unsupported(exception)
                    ? SemanticExtractionStatus.UNSUPPORTED : SemanticExtractionStatus.INVALID_SOURCE;
            return new SemanticExtractionResult(status, null,
                    java.util.List.of(status == SemanticExtractionStatus.UNSUPPORTED
                            ? "지원하지 않는 semantic operation 또는 diagram입니다."
                            : "semantic model이 후보 snapshot과 일치하지 않습니다."));
        }
        return extracted;
    }

    private SemanticExtractionResult persistResult(long questionId, QuestionSnapshotV1 source,
            SemanticExtractionResult result) {
        if (result == null) result = new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR,
                null, java.util.List.of("empty extraction result"));
        SemanticExtractionResult finalResult = result;
        if (result.status() == SemanticExtractionStatus.EXTRACTED && result.semanticModel() != null) {
            try {
                MaterializedProblem materialized = materializer.materialize(result.semanticModel());
                if (!sourceCompatible(materialized, source)) {
                    finalResult = new SemanticExtractionResult(SemanticExtractionStatus.INVALID_SOURCE, null,
                            java.util.List.of("materialized snapshot이 원본과 일치하지 않습니다."));
                }
            } catch (RuntimeException exception) {
                SemanticExtractionStatus status = unsupported(exception)
                        ? SemanticExtractionStatus.UNSUPPORTED : SemanticExtractionStatus.INVALID_SOURCE;
                finalResult = new SemanticExtractionResult(status, null,
                        java.util.List.of(status == SemanticExtractionStatus.UNSUPPORTED
                                ? "지원하지 않는 semantic operation 또는 diagram입니다."
                                : "semantic model이 원본 snapshot과 일치하지 않습니다."));
            }
        }
        SemanticExtractionResult toStore = finalResult;
        transactionTemplate.executeWithoutResult(status -> {
            ProblemQuestion question = questionRepository.findByIdForUpdate(questionId)
                    .orElseThrow(() -> new IllegalArgumentException("question을 찾을 수 없습니다."));
            switch (toStore.status()) {
                case EXTRACTED -> question.attachSemanticModel(codec.semanticModel(toStore.semanticModel()));
                case UNSUPPORTED -> question.markSemanticModelUnsupported();
                case INVALID_SOURCE, TECHNICAL_ERROR -> question.markSemanticModelFailed(
                        writeFindings(toStore.findings()));
            }
        });
        return toStore;
    }

    private boolean unsupported(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("지원하지") || message.contains("operation")
                || message.contains("diagram"));
    }

    /** 추출된 model이 원본과 같은 문항인지 정답과 구조 양쪽으로 확인한다. */
    private boolean sourceCompatible(MaterializedProblem materialized, QuestionSnapshotV1 source) {
        return answerCompatible(materialized, source) && structureCompatible(materialized, source);
    }

    /**
     * 원본에 없던 채점 기준·단계·보기를 추출이 지어내지 않았는지 확인한다.
     *
     * <p>채점 기준이 비어 있는 서술형 문항을 추출하면 LLM이 그럴듯한 기준을 만들어 낼 수 있다.
     * 그 model이 materialize에 성공하면 EXTRACTED로 저장되고, 이후 교사가 값 하나만 바꿔도
     * 원본에 없던 채점 기준이 문항에 조용히 생겨난다. 단계 수가 달라지는 빈칸형도 같은 문제다.
     * 정답만 비교해서는 이 차이를 잡을 수 없다.
     */
    private boolean structureCompatible(MaterializedProblem materialized, QuestionSnapshotV1 source) {
        QuestionSnapshotV1 generated = materialized.snapshot();
        return size(generated.choices()) == size(source.choices())
                && size(generated.steps()) == size(source.steps())
                && size(generated.rubricItems()) == size(source.rubricItems());
    }

    private int size(java.util.List<?> values) {
        return values == null ? 0 : values.size();
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

    private String writeFindings(java.util.List<String> findings) {
        try { return new ObjectMapper().writeValueAsString(findings == null ? java.util.List.of() : findings); }
        catch (Exception exception) { return "[]"; }
    }

    private QuestionSnapshotV1 readSnapshot(String json) {
        try { return new tools.jackson.databind.ObjectMapper().readValue(json, QuestionSnapshotV1.class); }
        catch (Exception exception) { throw new IllegalArgumentException("snapshot을 읽을 수 없습니다.", exception); }
    }
}
