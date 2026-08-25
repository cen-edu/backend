package com.cenedu.backend.ai.problem.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentPayload;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentResultEnvelope;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditConversationResult;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProblemEditAgentRequestedSpecificationTest {

    @Test
    void 난이도_변경값을_수정_대화_결과에_보존한다() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(anyString(), anyList(), anyString())).thenReturn(new LlmResponse("""
                {"schemaVersion":2,"problemEditResult":{
                  "action":"REQUEST_CONFIRMATION",
                  "instructionDeltas":[{"targetType":"DIFFICULTY","targetKey":null,
                    "changeNature":"STRUCTURAL","instruction":"난이도를 상으로 변경"}],
                  "semanticPatch":null,
                  "requestedSpecification":{"questionType":null,"difficulty":"high"},
                  "assistantMessage":"난이도를 상으로 변경할까요?"}}
                """, 0, 0, 0));
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(mapper);
        ProblemEditAgent agent = new ProblemEditAgent(client, provider, new ProblemEditPromptFactory(provider));
        UUID requestId = UUID.randomUUID();
        var payload = new ProblemEditAgentPayload(ProblemEditAgentPayload.CURRENT_SCHEMA_VERSION,
                requestId, 1L, 2L, AuthoringInteractionStatus.COLLECTING,
                null, snapshot(), null, List.of());

        var response = agent.handle(AgentRequest.of(AgentKind.PROBLEM_EDIT,
                new Actor(7L, Actor.Role.TEACHER), "난이도를 상으로 바꿔줘",
                Map.of(ProblemEditAgent.REQUEST_KEY, payload)));

        var result = (ProblemEditConversationResult) response.data()
                .get(ProblemEditAgentResultEnvelope.RESPONSE_KEY);
        assertThat(result.requestedSpecification().difficulty()).isEqualTo("high");
        assertThat(result.requestedSpecification().questionType()).isNull();
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY,
                        "mid", 10L, null, null, null),
                List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                        "3+4를 계산하시오.", null, null)),
                List.of(), List.of(), List.of(), List.of(), "7이다.", null, List.of());
    }
}
