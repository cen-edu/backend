package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.dto.request.ProblemEditTurnRequest;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.domain.problem.dto.response.ProblemModificationPreviewResponse;
import org.junit.jupiter.api.Test;
import java.util.*;

class ProblemEditApplicationServiceTest {
    @Test
    void preview는_answer_free_parameter_diff만_노출한다() {
        var result = new ProblemModificationExecutionResult(103L, SemanticEditMode.PARAMETRIC_PATCH,
                new ProblemSemanticDiff(List.of(new SemanticValueChange("RADIUS", "3", "5", "cm", "cm")),
                        Set.of(SemanticImpactArea.STEM, SemanticImpactArea.ANSWERS, SemanticImpactArea.EXPLANATION,
                                SemanticImpactArea.ASSETS), false, true), true, false);
        var preview = ProblemModificationPreviewResponse.from(result);
        assertThat(preview.previewVersionId()).isEqualTo(103L);
        assertThat(preview.parameterChanges()).containsExactly(
                new com.cenedu.backend.domain.problem.dto.response.ProblemParameterChangeResponse("RADIUS", "3", "5", "cm", "cm"));
        assertThat(preview.toString()).doesNotContain("answerRaw", "semanticModel", "svg");
    }

    @Test
    void 새로_시작한_수정이_AI_호출에_실패하면_대화를_취소한다() {
        var sessions = mock(ProblemAuthoringSessionRepository.class);
        var versions = mock(ProblemAuthoringVersionRepository.class);
        var codec = mock(ProblemAuthoringJsonCodec.class);
        var conversation = mock(ProblemEditConversationService.class);
        var gateway = mock(ProblemEditAgentGateway.class);
        var coordinator = mock(ProblemModificationExecutionCoordinator.class);
        var session = mock(ProblemAuthoringSession.class);
        var version = mock(ProblemAuthoringVersion.class);
        var snapshot = mock(QuestionSnapshotV1.class);
        when(session.getInteractionStatus()).thenReturn(AuthoringInteractionStatus.IDLE);
        when(session.getCurrentVersionId()).thenReturn(11L);
        when(version.getSnapshot()).thenReturn("snapshot");
        when(sessions.findByIdAndOwnerTeacherId(3L, 7L)).thenReturn(Optional.of(session));
        when(versions.findByIdAndSessionId(11L, 3L)).thenReturn(Optional.of(version));
        when(codec.read("snapshot", QuestionSnapshotV1.class)).thenReturn(snapshot);
        doThrow(new IllegalStateException("AI unavailable")).when(gateway)
                .handle(eq(7L), eq("수정해줘"), any(), any());
        var service = new ProblemEditApplicationService(
                sessions, versions, codec, conversation, gateway, coordinator);

        assertThatThrownBy(() -> service.handleTurn(7L, 3L,
                new ProblemEditTurnRequest("수정해줘", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI unavailable");
        verify(conversation).start(7L, 3L);
        verify(conversation).cancel(7L, 3L);
    }
}
