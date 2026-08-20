package com.cenedu.backend.domain.problem.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.analysis.reissue.ReissueProposalService;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CustomProblemGenerationServiceTest {
    @Test
    void refreshesValidatesPlansAndStartsInOrder() {
        var proposals = mock(ReissueProposalService.class);
        var validator = mock(CustomProblemGenerationRequestValidator.class);
        var curriculum = mock(CurriculumUnitQueryService.class);
        var planner = mock(PersonalizedProblemGenerationPlanningService.class);
        var async = mock(ProblemAsyncGenerationService.class);
        var proposal = mock(ReissueProposalResponse.class);
        var plan = mock(ProblemGenerationPlan.class);
        var request = new CustomProblemGenerationRequest(UUID.randomUUID(), 120L, 35L,
                List.of(new CustomProblemGenerationItemRequest(20L, 1, 0, 0)));
        when(proposals.getProposal(7L, 120L, 35L)).thenReturn(proposal);
        when(curriculum.getPathsBySubUnitIds(any())).thenReturn(Map.of());
        when(planner.plan(eq(request.clientRequestId()), eq(proposal), eq(request.items()), any()))
                .thenReturn(plan);
        when(async.startPersonalized(7L, plan))
                .thenReturn(new ProblemGenerationStartResponse(9L, GenerationJobStatus.QUEUED, 1));

        new CustomProblemGenerationService(proposals, validator, curriculum, planner, async)
                .start(7L, request);

        InOrder order = inOrder(proposals, validator, curriculum, planner, async);
        order.verify(proposals).getProposal(7L, 120L, 35L);
        order.verify(validator).validate(request, proposal);
        order.verify(curriculum).getPathsBySubUnitIds(any());
        order.verify(planner).plan(eq(request.clientRequestId()), eq(proposal), eq(request.items()), any());
        order.verify(async).startPersonalized(7L, plan);
    }
}
