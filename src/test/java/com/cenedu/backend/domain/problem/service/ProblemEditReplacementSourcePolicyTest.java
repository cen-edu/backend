package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cenedu.backend.domain.problem.authoring.edit.EditConversationAction;
import com.cenedu.backend.domain.problem.authoring.edit.PendingProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentPayload;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditConversationResult;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.edit.RequestedProblemSpecification;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.dto.request.ProblemEditTurnRequest;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * 교체 요청이 문제은행 조회 경로로 배선되는지 고정한다.
 *
 * <p>이 값이 GENERATE_ONLY로 돌아가면 {@code ReplacementSourcePolicy.BANK_FIRST}가 코드
 * 어디에서도 만들어지지 않아 {@code ProblemModificationExecutionCoordinator}의 문제은행 조회
 * 교체가 통째로 죽은 분기가 된다 — 컴파일도 테스트도 통과하면서 기능만 사라지는 종류의 회귀라
 * 여기서 명시적으로 막는다.
 */
class ProblemEditReplacementSourcePolicyTest {

    @Test
    void 교체_요청은_문제은행_조회를_먼저_시도하도록_배선한다() {
        var conversationService = mock(ProblemEditConversationService.class);
        var service = service(conversationService,
                new RequestedProblemSpecification(null, "low", null, false));

        service.handleTurn(7L, 1L, new ProblemEditTurnRequest("난이도 하나 낮춰줘", List.of(), null));

        assertThat(capturedCommand(conversationService).replacementSourcePolicy())
                .isEqualTo(ReplacementSourcePolicy.BANK_FIRST);
    }

    /**
     * 교사가 새로 만들어 달라고 명시하면 문제은행을 건너뛰는지 확인한다.
     *
     * <p>이 구분이 없으면 "새로 만들어줘"도 이미 적재된 문항으로 조용히 대체되어, 교사가 요청한
     * 것과 다른 결과가 요청대로 처리된 것처럼 돌아온다.
     */
    @Test
    void 새로_만들어_달라는_요청은_문제은행을_건너뛴다() {
        var conversationService = mock(ProblemEditConversationService.class);
        var service = service(conversationService,
                new RequestedProblemSpecification(null, null, null, false, true));

        service.handleTurn(7L, 1L, new ProblemEditTurnRequest("새로 만들어줘", List.of(), null));

        assertThat(capturedCommand(conversationService).replacementSourcePolicy())
                .isEqualTo(ReplacementSourcePolicy.GENERATE_ONLY);
    }

    @Test
    void 교체_요청이_아니면_소스_정책을_두지_않는다() {
        var conversationService = mock(ProblemEditConversationService.class);
        var service = service(conversationService, null);

        service.handleTurn(7L, 1L, new ProblemEditTurnRequest("해설을 더 짧게 해줘", List.of(), null));

        assertThat(capturedCommand(conversationService).replacementSourcePolicy())
                .isEqualTo(ReplacementSourcePolicy.NONE);
    }

    private PendingProblemEditCommand capturedCommand(ProblemEditConversationService service) {
        ArgumentCaptor<PendingProblemEditCommand> captor =
                ArgumentCaptor.forClass(PendingProblemEditCommand.class);
        verify(service).requestConfirmation(anyLong(), captor.capture());
        return captor.getValue();
    }

    private ProblemEditApplicationService service(ProblemEditConversationService conversationService,
                                                   RequestedProblemSpecification specification) {
        var jsonCodec = new ProblemAuthoringJsonCodec(new tools.jackson.databind.ObjectMapper());
        var session = mock(ProblemAuthoringSession.class);
        when(session.getCurrentVersionId()).thenReturn(2L);
        when(session.getInteractionStatus()).thenReturn(AuthoringInteractionStatus.COLLECTING);
        when(session.getPendingInstructions()).thenReturn(null);
        var version = mock(ProblemAuthoringVersion.class);
        when(version.getSnapshot()).thenReturn(jsonCodec.write(snapshot()));
        when(version.getSemanticModel()).thenReturn(null);

        var sessionRepository = mock(ProblemAuthoringSessionRepository.class);
        when(sessionRepository.findByIdAndOwnerTeacherId(anyLong(), anyLong()))
                .thenReturn(Optional.of(session));
        var versionRepository = mock(ProblemAuthoringVersionRepository.class);
        when(versionRepository.findByIdAndSessionId(anyLong(), anyLong()))
                .thenReturn(Optional.of(version));

        var gateway = mock(ProblemEditAgentGateway.class);
        when(gateway.handle(anyLong(), anyString(), anyList(), any(ProblemEditAgentPayload.class)))
                .thenReturn(new ProblemEditConversationResult(
                        EditConversationAction.REQUEST_CONFIRMATION, List.of(), null,
                        specification, "이렇게 바꿀까요?"));

        return new ProblemEditApplicationService(sessionRepository, versionRepository, jsonCodec,
                conversationService, gateway, mock(ProblemModificationExecutionCoordinator.class));
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
