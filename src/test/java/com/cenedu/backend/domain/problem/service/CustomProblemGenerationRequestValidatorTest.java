package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.analysis.reissue.ReissueProposalResponse;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationRequest;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.Test;

class CustomProblemGenerationRequestValidatorTest {

    private final CustomProblemGenerationRequestValidator validator =
            new CustomProblemGenerationRequestValidator();

    @Test
    void 유사_ORIGIN이_없는데_유사문제를_요청하면_거절한다() {
        CustomProblemGenerationRequest request = request(1, 1, 0);

        assertThatThrownBy(() -> validator.validate(request, proposal(false, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOM_PROBLEM_SIMILAR_REFERENCE_MISSING));
    }

    @Test
    void 응용이_발동되지_않았는데_응용문제를_요청하면_거절한다() {
        CustomProblemGenerationRequest request = request(1, 0, 1);

        assertThatThrownBy(() -> validator.validate(request, proposal(true, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOM_PROBLEM_ADVANCED_NOT_ALLOWED));
    }

    @Test
    void 중복_소단원과_20문항_초과를_거절한다() {
        CustomProblemGenerationRequest duplicated = new CustomProblemGenerationRequest(
                UUID.randomUUID(), 120L, 35L,
                List.of(requestItem(1, 0, 0), requestItem(1, 0, 0)));
        assertThatThrownBy(() -> validator.validate(duplicated, proposal(true, true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOM_PROBLEM_SUB_UNIT_DUPLICATED));

        CustomProblemGenerationRequest tooMany = new CustomProblemGenerationRequest(
                UUID.randomUUID(), 120L, 35L, List.of(requestItem(10, 10, 10)));
        assertThatThrownBy(() -> validator.validate(tooMany, proposal(true, true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOM_PROBLEM_TOTAL_LIMIT_EXCEEDED));
    }

    @Test
    void 유효한_요청은_통과한다() {
        validator.validate(request(1, 5, 0), proposal(true, true));
    }

    private CustomProblemGenerationRequest request(int review, int similar, int advanced) {
        return new CustomProblemGenerationRequest(UUID.randomUUID(), 120L, 35L,
                List.of(requestItem(review, similar, advanced)));
    }

    private CustomProblemGenerationItemRequest requestItem(int review, int similar, int advanced) {
        return new CustomProblemGenerationItemRequest(14L, review, similar, advanced);
    }

    private ReissueProposalResponse proposal(boolean withSimilarOrigin, boolean advancedTriggered) {
        ReissueProposalResponse.ReferenceQuestion reference =
                new ReissueProposalResponse.ReferenceQuestion(501L, 1, null);
        return new ReissueProposalResponse(List.of(new ReissueProposalResponse.SubUnitProposal(
                14L,
                "소인수분해",
                new ReissueProposalResponse.Guidance(null, null, null),
                new ReissueProposalResponse.AdaptiveState("mid", "default", null, null, 0, null),
                new ReissueProposalResponse.ReviewProposal(1, 10, List.of(501L)),
                new ReissueProposalResponse.SimilarProposal(
                        withSimilarOrigin ? 5 : 0,
                        withSimilarOrigin ? 10 : 0,
                        "mid",
                        withSimilarOrigin ? List.of(reference) : List.of(),
                        List.of()),
                new ReissueProposalResponse.AdvancedProposal(
                        advancedTriggered, 0, advancedTriggered ? 10 : 0,
                        1, 1, null, null, List.of(), List.of()))));
    }
}
