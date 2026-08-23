package com.cenedu.backend.ai.problem.agent;

import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.guard.GuardDecision;
import com.cenedu.backend.ai.guard.output.OutputGuard;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * PROBLEM_EDIT 구조화 응답의 허용 action과 민감 내용 노출을 검사한다.
 */
@Component
public class ProblemEditOutputGuard implements OutputGuard {
    private final ObjectMapper objectMapper;

    public ProblemEditOutputGuard(ObjectProvider<ObjectMapper> objectMapper) {
        this.objectMapper = objectMapper.getIfAvailable(ObjectMapper::new);
    }

    @Override
    public GuardDecision inspect(AgentRequest request, AgentResponse response) {
        if (request.kind() != AgentKind.PROBLEM_EDIT) return GuardDecision.allow();
        Object value = response.data().get(ProblemEditAgentResultEnvelope.RESPONSE_KEY);
        if (value == null) return GuardDecision.block("PROBLEM_EDIT_RESULT_MISSING", "문제 수정 결과가 없습니다.");
        try {
            ProblemEditConversationResult result = objectMapper.convertValue(value, ProblemEditConversationResult.class);
            if (result.action() == null) return GuardDecision.block("PROBLEM_EDIT_ACTION_INVALID", "수정 action이 없습니다.");
            ProblemEditAgentPayload payload = objectMapper.convertValue(
                request.payload().get(ProblemEditAgent.REQUEST_KEY), ProblemEditAgentPayload.class);
            if (payload.currentSemanticModel() != null) {
                // semanticPatch가 실제로 쓰이는 지점은 REQUEST_CONFIRMATION뿐이다 — 그 결과가
                // PendingProblemEditCommand에 저장되어 나중에 실행된다. CONTINUE_COLLECTION은
                // 아직 확정할 patch가 없는 대화 단계이고, CONFIRM_EXECUTION은 이번 턴이 아니라
                // 이전에 저장된 pending.semanticPatch()를 실행하므로 이번 응답의 semanticPatch를
                // 쓰지 않는다(ProblemEditApplicationService.handleTurn 참고). 두 action까지
                // semanticPatch를 강제하면 모델이 아직 결정할 게 없는 상황에서도 무의미한
                // patch를 만들어내야 해서, 실제로는 그냥 대화 중인 정상 응답을 계속 차단하게 된다.
                boolean patchRequired = result.action() == EditConversationAction.REQUEST_CONFIRMATION;
                ProblemSemanticPatch patch = result.semanticPatch();
                if (patch == null) {
                    if (patchRequired)
                        return GuardDecision.block("PROBLEM_EDIT_SEMANTIC_PATCH_MISSING", "semantic patch가 없습니다.");
                } else {
                    if (!payload.requestId().equals(patch.requestId())
                        || !payload.baseVersionId().equals(patch.baseVersionId())
                        || patch.schemaVersion() != ProblemSemanticPatch.CURRENT_SCHEMA_VERSION)
                        return GuardDecision.block("PROBLEM_EDIT_SEMANTIC_PATCH_BINDING", "semantic patch binding이 올바르지 않습니다.");
                    if ((patch.mode() == SemanticEditMode.STRUCTURAL_REGENERATION
                        || patch.mode() == SemanticEditMode.RESTORE
                        || patch.mode() == SemanticEditMode.REJECTED)
                        && !patch.operations().isEmpty())
                        return GuardDecision.block("PROBLEM_EDIT_SEMANTIC_PATCH_OPERATIONS", "해당 semantic patch mode에는 operation을 포함할 수 없습니다.");
                    if (new ProblemSemanticPatchClassifier().classify(patch) != patch.mode())
                        return GuardDecision.block("PROBLEM_EDIT_SEMANTIC_PATCH_INVALID", "semantic patch가 허용된 분류와 일치하지 않습니다.");
                }
            } else if (result.semanticPatch() != null) {
                return GuardDecision.block("PROBLEM_EDIT_SEMANTIC_PATCH_UNSUPPORTED", "semantic model이 없는 요청에는 semantic patch를 사용할 수 없습니다.");
            }
            String message = ((result.assistantMessage() == null ? "" : result.assistantMessage()) + " "
                + (result.semanticPatch() == null || result.semanticPatch().assistantMessage() == null
                ? "" : result.semanticPatch().assistantMessage())).toLowerCase(Locale.ROOT);
            if (message.contains("system prompt") || message.contains("시스템 프롬프트")
                || message.contains("정답은") || message.contains("<|system|>")
                || message.contains("<|assistant|>")) {
                return GuardDecision.block("PROBLEM_EDIT_OUTPUT_LEAKAGE", "수정 응답에 보호된 내용이 포함됐습니다.");
            }
            return GuardDecision.allow();
        } catch (RuntimeException exception) {
            return GuardDecision.block("PROBLEM_EDIT_RESULT_INVALID", "문제 수정 결과 형식이 올바르지 않습니다.");
        }
    }
}
