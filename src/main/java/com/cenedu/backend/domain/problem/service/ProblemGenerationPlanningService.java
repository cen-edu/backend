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
import com.cenedu.backend.domain.problem.authoring.retrieval.RetrievedProblemReference;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 문제은행을 먼저 채우고 부족한 슬롯만 AI 명령으로 만드는 계획을 계산한다. */
@Service
public class ProblemGenerationPlanningService {
    private static final Logger log = LoggerFactory.getLogger(ProblemGenerationPlanningService.class);
    private final ProblemQuestionSelector selector;
    private final ProblemBankSnapshotQueryService snapshotQueryService;
    private final ObjectProvider<ProblemReferenceRetrievalPort> retrievalPort;
    private final ObjectProvider<ProblemRetrievalTracePort> tracePort;
    private final ProblemRagProperties ragProperties;
    private final java.util.concurrent.ExecutorService planningExecutor;

    public ProblemGenerationPlanningService(ProblemQuestionSelector selector,
                                            ProblemBankSnapshotQueryService snapshotQueryService) {
        this(selector, snapshotQueryService, null, null, null, null);
    }

    /** 검색·추적 Port가 선택적으로 연결된 생성 계획 서비스를 구성한다. */
    public ProblemGenerationPlanningService(ProblemQuestionSelector selector,
                                            ProblemBankSnapshotQueryService snapshotQueryService,
                                            ObjectProvider<ProblemReferenceRetrievalPort> retrievalPort,
                                            ObjectProvider<ProblemRetrievalTracePort> tracePort,
                                            ProblemRagProperties ragProperties) {
        this(selector, snapshotQueryService, retrievalPort, tracePort, ragProperties, null);
    }

    /** 부족분 RAG 검색을 요구(소단원) 간 병렬 실행할 fan-out 풀을 연결한다(null이면 순차 실행). */
    @Autowired
    public ProblemGenerationPlanningService(ProblemQuestionSelector selector,
                                            ProblemBankSnapshotQueryService snapshotQueryService,
                                            ObjectProvider<ProblemReferenceRetrievalPort> retrievalPort,
                                            ObjectProvider<ProblemRetrievalTracePort> tracePort,
                                            ProblemRagProperties ragProperties,
                                            @org.springframework.beans.factory.annotation.Qualifier("problemPlanningRetrievalExecutor")
                                            java.util.concurrent.ExecutorService planningExecutor) {
        this.selector = selector;
        this.snapshotQueryService = snapshotQueryService;
        this.retrievalPort = retrievalPort;
        this.tracePort = tracePort;
        this.ragProperties = ragProperties;
        this.planningExecutor = planningExecutor;
    }

