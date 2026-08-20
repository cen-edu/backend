package com.cenedu.backend.domain.problem.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationJobResult;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationRequirement;
import com.cenedu.backend.domain.problem.dto.request.AsyncAssessmentGenerationRequest;
import com.cenedu.backend.domain.problem.dto.request.AsyncProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.AuthoringProblemSnapshotResponse;
import com.cenedu.backend.domain.problem.dto.response.AuthoringSlotDisplayStatus;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationJobStatusResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationSlotResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.springframework.stereotype.Service;

/** 동기 생성 API를 유지하면서 S3 Job 기반 비동기 생성을 제공한다. */
@Service
public class ProblemAsyncGenerationService {
    private static final Set<QuestionType> ASSESSMENT_QUESTION_TYPES = Set.of(
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.SHORT_INPUT,
            QuestionType.ESSAY);

    private final ProblemGenerationPlanningService planningService;
    private final ProblemGenerationJobService jobService;
    private final ProblemGenerationAsyncRunner runner;
    private final ProblemSnapshotQueryService snapshotQueryService;
    private final CurriculumUnitQueryService curriculumQueryService;

    public ProblemAsyncGenerationService(ProblemGenerationPlanningService planningService,
                                         ProblemGenerationJobService jobService,
                                         ProblemGenerationAsyncRunner runner,
                                         ProblemSnapshotQueryService snapshotQueryService,
                                         CurriculumUnitQueryService curriculumQueryService) {
        this.planningService = planningService;
        this.jobService = jobService;
        this.runner = runner;
        this.snapshotQueryService = snapshotQueryService;
        this.curriculumQueryService = curriculumQueryService;
    }

