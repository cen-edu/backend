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
    void 요청_스펙을_JSON으로_직렬화해도_계산용_empty_필드가_생기지_않는다() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                new com.cenedu.backend.domain.problem.authoring.edit.RequestedProblemSpecification(
                        null, "low", null, false, false));

        assertThat(json).contains("\"difficulty\":\"low\"").doesNotContain("\"empty\"");
    }

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

    @Test
    void 자료_유무_조건을_교체_스펙으로_보존한다() {
        var result = handle("""
                {"schemaVersion":2,"problemEditResult":{
                  "action":"REQUEST_CONFIRMATION",
                  "instructionDeltas":[],
                  "semanticPatch":null,
                  "requestedSpecification":{"questionType":null,"difficulty":null,
                    "requiresAsset":true,"differentProblemOnly":false},
                  "assistantMessage":"이미지가 있는 문제로 바꿀까요?"}}
                """, "이미지가 있는 문제로 바꿔줘");

        assertThat(result.requestedSpecification()).isNotNull();
        assertThat(result.requestedSpecification().requiresAsset()).isTrue();
    }

    /**
     * 바꿀 조건이 하나도 없는 "다른 문제로" 요청이 교체 요청으로 살아남는지 확인한다.
     *
     * <p>differentProblemOnly가 isEmpty 판정에 들어가지 않으면 이 스펙이 빈 스펙으로 취급돼
     * null로 정규화되고, 교체 요청 자체가 조용히 사라진다.
     */
    @Test
    void 같은_조건_다른_문제_요청은_교체_요청으로_남는다() {
        var result = handle("""
                {"schemaVersion":2,"problemEditResult":{
                  "action":"REQUEST_CONFIRMATION",
                  "instructionDeltas":[],
                  "semanticPatch":null,
                  "requestedSpecification":{"questionType":null,"difficulty":null,
                    "requiresAsset":null,"differentProblemOnly":true},
                  "assistantMessage":"다른 문제로 바꿀까요?"}}
                """, "이 문제 말고 다른 걸로 줘");

        assertThat(result.requestedSpecification()).isNotNull();
        assertThat(result.requestedSpecification().differentProblemOnly()).isTrue();
    }

    private ProblemEditConversationResult handle(String response, String userInput) {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(anyString(), anyList(), anyString()))
                .thenReturn(new LlmResponse(response, 0, 0, 0));
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(mapper);
        ProblemEditAgent agent = new ProblemEditAgent(client, provider, new ProblemEditPromptFactory(provider));
        var payload = new ProblemEditAgentPayload(ProblemEditAgentPayload.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(), 1L, 2L, AuthoringInteractionStatus.COLLECTING,
                null, snapshot(), null, List.of());

        return (ProblemEditConversationResult) agent.handle(AgentRequest.of(AgentKind.PROBLEM_EDIT,
                        new Actor(7L, Actor.Role.TEACHER), userInput,
                        Map.of(ProblemEditAgent.REQUEST_KEY, payload)))
                .data().get(ProblemEditAgentResultEnvelope.RESPONSE_KEY);
    }

    @Test
    void 바꿀_값이_없는_빈_스펙은_요청_없음으로_정규화한다() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(anyString(), anyList(), anyString())).thenReturn(new LlmResponse("""
                {"schemaVersion":2,"problemEditResult":{
                  "action":"REQUEST_CONFIRMATION",
                  "instructionDeltas":[{"targetType":"EXPLANATION","targetKey":null,
                    "changeNature":"PRESENTATIONAL","instruction":"해설을 더 짧게"}],
                  "semanticPatch":null,
                  "requestedSpecification":{"questionType":null,"difficulty":null},
                  "assistantMessage":"해설을 줄일까요?"}}
                """, 0, 0, 0));
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(mapper);
        ProblemEditAgent agent = new ProblemEditAgent(client, provider, new ProblemEditPromptFactory(provider));
        var payload = new ProblemEditAgentPayload(ProblemEditAgentPayload.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(), 1L, 2L, AuthoringInteractionStatus.COLLECTING,
                null, snapshot(), null, List.of());

        var response = agent.handle(AgentRequest.of(AgentKind.PROBLEM_EDIT,
                new Actor(7L, Actor.Role.TEACHER), "해설을 더 짧게 해줘",
                Map.of(ProblemEditAgent.REQUEST_KEY, payload)));

        var result = (ProblemEditConversationResult) response.data()
                .get(ProblemEditAgentResultEnvelope.RESPONSE_KEY);
        assertThat(result.requestedSpecification()).isNull();
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
