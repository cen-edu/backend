package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationSlotPlan;
import com.cenedu.backend.domain.problem.authoring.generation.PersonalizedGenerationEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationEvaluationAreaEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationDiagnosticEvidence;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceRetrievalPort;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemRetrievalTracePort;
import com.cenedu.backend.domain.problem.authoring.retrieval.RetrievalFallbackReason;
import com.cenedu.backend.domain.problem.authoring.retrieval.RetrievedProblemReference;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

/** 최신 재출제 제안과 교사 수량을 실행 가능한 맞춤 생성 계획으로 변환한다. */
@Service
public class PersonalizedProblemGenerationPlanningService {
    private final ProblemBankSnapshotQueryService snapshotQueryService;
    private final ObjectProvider<ProblemReferenceRetrievalPort> retrievalProvider;
    private final ObjectProvider<ProblemRetrievalTracePort> traceProvider;
    private final ProblemRagProperties ragProperties;

    public PersonalizedProblemGenerationPlanningService(ProblemBankSnapshotQueryService snapshotQueryService) {
        this(snapshotQueryService, null, null, null);
    }

    /** RAG 검색과 fallback 추적 Port를 선택적으로 연결해 계획기를 구성한다. */
    public PersonalizedProblemGenerationPlanningService(
            ProblemBankSnapshotQueryService snapshotQueryService,
            ObjectProvider<ProblemReferenceRetrievalPort> retrievalProvider,
            ObjectProvider<ProblemRetrievalTracePort> traceProvider,
            ProblemRagProperties ragProperties) {
        this.snapshotQueryService = snapshotQueryService;
        this.retrievalProvider = retrievalProvider;
        this.traceProvider = traceProvider;
        this.ragProperties = ragProperties;
    }

