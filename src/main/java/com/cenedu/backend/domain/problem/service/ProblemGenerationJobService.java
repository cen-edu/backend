package com.cenedu.backend.domain.problem.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationBatchCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationSlotPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationItemResult;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationJobResult;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationWorkItem;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemGenerationItem;
import com.cenedu.backend.domain.problem.entity.ProblemGenerationJob;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemGenerationItemRepository;
import com.cenedu.backend.domain.problem.repository.ProblemGenerationJobRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemRetrievalTracePort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 멱등 Job과 문항별 Item을 생성하고 독립 실행·재시도·집계를 관리한다. */
@Service
public class ProblemGenerationJobService {

    private final ProblemGenerationJobRepository jobRepository;
    private final ProblemGenerationItemRepository itemRepository;
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ProblemAuthoringVersionService versionService;
    private final ObjectProvider<ProblemRetrievalTracePort> tracePort;
    private final TransactionTemplate transactionTemplate;

    public ProblemGenerationJobService(ProblemGenerationJobRepository jobRepository,
                                       ProblemGenerationItemRepository itemRepository,
                                       ProblemAuthoringSessionRepository sessionRepository,
                                       ProblemAuthoringJsonCodec jsonCodec,
                                       ProblemAuthoringVersionService versionService,
                                       PlatformTransactionManager transactionManager) {
        this(jobRepository, itemRepository, sessionRepository, jsonCodec, versionService,
                transactionManager, null);
    }

