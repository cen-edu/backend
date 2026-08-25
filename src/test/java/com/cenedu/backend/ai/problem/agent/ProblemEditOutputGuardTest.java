package com.cenedu.backend.ai.problem.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 정상적인 수정 응답이 출력 guard에 막히지 않는지 고정한다.
 *
 * <p>guard는 예외가 나면 이유를 남기지 않고 PROBLEM_EDIT_RESULT_INVALID로 차단해 버린다.
 * 그래서 여기서 막히면 교사에게는 "결과 형식이 올바르지 않습니다"만 보이고 어떤 수정 요청도
 * 반영되지 않는다.
 */
class ProblemEditOutputGuardTest {

    private final ProblemEditOutputGuard guard = new ProblemEditOutputGuard(provider());

    @Test
    void semantic_model이_없는_문항의_난이도_변경_응답을_통과시킨다() {
        var result = new ProblemEditConversationResult(
                EditConversationAction.REQUEST_CONFIRMATION,
                List.of(new ProblemEditInstruction(EditTargetType.DIFFICULTY, null,
                        EditChangeNature.STRUCTURAL, "난이도를 하로 낮춘다")),
                null,
                new RequestedProblemSpecification(null, "low", null, false, false),
                "난이도를 낮출까요?");

        var decision = guard.inspect(request(), AgentResponse.ofData(
                Map.of(ProblemEditAgentResultEnvelope.RESPONSE_KEY, result)));

        assertThat(decision.blocked()).as("차단 사유=%s", decision.reasonCode()).isFalse();
    }

    @Test
    void 교체_조건이_없는_표현_수정_응답도_통과시킨다() {
        var result = new ProblemEditConversationResult(
                EditConversationAction.REQUEST_CONFIRMATION,
                List.of(new ProblemEditInstruction(EditTargetType.EXPLANATION, null,
                        EditChangeNature.PRESENTATIONAL, "해설을 더 짧게")),
                null, null, "해설을 줄일까요?");

        var decision = guard.inspect(request(), AgentResponse.ofData(
                Map.of(ProblemEditAgentResultEnvelope.RESPONSE_KEY, result)));

        assertThat(decision.blocked()).as("차단 사유=%s", decision.reasonCode()).isFalse();
    }

    private AgentRequest request() {
        var payload = new ProblemEditAgentPayload(ProblemEditAgentPayload.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(), 1L, 2L, AuthoringInteractionStatus.COLLECTING,
                null, snapshot(), null, List.of());
        return AgentRequest.of(AgentKind.PROBLEM_EDIT, new Actor(1L, Actor.Role.TEACHER),
                "난이도를 낮춰줘", Map.of(ProblemEditAgent.REQUEST_KEY, payload));
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.STEP_FILL, QuestionPresentation.TEXT_ONLY,
                        "mid", 10L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "빈칸을 채우시오.", null, null)),
                List.of(), List.of(), List.of(), List.of(), "해설", null, List.of());
    }

    private ObjectProvider<ObjectMapper> provider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        return provider;
    }
}
