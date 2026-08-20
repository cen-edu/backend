package com.cenedu.backend.domain.problem.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.analysis.reissue.ReissueProposalService;
import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import org.springframework.stereotype.Service;

/** 최신 취약 제안을 재검증하고 맞춤 생성 Job을 접수한다. */
@Service
public class CustomProblemGenerationService {
    private final ReissueProposalService proposalService;
    private final CustomProblemGenerationRequestValidator validator;
    private final CurriculumUnitQueryService curriculumUnitQueryService;
    private final PersonalizedProblemGenerationPlanningService planningService;
    private final ProblemAsyncGenerationService asyncGenerationService;

    public CustomProblemGenerationService(ReissueProposalService proposalService,
                                          CustomProblemGenerationRequestValidator validator,
                                          CurriculumUnitQueryService curriculumUnitQueryService,
                                          PersonalizedProblemGenerationPlanningService planningService,
                                          ProblemAsyncGenerationService asyncGenerationService) {
        this.proposalService = proposalService;
        this.validator = validator;
        this.curriculumUnitQueryService = curriculumUnitQueryService;
        this.planningService = planningService;
        this.asyncGenerationService = asyncGenerationService;
    }

    /** 최신 취약 제안과 교육과정 경로를 결합해 맞춤 생성 Job을 시작한다. */
    public ProblemGenerationStartResponse start(long teacherId, CustomProblemGenerationRequest request) {
        ReissueProposalResponse proposal = proposalService.getProposal(teacherId,
                request.sourceAssignmentId(), request.studentId());
        validator.validate(request, proposal);
        Set<Long> requestedIds = new HashSet<>();
        request.items().forEach(item -> requestedIds.add(item.subUnitId()));
        Map<Long, CurriculumPathResponse> paths = curriculumUnitQueryService
                .getPathsBySubUnitIds(requestedIds);
        ProblemGenerationPlan plan = planningService.plan(request.clientRequestId(), proposal,
                request.items(), paths);
        return asyncGenerationService.startPersonalized(teacherId, plan);
    }
}
