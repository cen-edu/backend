package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
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

    public ProblemEditApplicationService(ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository, ProblemAuthoringJsonCodec jsonCodec,
            ProblemEditConversationService conversationService, ProblemEditAgentGateway gateway) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.jsonCodec = jsonCodec;
        this.conversationService = conversationService;
        this.gateway = gateway;
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
        ProblemEditAgentPayload payload = new ProblemEditAgentPayload(1, sessionId, baseVersionId,
                session.getInteractionStatus(), request.selectedTarget(),
                jsonCodec.read(version.getSnapshot(), QuestionSnapshotV1.class), accumulated);
        ProblemEditConversationResult result = gateway.handle(teacherId, request.userInput(),
                request.history() == null ? List.of() : request.history(), payload);
        if (result.action() == EditConversationAction.REQUEST_CONFIRMATION) {
            List<ProblemEditInstruction> merged = new ArrayList<>(accumulated);
            if (result.instructionDeltas() != null) merged.addAll(result.instructionDeltas());
            conversationService.requestConfirmation(teacherId, new PendingProblemEditCommand(
                    UUID.randomUUID(), sessionId, baseVersionId, List.copyOf(merged),
                    null, null, ReplacementSourcePolicy.NONE));
        } else if (result.action() == EditConversationAction.CANCEL) {
            conversationService.cancel(teacherId, sessionId);
        }
        return ProblemEditTurnResponse.from(result);
    }

    private List<ProblemEditInstruction> accumulated(ProblemAuthoringSession session) {
        if (session.getPendingInstructions() == null) return List.of();
        PendingProblemEditCommand pending = jsonCodec.read(session.getPendingInstructions(), PendingProblemEditCommand.class);
        return pending.instructions() == null ? List.of() : pending.instructions();
    }
}
