package com.cenedu.backend.ai.problem.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.guard.GuardDecision;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProblemEditOutputGuardTest {
    @Test
    void semanticPayload에는_server_bound_patch가_필수다() {
        ObjectProvider<ObjectMapper> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(new ObjectMapper());
        var guard = new ProblemEditOutputGuard(provider);
        UUID requestId = UUID.randomUUID();
        var payload = new ProblemEditAgentPayload(2, requestId, 1L, 20L, AuthoringInteractionStatus.COLLECTING,
                null, ProblemSnapshotFixtures.shortInput(), org.mockito.Mockito.mock(com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1.class), List.of());
        var request = AgentRequest.of(AgentKind.PROBLEM_EDIT, new Actor(7L, Actor.Role.TEACHER), "수정", Map.of(ProblemEditAgent.REQUEST_KEY, payload));
        var result = new ProblemEditConversationResult(EditConversationAction.REQUEST_CONFIRMATION, List.of(), null, "확인해 주세요.");

        GuardDecision decision = guard.inspect(request, AgentResponse.ofData(Map.of(ProblemEditAgentResultEnvelope.RESPONSE_KEY, result)));

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("PROBLEM_EDIT_SEMANTIC_PATCH_MISSING");
    }

    @Test
    void semanticPatch의_request와_base가_다르면_차단한다() {
        ObjectProvider<ObjectMapper> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(new ObjectMapper());
        var guard = new ProblemEditOutputGuard(provider);
        var requestId = UUID.randomUUID();
        var payload = new ProblemEditAgentPayload(2, requestId, 1L, 20L, AuthoringInteractionStatus.COLLECTING,
                null, ProblemSnapshotFixtures.shortInput(), org.mockito.Mockito.mock(com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1.class), List.of());
        var patch = new ProblemSemanticPatch(1, UUID.randomUUID(), 99L, SemanticEditMode.PARAMETRIC_PATCH,
                List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE, "/parameters/A/value", "1", "2")), "변경");
        var result = new ProblemEditConversationResult(EditConversationAction.REQUEST_CONFIRMATION, List.of(), patch, "확인해 주세요.");

        GuardDecision decision = guard.inspect(AgentRequest.of(AgentKind.PROBLEM_EDIT, new Actor(7L, Actor.Role.TEACHER), "수정", Map.of(ProblemEditAgent.REQUEST_KEY, payload)),
                AgentResponse.ofData(Map.of(ProblemEditAgentResultEnvelope.RESPONSE_KEY, result)));

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("PROBLEM_EDIT_SEMANTIC_PATCH_BINDING");
    }

    @Test
    void fallback_payload의_semanticPatch는_차단한다() {
        ObjectProvider<ObjectMapper> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(new ObjectMapper());
        var guard = new ProblemEditOutputGuard(provider);
        var payload = new ProblemEditAgentPayload(1, 1L, 2L, AuthoringInteractionStatus.COLLECTING,
                null, ProblemSnapshotFixtures.shortInput(), List.of());
        var patch = new ProblemSemanticPatch(1, UUID.randomUUID(), 2L, SemanticEditMode.RESTORE, List.of(), "확인");
        var result = new ProblemEditConversationResult(EditConversationAction.REQUEST_CONFIRMATION, List.of(), patch, "확인");
        GuardDecision decision = guard.inspect(AgentRequest.of(AgentKind.PROBLEM_EDIT,
                new Actor(7L, Actor.Role.TEACHER), "수정", Map.of(ProblemEditAgent.REQUEST_KEY, payload)),
                AgentResponse.ofData(Map.of(ProblemEditAgentResultEnvelope.RESPONSE_KEY, result)));
        assertThat(decision.reasonCode()).isEqualTo("PROBLEM_EDIT_SEMANTIC_PATCH_UNSUPPORTED");
    }

    @Test
    void semanticPatch의_assistantMessage도_정보노출을_검사한다() {
        ObjectProvider<ObjectMapper> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(new ObjectMapper());
        var guard = new ProblemEditOutputGuard(provider);
        UUID requestId = UUID.randomUUID();
        var payload = new ProblemEditAgentPayload(2, requestId, 1L, 20L, AuthoringInteractionStatus.COLLECTING,
                null, ProblemSnapshotFixtures.shortInput(), org.mockito.Mockito.mock(com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1.class), List.of());
        var patch = new ProblemSemanticPatch(1, requestId, 20L, SemanticEditMode.PARAMETRIC_PATCH,
                List.of(new SemanticPatchOperation(SemanticPatchOperationType.SET_PARAMETER_VALUE, "/parameters/A/value", "1", "2")), "정답은 2입니다");
        var result = new ProblemEditConversationResult(EditConversationAction.REQUEST_CONFIRMATION, List.of(), patch, "확인");
        GuardDecision decision = guard.inspect(AgentRequest.of(AgentKind.PROBLEM_EDIT,
                new Actor(7L, Actor.Role.TEACHER), "수정", Map.of(ProblemEditAgent.REQUEST_KEY, payload)),
                AgentResponse.ofData(Map.of(ProblemEditAgentResultEnvelope.RESPONSE_KEY, result)));
        assertThat(decision.reasonCode()).isEqualTo("PROBLEM_EDIT_OUTPUT_LEAKAGE");
    }
}
