package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.dto.request.ProblemEditTurnRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemEditTurnResponse;
import com.cenedu.backend.domain.problem.entity.*;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Service;

/** 소유권·current Version을 확인한 뒤 사용자 수정 입력을 Dispatcher 경로로 보낸다. */
@Service
public class ProblemEditApplicationService {
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ProblemEditConversationService conversationService;
    private final ProblemEditAgentGateway gateway;
    private final ProblemModificationExecutionCoordinator executionCoordinator;

    public ProblemEditApplicationService(ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository, ProblemAuthoringJsonCodec jsonCodec,
            ProblemEditConversationService conversationService, ProblemEditAgentGateway gateway,
            ProblemModificationExecutionCoordinator executionCoordinator) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.jsonCodec = jsonCodec;
        this.conversationService = conversationService;
        this.gateway = gateway;
        this.executionCoordinator = executionCoordinator;
    }

    /** 수정 요청을 해석하고 확인 요청일 때만 구조화 명령을 Session에 저장한다. */
    public ProblemEditTurnResponse handleTurn(long teacherId, long sessionId, ProblemEditTurnRequest request) {
        ProblemAuthoringSession session = sessionRepository.findByIdAndOwnerTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        Long baseVersionId = session.getCurrentVersionId();
        if (baseVersionId == null) throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        ProblemAuthoringVersion version = versionRepository.findByIdAndSessionId(baseVersionId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        if (session.getInteractionStatus() == AuthoringInteractionStatus.IDLE) {
            conversationService.start(teacherId, sessionId);
            session = sessionRepository.findByIdAndOwnerTeacherId(sessionId, teacherId).orElseThrow();
        }
        List<ProblemEditInstruction> accumulated = accumulated(session);
        QuestionSnapshotV1 baseSnapshot = jsonCodec.read(version.getSnapshot(), QuestionSnapshotV1.class);
        ProblemSemanticModelV1 semanticModel = version.getSemanticModel() == null ? null
                : jsonCodec.read(version.getSemanticModel(), ProblemSemanticModelV1.class);
        ProblemEditAgentPayload payload = new ProblemEditAgentPayload(2, UUID.randomUUID(), sessionId, baseVersionId,
                session.getInteractionStatus(), request.selectedTarget(),
                baseSnapshot, semanticModel, accumulated);
        ProblemEditConversationResult result = gateway.handle(teacherId, request.userInput(),
                request.history() == null ? List.of() : request.history(), payload);
        if (result.action() == EditConversationAction.REQUEST_CONFIRMATION) {
            List<ProblemEditInstruction> merged = new ArrayList<>(accumulated);
            if (result.instructionDeltas() != null) merged.addAll(result.instructionDeltas());
            conversationService.requestConfirmation(teacherId, new PendingProblemEditCommand(
                    result.semanticPatch() == null ? UUID.randomUUID() : result.semanticPatch().requestId(),
                    sessionId, baseVersionId, List.copyOf(merged),
                    result.semanticPatch(),
                    null, null, ReplacementSourcePolicy.NONE));
        } else if (result.action() == EditConversationAction.CANCEL) {
            conversationService.cancel(teacherId, sessionId);
        } else if (result.action() == EditConversationAction.CONFIRM_EXECUTION) {
            if (session.getPendingInstructions() == null) {
                throw new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE);
            }
            PendingProblemEditCommand pending = jsonCodec.read(
                    session.getPendingInstructions(), PendingProblemEditCommand.class);
            ProblemEditExecutionPlan plan = conversationService.confirm(teacherId,
                    new ConfirmedProblemEditCommand(pending.requestId(), UUID.randomUUID(),
                            pending.sessionId(), pending.baseVersionId(), pending.instructions(),
                            pending.semanticPatch(),
                            pending.requestedSpecification(), pending.restoreReference(),
                            pending.replacementSourcePolicy()));
            if (plan.action() != EditAction.RESTORE) {
                executionCoordinator.execute(teacherId, plan, baseSnapshot);
            }
        }
        return ProblemEditTurnResponse.from(result);
    }

    private List<ProblemEditInstruction> accumulated(ProblemAuthoringSession session) {
        if (session.getPendingInstructions() == null) return List.of();
        PendingProblemEditCommand pending = jsonCodec.read(session.getPendingInstructions(), PendingProblemEditCommand.class);
        return pending.instructions() == null ? List.of() : pending.instructions();
    }
}