    /** retrieval trace 연결 Port를 선택적으로 주입해 기존 Job 저장 계약을 유지한다. */
    @org.springframework.beans.factory.annotation.Autowired
    public ProblemGenerationJobService(ProblemGenerationJobRepository jobRepository,
                                       ProblemGenerationItemRepository itemRepository,
                                       ProblemAuthoringSessionRepository sessionRepository,
                                       ProblemAuthoringJsonCodec jsonCodec,
                                       ProblemAuthoringVersionService versionService,
                                       PlatformTransactionManager transactionManager,
                                       ObjectProvider<ProblemRetrievalTracePort> tracePort) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.sessionRepository = sessionRepository;
        this.jsonCodec = jsonCodec;
        this.versionService = versionService;
        this.tracePort = tracePort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 계획 수립(RAG 임베딩·벡터검색) 이전 단계에서 clientRequestId로 기존 Job을 조회하는
     *  읽기 전용 멱등 조회다. 존재하면 호출부가 비싼 계획 수립을 건너뛰도록 한다.
     *  {@link #create} 내부 멱등 체크는 동시성 레이스 안전망으로 그대로 유지한다. */
    @Transactional(readOnly = true)
    public Optional<ProblemGenerationJobResult> findByClientRequestId(long ownerTeacherId,
                                                                      java.util.UUID clientRequestId) {
        return jobRepository.findByOwnerTeacherIdAndClientRequestId(ownerTeacherId, clientRequestId)
                .map(this::toResult);
    }

    /** 문제은행 재사용과 AI 생성 슬롯을 하나의 멱등 Job으로 저장한다. */
    public ProblemGenerationJobResult create(long ownerTeacherId, ProblemGenerationPlan plan) {
        validatePlan(plan);
        return createIdempotent(ownerTeacherId, plan.clientRequestId(),
                () -> createPlanned(ownerTeacherId, plan));
    }

    /** clientRequestId를 멱등 키로 사용해 Job과 문항별 Session·Item을 생성한다. */
    public ProblemGenerationJobResult create(long ownerTeacherId,
                                             ProblemGenerationBatchCommand batch) {
        validateBatch(batch);
        return createIdempotent(ownerTeacherId, batch.clientRequestId(),
                () -> createNew(ownerTeacherId, batch));
    }

    /**
     * clientRequestId를 멱등 키로 Job을 만든다. 먼저 조회해 있으면 그대로 반환하고, 없으면 별도
     * 트랜잭션에서 생성한다.
     *
     * <p>find-then-insert 사이에 <b>진짜 동시</b> 요청 둘이 모두 조회를 통과할 수 있다. 그때는
     * 유니크 제약 {@code (owner_teacher_id, client_request_id)}이 후발 insert를 막아
     * {@link DataIntegrityViolationException}을 던진다. PostgreSQL은 선발이 커밋된 뒤에야 이 예외를
     * 내므로, 잡아서 재조회하면 선발이 만든 Job이 반드시 보인다 — 이를 반환해 멱등성을 지킨다.
     * 생성은 {@code TransactionTemplate}로 별도 트랜잭션에서 수행해, 위반으로 트랜잭션이 rollback-only가
     * 된 뒤에도 바깥에서 깨끗한 재조회가 가능하게 한다.
     */
    private ProblemGenerationJobResult createIdempotent(long ownerTeacherId,
                                                        java.util.UUID clientRequestId,
                                                        java.util.function.Supplier<ProblemGenerationJobResult> creator) {
        ProblemGenerationJobResult existing = findResultByClientRequestId(ownerTeacherId, clientRequestId);
        if (existing != null) {
            return existing;
        }
        try {
            return transactionTemplate.execute(status -> creator.get());
        } catch (DataIntegrityViolationException race) {
            ProblemGenerationJobResult created = findResultByClientRequestId(ownerTeacherId, clientRequestId);
            if (created != null) {
                return created;
            }
            throw race;
        }
    }

    private ProblemGenerationJobResult findResultByClientRequestId(long ownerTeacherId,
                                                                   java.util.UUID clientRequestId) {
        return jobRepository.findByOwnerTeacherIdAndClientRequestId(ownerTeacherId, clientRequestId)
                .map(this::toResult).orElse(null);
    }

    /** 멱등 재요청이 동시에 와도 QUEUED Item을 한 Worker만 원자적으로 선점한다. */
    @Transactional
    public Optional<ProblemGenerationWorkItem> tryClaim(Long itemId) {
        ProblemGenerationItem item = getItemForUpdate(itemId);
        if (item.getStatus() != GenerationItemStatus.QUEUED) {
            return Optional.empty();
        }
        ProblemGenerationJob job = getJobForUpdate(item.getJobId());
        item.startGeneration();
        if (job.getStatus() == GenerationJobStatus.QUEUED) {
            job.start();
        }
        return Optional.of(new ProblemGenerationWorkItem(
                item.getId(), job.getId(), job.getOwnerTeacherId(), item.getSessionId(),
                jsonCodec.read(item.getGenerationCommand(), ProblemGenerationCommand.class)));
    }

    /** 후보 생성 후 Item을 의미 검증 중으로 전이한다. */
    @Transactional
    public void startVerification(Long itemId) {
        getItemForUpdate(itemId).startVerification();
    }

    /** 생성·검증 실패를 상한 내에서 재시도 상태로 돌린다. */
    @Transactional
    public boolean prepareRetry(ProblemGenerationWorkItem workItem, String errorCode) {
        ProblemGenerationItem item = getItemForUpdate(workItem.itemId());
        getOwnedJob(item.getJobId(), workItem.ownerTeacherId());
        if (!item.canRetry()) {
            return false;
        }
        item.retryGeneration(errorCode);
        ProblemAuthoringSession session = sessionRepository
                .findOwnedByIdForUpdate(workItem.sessionId(), workItem.ownerTeacherId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        session.prepareRetry(false, errorCode);
        return true;
    }

    /** PASSED Version이 current로 승격된 Item을 성공으로 종료하고 Job을 집계한다. */
    @Transactional
    public void succeed(ProblemGenerationWorkItem workItem) {
        ProblemGenerationItem item = getItemForUpdate(workItem.itemId());
        getOwnedJob(item.getJobId(), workItem.ownerTeacherId());
        item.succeed();
        aggregateJob(item.getJobId());
    }

    /** 재시도를 소진한 Item과 Session을 실패로 마감하고 Job을 집계한다. */
    @Transactional
    public void fail(ProblemGenerationWorkItem workItem, String errorCode) {
        ProblemGenerationItem item = getItemForUpdate(workItem.itemId());
        getOwnedJob(item.getJobId(), workItem.ownerTeacherId());
        item.fail(errorCode);
        ProblemAuthoringSession session = sessionRepository
                .findOwnedByIdForUpdate(workItem.sessionId(), workItem.ownerTeacherId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        session.failOperation(errorCode);
        aggregateJob(item.getJobId());
    }

    /** 교사가 요청한 Job과 문항별 진행 상태를 요청 순서대로 반환한다. */
    @Transactional(readOnly = true)
    public ProblemGenerationJobResult get(long ownerTeacherId, long jobId) {
        return toResult(jobRepository.findByIdAndOwnerTeacherId(jobId, ownerTeacherId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_GENERATION_JOB_NOT_FOUND)));
    }

    private ProblemGenerationJobResult createNew(long ownerTeacherId,
                                                 ProblemGenerationBatchCommand batch) {
        ProblemGenerationJob job = jobRepository.saveAndFlush(ProblemGenerationJob.create(
                ownerTeacherId, batch.clientRequestId(), batch.jobType()));
        for (int index = 0; index < batch.items().size(); index++) {
            ProblemGenerationCommand command = batch.items().get(index);
            ProblemAuthoringSession session = sessionRepository.saveAndFlush(
                    ProblemAuthoringSession.createGenerating(ownerTeacherId));
            itemRepository.save(ProblemGenerationItem.create(
                    job.getId(), index + 1, command.requestId(), session.getId(),
                    command.purpose(), 1, jsonCodec.write(command)));
        }
        return toResult(job);
    }

    private ProblemGenerationJobResult createPlanned(long ownerTeacherId, ProblemGenerationPlan plan) {
        ProblemGenerationJob job = jobRepository.saveAndFlush(ProblemGenerationJob.create(
                ownerTeacherId, plan.clientRequestId(), plan.jobType()));
        boolean hasAi = false;
        for (ProblemGenerationSlotPlan slot : plan.slots()) {
            ProblemAuthoringSession session = sessionRepository.saveAndFlush(
                    ProblemAuthoringSession.createGenerating(ownerTeacherId));
            if (slot.source() == GenerationSlotSource.BANK_REUSE) {
                itemRepository.save(ProblemGenerationItem.createBankReuse(
                        job.getId(), slot.slotIndex(), java.util.UUID.randomUUID(),
                        session.getId(), slot.sourceQuestionId(), slot.customStage()));
                versionService.saveBankReuse(ownerTeacherId, session.getId(),
                        slot.sourceQuestionId(), jsonCodec.write(slot.sourceSnapshot()),
                        jsonCodec.write(java.util.Map.of(
                                "schemaVersion", 1,
                                "plans", java.util.List.of(),
                                "artifacts", slot.sourceAssetStorageKeys().entrySet().stream()
                                        .map(entry -> java.util.Map.of(
                                                "assetKey", entry.getKey(),
                                                "status", "READY",
                                                "draftStorageKey", entry.getValue(),
                                                "attemptCount", 0)).toList())));
            } else {
                hasAi = true;
                ProblemGenerationCommand command = slot.generationCommand();
                ProblemGenerationItem savedItem = itemRepository.save(ProblemGenerationItem.create(
                        job.getId(), slot.slotIndex(), command.requestId(), session.getId(),
                        command.purpose(), 1, jsonCodec.write(command),
                        slot.customStage(), slot.originQuestionId()));
                linkGeneration(command, job, savedItem);
            }
        }
        if (!hasAi) job.completeWithoutExecution();
        return toResult(job);
    }

    private void linkGeneration(ProblemGenerationCommand command, ProblemGenerationJob job, ProblemGenerationItem item) {
        if (command.retrievalRequestId() == null || tracePort == null) return;
        ProblemRetrievalTracePort trace = tracePort.getIfAvailable();
        if (trace == null) return;
        try { trace.linkGeneration(command.retrievalRequestId(), job.getId(), item.getId()); }
        catch (RuntimeException exception) { /* telemetry must not reverse Job creation */ }
    }

    private void aggregateJob(Long jobId) {
        ProblemGenerationJob job = getJobForUpdate(jobId);
        List<ProblemGenerationItem> items = itemRepository
                .findAllByJobIdOrderByItemOrder(jobId);
        if (items.stream().anyMatch(item -> !isTerminal(item.getStatus()))) {
            return;
        }
        long successes = items.stream()
                .filter(item -> item.getStatus() == GenerationItemStatus.SUCCEEDED)
                .count();
        GenerationJobStatus status = successes == items.size()
                ? GenerationJobStatus.COMPLETED
                : successes == 0
                ? GenerationJobStatus.FAILED
                : GenerationJobStatus.PARTIALLY_FAILED;
        job.complete(status);
    }

    private ProblemGenerationJobResult toResult(ProblemGenerationJob job) {
        List<ProblemGenerationItemResult> items = itemRepository
                .findAllByJobIdOrderByItemOrder(job.getId()).stream()
                .map(item -> new ProblemGenerationItemResult(
                        item.getId(), item.getSessionId(), item.getItemOrder(),
                        item.getStatus(), item.getRetryCount(), item.getLastErrorCode(),
                        item.getSlotSource(), item.getSourceQuestionId(), item.getOriginQuestionId(),
                        item.getCustomStage()))
                .toList();
        return new ProblemGenerationJobResult(job.getId(), job.getStatus(), items);
    }

    private void validateBatch(ProblemGenerationBatchCommand batch) {
        if (batch == null || batch.clientRequestId() == null || batch.jobType() == null
                || batch.items() == null || batch.items().isEmpty()) {
            throw new IllegalArgumentException("생성 Job 필수값이 누락되었습니다.");
        }
        Set<java.util.UUID> requestIds = new HashSet<>();
        for (ProblemGenerationCommand command : batch.items()) {
            if (command == null || command.requestId() == null
                    || !requestIds.add(command.requestId())) {
                throw new IllegalArgumentException("Item requestId는 필수이고 중복될 수 없습니다.");
            }
            if (!matches(batch.jobType(), command.purpose())) {
                throw new IllegalArgumentException("Job 유형과 생성 목적이 일치하지 않습니다.");
            }
        }
    }

    private void validatePlan(ProblemGenerationPlan plan) {
        if (plan == null) throw new IllegalArgumentException("생성 계획이 필요합니다.");
        Set<java.util.UUID> requestIds = new HashSet<>();
        for (ProblemGenerationSlotPlan slot : plan.slots()) {
            if (slot.source() == GenerationSlotSource.AI_GENERATION) {
                ProblemGenerationCommand command = slot.generationCommand();
                if (!requestIds.add(command.requestId()) || !matches(plan.jobType(), command.purpose())) {
                    throw new IllegalArgumentException("생성 계획의 요청 ID 또는 목적이 올바르지 않습니다.");
                }
            }
        }
    }

    private boolean matches(GenerationJobType jobType, GenerationPurpose purpose) {
        return switch (jobType) {
            case GENERAL_LEARNING -> purpose == GenerationPurpose.GENERAL_LEARNING_SHORTAGE;
            case COMPREHENSIVE_ASSESSMENT ->
                    purpose == GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE;
            case PERSONALIZED -> purpose == GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE
                    || purpose == GenerationPurpose.PERSONALIZED_APPLICATION;
        };
    }

    private boolean isTerminal(GenerationItemStatus status) {
        return status == GenerationItemStatus.SUCCEEDED
                || status == GenerationItemStatus.FAILED;
    }

    private ProblemGenerationItem getItemForUpdate(Long itemId) {
        return itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_GENERATION_ITEM_NOT_FOUND));
    }

    private ProblemGenerationJob getJobForUpdate(Long jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_GENERATION_JOB_NOT_FOUND));
    }

    private ProblemGenerationJob getOwnedJob(Long jobId, Long ownerTeacherId) {
        ProblemGenerationJob job = getJobForUpdate(jobId);
        if (!job.getOwnerTeacherId().equals(ownerTeacherId)) {
            throw new BusinessException(ErrorCode.PROBLEM_GENERATION_JOB_NOT_FOUND);
        }
        return job;
    }
}