    /** 요청 조건을 화면 순서가 보존된 실행 계획으로 변환한다.
     *
     * <p>Pass 1(순차): 은행 선택으로 재사용 슬롯과 부족분 수량을 확정한다. 전역 {@code selectedIds}를
     * 순차로 쌓아 소단원 간 은행 문항 중복 재사용을 막는 정합성을 그대로 보존한다.
     * <p>Pass 2(요구 간 병렬): 부족분 RAG 검색만 fan-out으로 병렬 실행한다. 요구 내부의 부족 슬롯
     * 루프는 순차를 유지해 같은 소단원 안의 예시 다양성(누적 제외)을 보존한다. 검색은 few-shot 예시
     * 제공용이라 정답·문항 수에 영향이 없고, 소단원 간 예시가 약간 더 겹칠 수 있는 것이 유일한 차이다.
     * <p>Pass 3(순차): 요청 순서대로 재사용·AI 슬롯을 이어붙이고 화면 순서 인덱스를 매긴다. */
    public ProblemGenerationPlan plan(UUID clientRequestId, GenerationJobType jobType,
                                      List<ProblemGenerationRequirement> requirements) {
        Set<Long> selectedIds = new HashSet<>();
        List<RequirementStage> stages = new ArrayList<>(requirements.size());
        for (ProblemGenerationRequirement requirement : requirements) {
            List<ProblemQuestion> bank = selector.selectAvailable(requirement.subUnitId(),
                requirement.difficulty(), requirement.questionType(), Integer.MAX_VALUE, selectedIds);
            List<Long> candidateIds = bank.stream().map(ProblemQuestion::getId).toList();
            List<BankSnapshotResult> snapshotResults = snapshotQueryService.getSnapshots(candidateIds);
            java.util.Map<Long, BankSnapshotResult> resultById = snapshotResults.stream()
                    .collect(java.util.stream.Collectors.toMap(BankSnapshotResult::questionId, result -> result));
            List<BankReuse> reuses = new ArrayList<>();
            for (ProblemQuestion question : bank) {
                BankSnapshotResult result = resultById.get(question.getId());
                if (result == null || !result.reusable()) continue;
                selectedIds.add(question.getId());
                reuses.add(new BankReuse(question.getId(), result.snapshot(), result.assetStorageKeys()));
                if (reuses.size() == requirement.count()) break;
            }
            int shortage = requirement.count() - reuses.size();
            stages.add(new RequirementStage(requirement, reuses, shortage, Set.copyOf(selectedIds)));
        }

        List<java.util.concurrent.Callable<List<ProblemGenerationCommand>>> tasks = stages.stream()
                .map(stage -> (java.util.concurrent.Callable<List<ProblemGenerationCommand>>)
                        () -> buildShortageCommands(stage))
                .toList();
        List<List<ProblemGenerationCommand>> commandsPerStage =
                OrderedParallelPlanner.map(planningExecutor, tasks);

        List<ProblemGenerationSlotPlan> slots = new ArrayList<>();
        int index = 1;
        for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
            RequirementStage stage = stages.get(stageIndex);
            for (BankReuse reuse : stage.reuses()) {
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.BANK_REUSE,
                    reuse.questionId(), reuse.snapshot(), reuse.assetStorageKeys(), null));
            }
            for (ProblemGenerationCommand command : commandsPerStage.get(stageIndex)) {
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.AI_GENERATION,
                    null, command));
            }
        }
        log.info("event=problem_authoring_plan stage=PLANNING outcome=SUCCESS clientRequestId={} jobType={} "
                        + "slotCount={} bankReuseCount={} aiGenerationCount={} ragEnabled={}",
                clientRequestId, jobType,
                slots.size(),
                slots.stream().filter(slot -> slot.source() == GenerationSlotSource.BANK_REUSE).count(),
                slots.stream().filter(slot -> slot.source() == GenerationSlotSource.AI_GENERATION).count(),
                ragProperties != null && ragProperties.enabled());
        return new ProblemGenerationPlan(clientRequestId, jobType, slots);
    }

    /** 한 요구의 부족분 AI 명령을 순차로 만든다. 요구-지역 제외 집합을 은행 확정 시점의 스냅샷으로
     *  시작해, 같은 소단원 안에서 생성되는 문항끼리는 예시가 겹치지 않도록 누적 제외를 보존한다. */
    private List<ProblemGenerationCommand> buildShortageCommands(RequirementStage stage) {
        if (stage.shortage() <= 0) return List.of();
        List<ProblemGenerationCommand> commands = new ArrayList<>(stage.shortage());
        Set<Long> excluded = new HashSet<>(stage.exclusionSeed());
        for (int i = 0; i < stage.shortage(); i++) {
            ProblemGenerationCommand command = createGenerationCommand(stage.requirement(), excluded);
            excluded.addAll(command.references().stream().map(GenerationReference::sourceQuestionId)
                    .filter(java.util.Objects::nonNull).toList());
            commands.add(command);
        }
        return commands;
    }

    private ProblemGenerationCommand createGenerationCommand(ProblemGenerationRequirement requirement,
                                                              Set<Long> excludedQuestionIds) {
        boolean enabled = ragProperties != null && ragProperties.enabled();
        ProblemReferenceRetrievalPort port = !enabled || retrievalPort == null
                ? null : retrievalPort.getIfAvailable();
        log.info("event=problem_retrieval stage=PLANNING outcome=STARTED enabled={} providerPresent={} "
                        + "purpose={} subUnitId={} excludedCount={}",
                enabled, port != null, requirement.purpose(), requirement.subUnitId(), excludedQuestionIds.size());
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
        long startedAt = System.nanoTime();
        try {
            List<RetrievedProblemReference> retrieved = port.retrieve(createRetrievalQuery(requirement, retrievalRequestId, excludedQuestionIds));
            log.info("event=problem_retrieval stage=RAG outcome=SUCCESS requestId={} candidateReferenceCount={} elapsedMs={}",
                    retrievalRequestId, retrieved.size(), elapsedMs(startedAt));
            return retrieved.stream()
                    .map(reference -> new GenerationReference(GenerationReferenceRole.EXAMPLE,
                            reference.questionId(), reference.snapshot())).toList();
        } catch (RuntimeException exception) {
            log.warn("event=problem_retrieval stage=RAG outcome=FALLBACK requestId={} elapsedMs={} exceptionType={}",
                    retrievalRequestId, elapsedMs(startedAt), exception.getClass().getSimpleName());
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

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /** Pass 1에서 확정한 한 요구의 은행 재사용·부족분 수량·검색 제외 시드를 담는 중간 결과다. */
    private record RequirementStage(ProblemGenerationRequirement requirement,
                                    List<BankReuse> reuses, int shortage, Set<Long> exclusionSeed) {
    }

    /** 은행 재사용 슬롯 하나에 필요한 최소 정보다. 최종 슬롯 인덱스는 Pass 3에서 매긴다. */
    private record BankReuse(Long questionId,
                             com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 snapshot,
                             java.util.Map<String, String> assetStorageKeys) {
    }
}
