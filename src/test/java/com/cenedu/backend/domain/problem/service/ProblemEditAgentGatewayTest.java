package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.dispatcher.AgentDispatcher;
import com.cenedu.backend.ai.problem.agent.ProblemEditAgent;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProblemEditAgentGatewayTest {
    @Test
    void gateway는_problem_edit를_dispatcher로만_호출한다() {
        AgentDispatcher dispatcher = mock(AgentDispatcher.class);
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        var gateway = new ProblemEditAgentGateway(dispatcher, provider);
        var payload = new ProblemEditAgentPayload(1, 1L, 2L, AuthoringInteractionStatus.COLLECTING,
                null, ProblemSnapshotFixtures.shortInput(), List.of());
        var result = new ProblemEditConversationResult(EditConversationAction.CONTINUE_COLLECTION, List.of(), "계속 알려 주세요.");
        when(dispatcher.dispatch(any())).thenReturn(AgentResponse.ofData(Map.of(ProblemEditAgentResultEnvelope.RESPONSE_KEY, result)));

        var actual = gateway.handle(7L, "문장을 다듬어 줘", List.of(), payload);

        assertThat(actual).isEqualTo(result);
        verify(dispatcher).dispatch(any(AgentRequest.class));
    }
}
