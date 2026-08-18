package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceRetrievalPort;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemRetrievalTracePort;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationSlotPlan;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/** 문제은행을 먼저 채우고 부족한 슬롯만 AI 명령으로 만드는 계획을 계산한다. */
@Service
public class ProblemGenerationPlanningService {
    private final ProblemQuestionSelector selector;
    private final ProblemBankSnapshotQueryService snapshotQueryService;
    private final ObjectProvider<ProblemReferenceRetrievalPort> retrievalPort;
    private final ObjectProvider<ProblemRetrievalTracePort> tracePort;
    private final ProblemRagProperties ragProperties;

    public ProblemGenerationPlanningService(ProblemQuestionSelector selector,
                                            ProblemBankSnapshotQueryService snapshotQueryService) {
        this(selector, snapshotQueryService, null, null, null);
    }

    /** 검색·추적 Port가 선택적으로 연결된 생성 계획 서비스를 구성한다. */
    @Autowired
    public ProblemGenerationPlanningService(ProblemQuestionSelector selector,
                                            ProblemBankSnapshotQueryService snapshotQueryService,
                                            ObjectProvider<ProblemReferenceRetrievalPort> retrievalPort,
                                            ObjectProvider<ProblemRetrievalTracePort> tracePort,
                                            ProblemRagProperties ragProperties) {
        this.selector = selector;
        this.snapshotQueryService = snapshotQueryService;
        this.retrievalPort = retrievalPort;
        this.tracePort = tracePort;
        this.ragProperties = ragProperties;
    }

    /** 요청 조건을 화면 순서가 보존된 실행 계획으로 변환한다. */
    public ProblemGenerationPlan plan(UUID clientRequestId, GenerationJobType jobType,
                                      List<ProblemGenerationRequirement> requirements) {
        List<ProblemGenerationSlotPlan> slots = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        int index = 1;
        for (ProblemGenerationRequirement requirement : requirements) {
            List<ProblemQuestion> bank = selector.selectAvailable(requirement.subUnitId(),
                requirement.difficulty(), requirement.questionType(), Integer.MAX_VALUE, selectedIds);
            List<Long> candidateIds = bank.stream().map(ProblemQuestion::getId).toList();
            List<BankSnapshotResult> snapshotResults = snapshotQueryService.getSnapshots(candidateIds);
            selectedIds.addAll(candidateIds);
            java.util.Map<Long, BankSnapshotResult> resultById = snapshotResults.stream()
                    .collect(java.util.stream.Collectors.toMap(BankSnapshotResult::questionId, result -> result));
            int reusableCount = 0;
            for (ProblemQuestion question : bank) {
                BankSnapshotResult result = resultById.get(question.getId());
                if (result == null || !result.reusable()) continue;
                selectedIds.add(question.getId());
                reusableCount++;
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.BANK_REUSE,
                    question.getId(), result.snapshot(), result.assetStorageKeys(), null));
                if (reusableCount == requirement.count()) break;
            }
            int shortage = requirement.count() - reusableCount;
            for (int i = 0; i < shortage; i++) {
                ProblemGenerationCommand command = createGenerationCommand(requirement, selectedIds);
                selectedIds.addAll(command.references().stream().map(GenerationReference::sourceQuestionId)
                        .filter(java.util.Objects::nonNull).toList());
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.AI_GENERATION,
                    null, command));
            }
        }
        return new ProblemGenerationPlan(clientRequestId, jobType, slots);
    }

    private ProblemGenerationCommand createGenerationCommand(ProblemGenerationRequirement requirement,
                                                              Set<Long> excludedQuestionIds) {
        boolean enabled = ragProperties != null && ragProperties.enabled();
        ProblemReferenceRetrievalPort port = !enabled || retrievalPort == null
                ? null : retrievalPort.getIfAvailable();
        UUID retrievalRequestId = enabled && port != null ? UUID.randomUUID() : null;
        List<GenerationReference> references = new ArrayList<>(requirement.references());
        if (retrievalRequestId != null) {
            references.addAll(retrieveReferences(requirement, retrievalRequestId, excludedQuestionIds));
        }
        return new ProblemGenerationCommand(UUID.randomUUID(), retrievalRequestId, requirement.purpose(),
                requirement.specification(), requirement.curriculum(), references, requirement.conceptEvidence());
    }

    private List<GenerationReference> retrieveReferences(ProblemGenerationRequirement requirement,
                                                           UUID retrievalRequestId, Set<Long> excludedQuestionIds) {
        ProblemReferenceRetrievalPort port = retrievalPort.getIfAvailable();
        if (port == null) return List.of();
        try {
            return port.retrieve(createRetrievalQuery(requirement, retrievalRequestId, excludedQuestionIds)).stream()
                    .map(reference -> new GenerationReference(GenerationReferenceRole.EXAMPLE,
                            reference.questionId(), reference.snapshot())).toList();
        } catch (RuntimeException exception) {
            if (tracePort != null && tracePort.getIfAvailable() != null) {
                tracePort.getIfAvailable().recordFallback(
                        createRetrievalQuery(requirement, retrievalRequestId, excludedQuestionIds),
                        com.cenedu.backend.domain.problem.authoring.retrieval.RetrievalFallbackReason.PROVIDER_FAILURE);
            }
            return List.of();
        }
    }

    private ProblemReferenceQuery createRetrievalQuery(ProblemGenerationRequirement requirement,
                                                        UUID retrievalRequestId, Set<Long> excludedQuestionIds) {
        GenerationReference origin = requirement.references().stream()
                .filter(reference -> reference.role() == GenerationReferenceRole.ORIGIN).findFirst().orElse(null);
        int selectionLimit = requirement.purpose() == com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose.GENERAL_LEARNING_SHORTAGE
                || requirement.purpose() == com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE ? 3 : 4;
        return new ProblemReferenceQuery(retrievalRequestId, requirement.purpose(), requirement.curriculum(),
                requirement.questionType(), difficultyLabel(requirement.difficulty()),
                origin == null ? null : origin.sourceQuestionId(), origin == null ? null : origin.snapshot(),
                ragProperties == null ? 40 : ragProperties.candidateLimit(), selectionLimit,
                Set.copyOf(excludedQuestionIds));
    }

    private String difficultyLabel(short difficulty) {
        return switch (difficulty) { case 1 -> "low"; case 3 -> "high"; default -> "mid"; };
    }
}