    /** 일반학습 STEP_FILL 요청을 Job으로 접수하고 AI 부족분만 병렬 실행한다. */
    public ProblemGenerationStartResponse startGeneral(long teacherId, AsyncProblemGenerationRequest request) {
        Set<Long> ids = new HashSet<>();
        request.items().forEach(item -> ids.add(item.subUnitId()));
        Map<Long, CurriculumPathResponse> paths = curriculumQueryService.getPathsBySubUnitIds(ids);
        List<ProblemGenerationRequirement> requirements = request.items().stream().map(item -> requirement(
                item.subUnitId(), item.difficulty(), QuestionType.STEP_FILL, item.count(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, paths.get(item.subUnitId()))).toList();
        return createAndRun(teacherId, request.clientRequestId(), GenerationJobType.GENERAL_LEARNING, requirements);
    }

    /** 종합평가 요청을 Job으로 접수하고 AI 부족분만 병렬 실행한다. */
    public ProblemGenerationStartResponse startAssessment(long teacherId, AsyncAssessmentGenerationRequest request) {
        Set<Long> ids = new HashSet<>();
        request.items().forEach(item -> {
            validateAssessmentQuestionType(item.questionType());
            ids.add(item.subUnitId());
        });
        Map<Long, CurriculumPathResponse> paths = curriculumQueryService.getPathsBySubUnitIds(ids);
        List<ProblemGenerationRequirement> requirements = request.items().stream().map(item -> requirement(
                item.subUnitId(), item.difficulty(), item.questionType(), item.count(),
                GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE, paths.get(item.subUnitId()))).toList();
        return createAndRun(teacherId, request.clientRequestId(), GenerationJobType.COMPREHENSIVE_ASSESSMENT, requirements);
    }

    /** 검증된 맞춤 계획을 멱등 Job으로 저장하고 AI 슬롯만 비동기 실행한다. */
    public ProblemGenerationStartResponse startPersonalized(long teacherId, ProblemGenerationPlan plan) {
        if (plan == null || plan.jobType() != GenerationJobType.PERSONALIZED) {
            throw new IllegalArgumentException("맞춤 생성 계획만 접수할 수 있습니다.");
        }
        return createAndRun(teacherId, plan);
    }

    /** 교사 소유 Job의 전체 상태와 문항별 미리보기를 반환한다. */
    public ProblemGenerationJobStatusResponse getStatus(long teacherId, long jobId) {
        ProblemGenerationJobResult job = jobService.get(teacherId, jobId);
        List<ProblemGenerationSlotResponse> slots = job.items().stream().map(item -> {
            AuthoringProblemSnapshotResponse preview = item.status() == GenerationItemStatus.SUCCEEDED
                    ? snapshotQueryService.getCurrent(teacherId, item.sessionId()) : null;
            AuthoringSlotDisplayStatus display = switch (item.status()) {
                case QUEUED -> AuthoringSlotDisplayStatus.QUEUED;
                case GENERATING -> AuthoringSlotDisplayStatus.GENERATING_CONTENT;
                case VERIFYING -> AuthoringSlotDisplayStatus.VERIFYING;
                case SUCCEEDED -> AuthoringSlotDisplayStatus.READY;
                case FAILED -> AuthoringSlotDisplayStatus.FAILED;
            };
            return new ProblemGenerationSlotResponse(item.itemOrder(), item.itemId(), item.sessionId(),
                    display, preview, item.errorCode(),
                    item.status() == GenerationItemStatus.FAILED && item.retryCount() < 2);
        }).toList();
        int completed = (int) job.items().stream().filter(item -> item.status() == GenerationItemStatus.SUCCEEDED
                || item.status() == GenerationItemStatus.FAILED).count();
        return new ProblemGenerationJobStatusResponse(job.jobId(), job.status(), slots.size(), completed, slots);
    }

    /** 생성 계획을 멱등 Job으로 저장하고 대기 문항만 비동기 실행한다. */
    private ProblemGenerationStartResponse createAndRun(long teacherId, java.util.UUID clientRequestId,
                                                         GenerationJobType type,
                                                         List<ProblemGenerationRequirement> requirements) {
        ProblemGenerationJobResult job = jobService.create(teacherId,
                planningService.plan(clientRequestId, type, requirements));
        job.items().stream().filter(item -> item.status() == GenerationItemStatus.QUEUED)
                .forEach(item -> runner.execute(item.itemId()));
        return new ProblemGenerationStartResponse(job.jobId(), job.status(), job.items().size());
    }

    /** 이미 수립된 계획을 Job으로 저장하고 대기 AI Item만 실행한다. */
    private ProblemGenerationStartResponse createAndRun(long teacherId, ProblemGenerationPlan plan) {
        ProblemGenerationJobResult job = jobService.create(teacherId, plan);
        job.items().stream().filter(item -> item.status() == GenerationItemStatus.QUEUED)
                .forEach(item -> runner.execute(item.itemId()));
        return new ProblemGenerationStartResponse(job.jobId(), job.status(), job.items().size());
    }

    /** HTTP 요청의 한 조건을 은행 탐색과 AI 생성이 공유하는 요구 계약으로 변환한다. */
    private ProblemGenerationRequirement requirement(Long subUnitId, short difficulty,
                                                     QuestionType type, int count,
                                                     GenerationPurpose purpose,
                                                     CurriculumPathResponse path) {
        String difficultyLabel = switch (difficulty) { case 1 -> "low"; case 2 -> "mid"; case 3 -> "high";
            default -> throw new IllegalArgumentException("지원하지 않는 난이도입니다."); };
        CurriculumScope context = new CurriculumScope(path.curriculumRevision(), path.schoolLevel(),
                path.grade(), path.semester() == null ? null : path.semester().intValue(), path.achievementStandardId(), subUnitId,
                path.majorUnitName(), path.middleUnitName(), path.subUnitName());
        return new ProblemGenerationRequirement(subUnitId, difficulty, type, count, purpose,
                new GenerationSpecification(type, difficultyLabel, null, List.of()), context,
                List.of(), List.of());
    }

    /** 종합평가에서 허용하는 객관식·주관식·서술형인지 확인한다. */
    private void validateAssessmentQuestionType(QuestionType questionType) {
        if (!ASSESSMENT_QUESTION_TYPES.contains(questionType)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_QUESTION_TYPE_NOT_ALLOWED);
        }
    }
}
