package com.cenedu.backend.ai.problem.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.client.*;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProblemEditAgentTest {
    @Test
    @SuppressWarnings("unchecked")
    void returnsStructuredProblemEditResult() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("""
                {"schemaVersion":1,"problemEditResult":{"action":"CONTINUE_COLLECTION",
                "instructionDeltas":[{"targetType":"QUESTION_BODY","targetKey":null,
                "changeNature":"SEMANTIC","instruction":"문장을 간결하게 바꾼다"}],
                "assistantMessage":"추가 수정 사항이 있나요?"}}
                """, 1, 1, 0));
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        ProblemEditAgent agent = new ProblemEditAgent(client, provider, new ProblemEditPromptFactory());
        ProblemEditAgentPayload payload = new ProblemEditAgentPayload(1, 1L, 2L,
                AuthoringInteractionStatus.COLLECTING, null, ProblemSnapshotFixtures.shortInput(), List.of());

        AgentResponse response = agent.handle(new AgentRequest(AgentKind.PROBLEM_EDIT,
                new Actor(7L, Actor.Role.TEACHER), "문장을 간결하게", List.of(),
                Map.of(ProblemEditAgent.REQUEST_KEY, payload)));

        assertNotNull(response.data().get(ProblemEditAgentResultEnvelope.RESPONSE_KEY));
        verify(client).completeStructured(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectedTargetOverridesModelInventedTargetKey() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("""
                {"schemaVersion":1,"problemEditResult":{"action":"REQUEST_CONFIRMATION",
                "instructionDeltas":[{"targetType":"QUESTION_BODY","targetKey":"S1",
                "changeNature":"PRESENTATIONAL","instruction":"발문을 다듬는다"}],
                "assistantMessage":"확인해 주세요."}}
                """, 1, 1, 0));
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        ProblemEditAgent agent = new ProblemEditAgent(
                client, provider, new ProblemEditPromptFactory());
        ProblemEditAgentPayload payload = new ProblemEditAgentPayload(1, 1L, 2L,
                AuthoringInteractionStatus.COLLECTING,
                new ProblemEditTargetRef(EditTargetType.QUESTION_BODY, null),
                ProblemSnapshotFixtures.shortInput(), List.of());

        AgentResponse response = agent.handle(new AgentRequest(AgentKind.PROBLEM_EDIT,
                new Actor(7L, Actor.Role.TEACHER), "발문을 다듬어 줘", List.of(),
                Map.of(ProblemEditAgent.REQUEST_KEY, payload)));

        var result = (ProblemEditConversationResult) response.data()
                .get(ProblemEditAgentResultEnvelope.RESPONSE_KEY);
        assertEquals(EditTargetType.QUESTION_BODY,
                result.instructionDeltas().getFirst().targetType());
        assertNull(result.instructionDeltas().getFirst().targetKey());
    }
}
