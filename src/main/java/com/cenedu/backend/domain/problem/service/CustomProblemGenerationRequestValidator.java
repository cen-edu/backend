package com.cenedu.backend.domain.problem.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationRequest;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Component;

/** 최신 재출제 제안과 교사가 요청한 맞춤 문항 수량의 일치 여부를 검증한다. */
@Component
public class CustomProblemGenerationRequestValidator {

    private static final int MAX_TOTAL_COUNT = 20;

    /** 맞춤 생성 요청이 최신 취약점 제안의 정책과 수량 상한을 만족하는지 확인한다. */
    public void validate(CustomProblemGenerationRequest request,
                         ReissueProposalResponse latestProposal) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_EMPTY_SELECTION);
        }
        if (latestProposal == null || latestProposal.subcategories() == null) {
            throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_SUB_UNIT_NOT_PROPOSED);
        }

        Map<Long, ReissueProposalResponse.SubUnitProposal> proposals = latestProposal.subcategories()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ReissueProposalResponse.SubUnitProposal::subUnitId,
                        proposal -> proposal,
                        (left, right) -> left,
                        HashMap::new));
        Set<Long> requestedSubUnitIds = new HashSet<>();
        int totalCount = 0;
        for (CustomProblemGenerationItemRequest item : request.items()) {
            if (item == null || item.subUnitId() == null) {
                throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_SUB_UNIT_NOT_PROPOSED);
            }
            if (!requestedSubUnitIds.add(item.subUnitId())) {
                throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_SUB_UNIT_DUPLICATED);
            }
            ReissueProposalResponse.SubUnitProposal proposal = proposals.get(item.subUnitId());
            if (proposal == null) {
                throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_SUB_UNIT_NOT_PROPOSED);
            }
            totalCount += item.totalCount();
            validateCounts(item, proposal);
        }
        if (totalCount == 0) {
            throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_EMPTY_SELECTION);
        }
        if (totalCount > MAX_TOTAL_COUNT) {
            throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_TOTAL_LIMIT_EXCEEDED);
        }
    }

    /** 소단원 하나의 단계별 수량이 최신 제안의 상한과 발동 조건을 넘지 않는지 확인한다. */
    private void validateCounts(CustomProblemGenerationItemRequest item,
                                ReissueProposalResponse.SubUnitProposal proposal) {
        if (item.similarCount() > 0 && proposal.similar().referenceQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_SIMILAR_REFERENCE_MISSING);
        }
        if (item.advancedCount() > 0 && !proposal.advanced().triggered()) {
            throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_ADVANCED_NOT_ALLOWED);
        }
        if (item.reviewCount() > proposal.review().maxCount()
                || item.similarCount() > proposal.similar().maxCount()
                || item.advancedCount() > proposal.advanced().maxCount()) {
            throw new BusinessException(ErrorCode.CUSTOM_PROBLEM_COUNT_EXCEEDS_PROPOSAL);
        }
    }
}
