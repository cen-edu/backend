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
import org.springframework.transaction.annotation.Transactional;

/** 멱등 Job과 문항별 Item을 생성하고 독립 실행·재시도·집계를 관리한다. */
@Service
public class ProblemGenerationJobService {

    private final ProblemGenerationJobRepository jobRepository;
    private final ProblemGenerationItemRepository itemRepository;
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ProblemAuthoringVersionService versionService;
    private final ObjectProvider<ProblemRetrievalTracePort> tracePort;

    public ProblemGenerationJobService(ProblemGenerationJobRepository jobRepository,
                                       ProblemGenerationItemRepository itemRepository,
                                       ProblemAuthoringSessionRepository sessionRepository,
                                       ProblemAuthoringJsonCodec jsonCodec,
                                       ProblemAuthoringVersionService versionService) {
        this(jobRepository, itemRepository, sessionRepository, jsonCodec, versionService, null);
    }

    /** retrieval trace 연결 Port를 선택적으로 주입해 기존 Job 저장 계약을 유지한다. */
    @org.springframework.beans.factory.annotation.Autowired
    public ProblemGenerationJobService(ProblemGenerationJobRepository jobRepository,
                                       ProblemGenerationItemRepository itemRepository,
                                       ProblemAuthoringSessionRepository sessionRepository,
                                       ProblemAuthoringJsonCodec jsonCodec,
                                       ProblemAuthoringVersionService versionService,
                                       ObjectProvider<ProblemRetrievalTracePort> tracePort) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.sessionRepository = sessionRepository;
        this.jsonCodec = jsonCodec;
        this.versionService = versionService;
        this.tracePort = tracePort;
    }

    /** 문제은행 재사용과 AI 생성 슬롯을 하나의 멱등 Job으로 저장한다. */
    @Transactional
    public ProblemGenerationJobResult create(long ownerTeacherId, ProblemGenerationPlan plan) {
        validatePlan(plan);
        return jobRepository.findByOwnerTeacherIdAndClientRequestId(ownerTeacherId, plan.clientRequestId())
                .map(this::toResult)
                .orElseGet(() -> createPlanned(ownerTeacherId, plan));
    }

    /** clientRequestId를 멱등 키로 사용해 Job과 문항별 Session·Item을 생성한다. */
    @Transactional
    public ProblemGenerationJobResult create(long ownerTeacherId,
                                             ProblemGenerationBatchCommand batch) {
        validateBatch(batch);
        return jobRepository.findByOwnerTeacherIdAndClientRequestId(
                        ownerTeacherId, batch.clientRequestId())
                .map(this::toResult)
                .orElseGet(() -> createNew(ownerTeacherId, batch));
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
                        session.getId(), slot.sourceQuestionId()));
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
                        command.purpose(), 1, jsonCodec.write(command)));
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
                        item.getStatus(), item.getRetryCount(), item.getLastErrorCode()))
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