    /** 교육과정 순서와 REVIEW·SIMILAR·ADVANCED 단계 순서를 보존한 계획을 만든다. */
    public ProblemGenerationPlan plan(UUID clientRequestId, ReissueProposalResponse proposal,
                                      List<CustomProblemGenerationItemRequest> items,
                                      Map<Long, CurriculumPathResponse> curriculumPaths) {
        if (clientRequestId == null || proposal == null || items == null || curriculumPaths == null) {
            throw new IllegalArgumentException("맞춤 생성 계획 입력이 필요합니다.");
        }
        Map<Long, CustomProblemGenerationItemRequest> requests = toRequestMap(items);
        List<ProblemGenerationSlotPlan> slots = new ArrayList<>();
        appendReviewSlots(slots, proposal, requests);
        appendSimilarSlots(slots, proposal, requests, curriculumPaths,
                slots.stream().map(ProblemGenerationSlotPlan::sourceQuestionId)
                        .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        appendAdvancedSlots(slots, proposal, requests, curriculumPaths);
        return new ProblemGenerationPlan(clientRequestId, GenerationJobType.PERSONALIZED, reindex(slots));
    }

    /** 요청을 수량 조회용 Map으로 만들고 중복 소단원을 거절한다. */
    private Map<Long, CustomProblemGenerationItemRequest> toRequestMap(List<CustomProblemGenerationItemRequest> items) {
        Map<Long, CustomProblemGenerationItemRequest> result = new LinkedHashMap<>();
        for (CustomProblemGenerationItemRequest item : items) {
            if (item == null || item.subUnitId() == null || item.totalCount() < 1
                    || result.put(item.subUnitId(), item) != null) {
                throw new IllegalArgumentException("맞춤 생성 소단원 요청이 올바르지 않습니다.");
            }
        }
        return result;
    }

    /** 제안의 교육과정 순서대로 REVIEW 은행 재사용 슬롯을 먼저 붙인다. */
    private void appendReviewSlots(List<ProblemGenerationSlotPlan> slots,
                                   ReissueProposalResponse proposal,
                                   Map<Long, CustomProblemGenerationItemRequest> requests) {
        for (ReissueProposalResponse.SubUnitProposal subUnit : proposal.subcategories()) {
            CustomProblemGenerationItemRequest request = requests.get(subUnit.subUnitId());
            if (request == null || request.reviewCount() == 0) continue;
            List<Long> candidates = subUnit.review().candidateQuestionIds();
            if (candidates == null || candidates.size() < request.reviewCount()) {
                throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
            }
            List<Long> selected = candidates.subList(0, request.reviewCount());
            List<BankSnapshotResult> results = snapshotQueryService.getSnapshots(selected);
            Map<Long, BankSnapshotResult> byId = results.stream()
                    .collect(java.util.stream.Collectors.toMap(BankSnapshotResult::questionId,
                            value -> value, (first, second) -> first));
            for (Long questionId : selected) {
                BankSnapshotResult result = byId.get(questionId);
                if (result == null || !result.reusable()) {
                    throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
                }
                slots.add(new ProblemGenerationSlotPlan(1, GenerationSlotSource.BANK_REUSE,
                        questionId, null, CustomStage.REVIEW, result.snapshot(),
                        result.assetStorageKeys(), null));
            }
        }
    }

    /** SIMILAR 단계의 AI 슬롯 뼈대를 교육과정 순서대로 붙인다. */
    private void appendSimilarSlots(List<ProblemGenerationSlotPlan> slots,
                                    ReissueProposalResponse proposal,
                                    Map<Long, CustomProblemGenerationItemRequest> requests,
                                    Map<Long, CurriculumPathResponse> paths,
                                    java.util.Set<Long> alreadyReusedIds) {
        for (ReissueProposalResponse.SubUnitProposal subUnit : proposal.subcategories()) {
            CustomProblemGenerationItemRequest request = requests.get(subUnit.subUnitId());
            if (request == null) continue;
            appendSimilarForSubUnit(slots, subUnit, request.similarCount(), paths.get(subUnit.subUnitId()),
                    alreadyReusedIds);
        }
    }

    /** 한 소단원의 ORIGIN·검색 결과를 은행 재사용과 AI 부족분으로 분할한다. */
    private void appendSimilarForSubUnit(List<ProblemGenerationSlotPlan> slots,
                                         ReissueProposalResponse.SubUnitProposal subUnit,
                                         int requestedCount, CurriculumPathResponse path,
                                         java.util.Set<Long> alreadyReusedIds) {
        if (requestedCount == 0) return;
        ReissueProposalResponse.SimilarProposal similar = subUnit.similar();
        if (similar.referenceQuestions() == null || similar.referenceQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
        long originId = similar.referenceQuestions().getFirst().questionId();
        List<Long> referenceIds = similar.referenceQuestions().stream()
                .map(ReissueProposalResponse.ReferenceQuestion::questionId).distinct().toList();
        Map<Long, BankSnapshotResult> references = snapshotsById(referenceIds);
        BankSnapshotResult origin = references.get(originId);
        if (origin == null || !origin.reusable()) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
        CurriculumScope curriculum = curriculum(path);
        UUID retrievalRequestId = UUID.randomUUID();
        List<RetrievedProblemReference> retrieved = retrieveSimilar(subUnit, similar, requestedCount,
                curriculum, originId, origin.snapshot(), alreadyReusedIds, retrievalRequestId);
        List<RetrievedProblemReference> selected = retrieved.stream().limit(Math.min(4, requestedCount)).toList();
        List<Long> selectedIds = selected.stream().map(RetrievedProblemReference::questionId).distinct().toList();
        Map<Long, BankSnapshotResult> selectedSnapshots = snapshotsById(selectedIds);
        List<GenerationReference> examples = new ArrayList<>();
        examples.add(new GenerationReference(GenerationReferenceRole.ORIGIN, originId, origin.snapshot()));
        for (Long referenceId : referenceIds) {
            if (referenceId.equals(originId)) continue;
            BankSnapshotResult reference = references.get(referenceId);
            if (reference != null && reference.reusable()) {
                examples.add(new GenerationReference(GenerationReferenceRole.EXAMPLE,
                        referenceId, reference.snapshot()));
            }
        }
        for (RetrievedProblemReference reference : selected) {
            examples.add(new GenerationReference(GenerationReferenceRole.EXAMPLE,
                    reference.questionId(), reference.snapshot()));
        }
        for (Long questionId : selectedIds) {
            BankSnapshotResult result = selectedSnapshots.get(questionId);
            if (result == null || !result.reusable()) continue;
            slots.add(new ProblemGenerationSlotPlan(1, GenerationSlotSource.BANK_REUSE,
                    questionId, null, CustomStage.SIMILAR, result.snapshot(),
                    result.assetStorageKeys(), null));
            alreadyReusedIds.add(questionId);
        }
        int shortage = requestedCount - selectedIds.size();
        for (int i = 0; i < shortage; i++) {
            slots.add(aiSlot(subUnit, path, CustomStage.SIMILAR,
                    GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE, originId, curriculum, examples));
        }
    }

    /** ORIGIN과 검색 후보 Snapshot을 ID 기준으로 보존한다. */
    private Map<Long, BankSnapshotResult> snapshotsById(List<Long> ids) {
        return snapshotQueryService.getSnapshots(ids).stream().collect(
                java.util.stream.Collectors.toMap(BankSnapshotResult::questionId, value -> value,
                        (first, second) -> first, LinkedHashMap::new));
    }

    /** RAG가 꺼졌거나 실패하면 빈 결과로 전환하고 fallback을 기록한다. */
    private List<RetrievedProblemReference> retrieveSimilar(
            ReissueProposalResponse.SubUnitProposal subUnit,
            ReissueProposalResponse.SimilarProposal similar, int requestedCount,
            CurriculumScope curriculum, long originId, com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 originSnapshot,
            java.util.Set<Long> alreadyReusedIds, UUID retrievalRequestId) {
        ProblemReferenceRetrievalPort port = ragProperties != null && ragProperties.enabled()
                && retrievalProvider != null ? retrievalProvider.getIfAvailable() : null;
        ProblemReferenceQuery query = new ProblemReferenceQuery(retrievalRequestId,
                GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE, curriculum, QuestionType.STEP_FILL,
                similar.difficulty(), originId, originSnapshot,
                ragProperties == null ? 40 : ragProperties.candidateLimit(),
                Math.min(4, requestedCount), excludedIds(similar, alreadyReusedIds));
        if (port == null) {
            recordFallback(query, RetrievalFallbackReason.PORT_UNAVAILABLE);
            return List.of();
        }
        try {
            return port.retrieve(query);
        } catch (RuntimeException exception) {
            recordFallback(query, RetrievalFallbackReason.PROVIDER_FAILURE);
            return List.of();
        }
    }

    private java.util.Set<Long> excludedIds(ReissueProposalResponse.SimilarProposal similar,
                                            java.util.Set<Long> alreadyReusedIds) {
        java.util.Set<Long> result = new java.util.HashSet<>(alreadyReusedIds);
        if (similar.excludedQuestionIds() != null) result.addAll(similar.excludedQuestionIds());
        return result;
    }

    private void recordFallback(ProblemReferenceQuery query, RetrievalFallbackReason reason) {
        if (traceProvider != null && traceProvider.getIfAvailable() != null) {
            traceProvider.getIfAvailable().recordFallback(query, reason);
        }
    }

    /** ADVANCED 단계의 AI 슬롯 뼈대를 교육과정 순서대로 붙인다. */
    private void appendAdvancedSlots(List<ProblemGenerationSlotPlan> slots,
                                     ReissueProposalResponse proposal,
                                     Map<Long, CustomProblemGenerationItemRequest> requests,
                                     Map<Long, CurriculumPathResponse> paths) {
        for (ReissueProposalResponse.SubUnitProposal subUnit : proposal.subcategories()) {
            CustomProblemGenerationItemRequest request = requests.get(subUnit.subUnitId());
            if (request == null) continue;
            for (int i = 0; i < request.advancedCount(); i++) {
                slots.add(advancedAiSlot(subUnit, paths.get(subUnit.subUnitId())));
            }
        }
    }

    /** 취약 분포와 풀이 단계를 포함한 ADVANCED AI 슬롯을 만든다. */
    private ProblemGenerationSlotPlan advancedAiSlot(
            ReissueProposalResponse.SubUnitProposal subUnit, CurriculumPathResponse path) {
        ReissueProposalResponse.SimilarProposal similar = subUnit.similar();
        if (path == null || similar.referenceQuestions() == null || similar.referenceQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
        long originId = similar.referenceQuestions().getFirst().questionId();
        Map<Long, BankSnapshotResult> references = snapshotsById(similar.referenceQuestions().stream()
                .map(ReissueProposalResponse.ReferenceQuestion::questionId).distinct().toList());
        BankSnapshotResult origin = references.get(originId);
        if (origin == null || !origin.reusable()) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
        CurriculumScope curriculum = curriculum(path);
        List<GenerationReference> generationReferences = new ArrayList<>();
        generationReferences.add(new GenerationReference(GenerationReferenceRole.ORIGIN,
                originId, origin.snapshot()));
        for (ReissueProposalResponse.ReferenceQuestion reference : similar.referenceQuestions()) {
            if (reference.questionId() == originId) continue;
            BankSnapshotResult example = references.get(reference.questionId());
            if (example != null && example.reusable()) {
                generationReferences.add(new GenerationReference(GenerationReferenceRole.EXAMPLE,
                        reference.questionId(), example.snapshot()));
            }
        }
        generationReferences.addAll(retrieveAdvancedExamples(curriculum, originId, origin.snapshot()));
        PersonalizedGenerationEvidence evidence = evidence(subUnit.advanced());
        List<DiagnosticType> diagnosticTypes = evidence.diagnosticEvidence().stream()
                .map(GenerationDiagnosticEvidence::diagnosticType).toList();
        GenerationSpecification specification = new GenerationSpecification(
                QuestionType.STEP_FILL, "high", subUnit.advanced().primaryEvaluationArea(),
                diagnosticTypes, true);
        ProblemGenerationCommand command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.PERSONALIZED_APPLICATION, specification, curriculum,
                generationReferences, List.of(), evidence);
        return new ProblemGenerationSlotPlan(1, GenerationSlotSource.AI_GENERATION, null,
                originId, CustomStage.ADVANCED, null, Map.of(), command);
    }

    /** ADVANCED 검색 결과는 재사용하지 않고 AI 명령의 EXAMPLE로만 전달한다. */
    private List<GenerationReference> retrieveAdvancedExamples(
            CurriculumScope curriculum, long originId,
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 originSnapshot) {
        if (ragProperties == null || !ragProperties.enabled() || retrievalProvider == null
                || retrievalProvider.getIfAvailable() == null) {
            return List.of();
        }
        UUID requestId = UUID.randomUUID();
        ProblemReferenceQuery query = new ProblemReferenceQuery(requestId,
                GenerationPurpose.PERSONALIZED_APPLICATION, curriculum, QuestionType.STEP_FILL,
                "high", originId, originSnapshot, ragProperties.candidateLimit(),
                Math.min(4, ragProperties.candidateLimit()), java.util.Set.of());
        try {
            return retrievalProvider.getIfAvailable().retrieve(query).stream()
                    .map(reference -> new GenerationReference(GenerationReferenceRole.EXAMPLE,
                            reference.questionId(), reference.snapshot())).toList();
        } catch (RuntimeException exception) {
            recordFallback(query, RetrievalFallbackReason.PROVIDER_FAILURE);
            return List.of();
        }
    }

    /** 분석 응답의 ADVANCED 근거를 문제 생성 도메인의 독립 계약으로 복사한다. */
    private PersonalizedGenerationEvidence evidence(ReissueProposalResponse.AdvancedProposal advanced) {
        List<GenerationEvaluationAreaEvidence> areaEvidence = advanced.evaluationAreaEvidence() == null
                ? List.of() : advanced.evaluationAreaEvidence().stream()
                .map(value -> new GenerationEvaluationAreaEvidence(value.evaluationArea(),
                        value.gradedItemCount(), value.incorrectItemCount(), value.incorrectRate()))
                .toList();
        List<GenerationDiagnosticEvidence> diagnosticEvidence = advanced.diagnosticStageEvidence() == null
                ? List.of() : advanced.diagnosticStageEvidence().stream()
                .map(value -> new GenerationDiagnosticEvidence(
                        DiagnosticType.valueOf(value.diagnosticType().name()),
                        value.gradedUnitCount(), value.incorrectUnitCount(), value.incorrectRate()))
                .toList();
        return new PersonalizedGenerationEvidence(advanced.historicalIncorrectItemCount(),
                advanced.incorrectSessionCount(), areaEvidence, diagnosticEvidence);
    }

    /** 기준 문항과 교육과정 경로를 보존하는 맞춤 AI 슬롯을 만든다. */
    private ProblemGenerationSlotPlan aiSlot(ReissueProposalResponse.SubUnitProposal subUnit,
                                             CurriculumPathResponse path, CustomStage stage,
                                             GenerationPurpose purpose) {
        if (path == null || subUnit.similar().referenceQuestions() == null
                || subUnit.similar().referenceQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
        long originId = subUnit.similar().referenceQuestions().getFirst().questionId();
        CurriculumScope curriculum = curriculum(path);
        return aiSlot(subUnit, path, stage, purpose, originId, curriculum,
                List.of(new GenerationReference(GenerationReferenceRole.ORIGIN, originId, null)));
    }

    /** 이미 조회한 ORIGIN과 검색 예시를 사용해 AI 부족분 명령을 만든다. */
    private ProblemGenerationSlotPlan aiSlot(ReissueProposalResponse.SubUnitProposal subUnit,
                                             CurriculumPathResponse path, CustomStage stage,
                                             GenerationPurpose purpose, long originId,
                                             CurriculumScope curriculum,
                                             List<GenerationReference> references) {
        String difficulty = stage == CustomStage.ADVANCED ? "high" : subUnit.similar().difficulty();
        GenerationSpecification specification = new GenerationSpecification(
                QuestionType.STEP_FILL, difficulty, null, List.of());
        ProblemGenerationCommand command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                purpose, specification, curriculum, references, List.of());
        return new ProblemGenerationSlotPlan(1, GenerationSlotSource.AI_GENERATION, null,
                originId, stage, null, Map.of(), command);
    }

    /** 교육과정 응답을 생성 계약에서 사용하는 범위 객체로 변환한다. */
    private CurriculumScope curriculum(CurriculumPathResponse path) {
        if (path == null) throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        return new CurriculumScope(path.curriculumRevision(), path.schoolLevel(), path.grade(),
                path.semester() == null ? null : path.semester().intValue(), path.achievementStandardId(),
                path.subUnitId(), path.majorUnitName(), path.middleUnitName(), path.subUnitName());
    }

    /** 3-pass로 만든 임시 슬롯 번호를 최종 화면 순서 번호로 다시 매긴다. */
    private List<ProblemGenerationSlotPlan> reindex(List<ProblemGenerationSlotPlan> slots) {
        List<ProblemGenerationSlotPlan> result = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            ProblemGenerationSlotPlan slot = slots.get(i);
            result.add(new ProblemGenerationSlotPlan(i + 1, slot.source(), slot.sourceQuestionId(),
                    slot.originQuestionId(), slot.customStage(), slot.sourceSnapshot(),
                    slot.sourceAssetStorageKeys(), slot.generationCommand()));
        }
        return result;
    }
}
