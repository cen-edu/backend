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
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.springframework.stereotype.Service;

/** 최신 재출제 제안과 교사 수량을 실행 가능한 맞춤 생성 계획으로 변환한다. */
@Service
public class PersonalizedProblemGenerationPlanningService {
    private final ProblemBankSnapshotQueryService snapshotQueryService;

    public PersonalizedProblemGenerationPlanningService(ProblemBankSnapshotQueryService snapshotQueryService) {
        this.snapshotQueryService = snapshotQueryService;
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
        appendSimilarSlots(slots, proposal, requests, curriculumPaths);
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
                                    Map<Long, CurriculumPathResponse> paths) {
        for (ReissueProposalResponse.SubUnitProposal subUnit : proposal.subcategories()) {
            CustomProblemGenerationItemRequest request = requests.get(subUnit.subUnitId());
            if (request == null) continue;
            for (int i = 0; i < request.similarCount(); i++) {
                slots.add(aiSlot(subUnit, paths.get(subUnit.subUnitId()), CustomStage.SIMILAR,
                        GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE));
            }
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
                slots.add(aiSlot(subUnit, paths.get(subUnit.subUnitId()), CustomStage.ADVANCED,
                        GenerationPurpose.PERSONALIZED_APPLICATION));
            }
        }
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
        CurriculumScope curriculum = new CurriculumScope(path.curriculumRevision(), path.schoolLevel(),
                path.grade(), path.semester() == null ? null : path.semester().intValue(),
                path.achievementStandardId(), path.subUnitId(),
                path.majorUnitName(), path.middleUnitName(), path.subUnitName());
        String difficulty = stage == CustomStage.ADVANCED ? "high" : subUnit.similar().difficulty();
        GenerationSpecification specification = new GenerationSpecification(
                QuestionType.STEP_FILL, difficulty, null, List.of());
        ProblemGenerationCommand command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                purpose, specification, curriculum,
                List.of(new GenerationReference(GenerationReferenceRole.ORIGIN, originId, null)), List.of());
        return new ProblemGenerationSlotPlan(1, GenerationSlotSource.AI_GENERATION, null,
                originId, stage, null, Map.of(), command);
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
