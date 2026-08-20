package com.cenedu.backend.domain.problem.service;

import static com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures.shortInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationDiagnosticEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationEvaluationAreaEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.PersonalizedGenerationEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationWorkItem;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;

class ProblemGenerationWorkerTest {

    private ProblemGenerationJobService jobService;
    private ProblemCandidateProcessingService candidateService;
    private ProblemGenerationPort generationPort;
    private ProblemGenerationWorker worker;
    private ProblemGenerationWorkItem workItem;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jobService = mock(ProblemGenerationJobService.class);
        candidateService = mock(ProblemCandidateProcessingService.class);
        generationPort = mock(ProblemGenerationPort.class);
        ObjectProvider<ProblemGenerationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(generationPort);
        worker = new ProblemGenerationWorker(jobService, candidateService, provider,
                new ProblemAiConcurrencyLimiter(4, 30));
        Logger workerLogger = (Logger) LoggerFactory.getLogger(ProblemGenerationWorker.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        workerLogger.addAppender(logAppender);
        PersonalizedGenerationEvidence evidence = new PersonalizedGenerationEvidence(
                8, 3,
                List.of(new GenerationEvaluationAreaEvidence(
                        EvaluationArea.CALCULATION, 6, 4, BigDecimal.valueOf(66.67))),
                List.of(new GenerationDiagnosticEvidence(
                        DiagnosticType.EXECUTE, 8, 5, BigDecimal.valueOf(62.5))));
        ProblemGenerationCommand command = new ProblemGenerationCommand(
                UUID.randomUUID(),
                null,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(
                        QuestionType.SHORT_INPUT, "mid", null, List.of()),
                new CurriculumScope(
                        "2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"),
                List.of(), List.of(), evidence);
        workItem = new ProblemGenerationWorkItem(1L, 2L, 7L, 3L, command);
        when(jobService.tryClaim(1L)).thenReturn(Optional.of(workItem));
    }

    @AfterEach
    void detachLogAppender() {
        Logger workerLogger = (Logger) LoggerFactory.getLogger(ProblemGenerationWorker.class);
        workerLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    @DisplayName("검증 실패를 2회 재생성한 뒤 성공하면 Item을 성공으로 종료한다")
    void retriesTwiceThenSucceeds() {
        when(generationPort.generate(any())).thenAnswer(invocation ->
                candidate(invocation.getArgument(0, ProblemGenerationCommand.class).requestId()));
        when(candidateService.process(any()))
                .thenReturn(result(VerificationOverallStatus.FAILED, false))
                .thenReturn(result(VerificationOverallStatus.FAILED, false))
                .thenReturn(result(VerificationOverallStatus.PASSED, true));
        when(jobService.prepareRetry(any(), any()))
                .thenReturn(true, true);

        worker.execute(1L);

        verify(generationPort, times(3)).generate(any());
        verify(jobService).succeed(workItem);
        ArgumentCaptor<ProblemGenerationCommand> commands = ArgumentCaptor.forClass(ProblemGenerationCommand.class);
        verify(generationPort, times(3)).generate(commands.capture());
        assertThat(commands.getAllValues().get(1).requestId()).isEqualTo(UUID.nameUUIDFromBytes(
                (workItem.command().requestId() + ":attempt:1").getBytes(StandardCharsets.UTF_8)));
        assertThat(commands.getAllValues().get(2).requestId()).isEqualTo(UUID.nameUUIDFromBytes(
                (workItem.command().requestId() + ":attempt:2").getBytes(StandardCharsets.UTF_8)));
        assertThat(commands.getAllValues())
                .extracting(ProblemGenerationCommand::personalizedEvidence)
                .containsOnly(workItem.command().personalizedEvidence());
        assertThat(logMessages()).anyMatch(message -> message.contains("stage=GENERATION")
                && message.contains("outcome=SUCCESS")
                && message.contains("jobId=2")
                && message.contains("itemId=1")
                && message.contains("sessionId=3")
                && message.contains("operationId=" + workItem.command().requestId())
                && message.contains("purpose=GENERAL_LEARNING_SHORTAGE")
                && message.contains("candidateAttempt=1")
                && message.contains("elapsedMs="));
        assertThat(logMessages()).anyMatch(message -> message.contains("stage=PROMOTION")
                && message.contains("outcome=SUCCESS"));
    }

    @Test
    @DisplayName("생성 재시도를 소진하면 Item을 실패로 종료한다")
    void failsAfterRetryExhaustion() {
        when(generationPort.generate(any()))
                .thenThrow(new IllegalStateException("provider"));
        when(jobService.prepareRetry(any(), any()))
                .thenReturn(true, true, false);

        worker.execute(1L);

        verify(generationPort, times(3)).generate(any());
        verify(jobService).fail(workItem, "GENERATION_FAILED");
        assertThat(logMessages()).anyMatch(message -> message.contains("stage=GENERATION")
                && message.contains("outcome=ERROR")
                && message.contains("candidateAttempt=3")
                && message.contains("errorType=IllegalStateException"));
    }

    @Test
    @DisplayName("검증 기술 오류는 새 후보를 생성하지 않고 Item을 실패로 종료한다")
    void verificationErrorDoesNotRegenerate() {
        when(generationPort.generate(any())).thenAnswer(invocation ->
                candidate(invocation.getArgument(0, ProblemGenerationCommand.class).requestId()));
        when(candidateService.process(any()))
                .thenReturn(result(VerificationOverallStatus.ERROR, false));

        worker.execute(1L);

        verify(generationPort, times(1)).generate(any());
        verify(jobService).fail(workItem, "VERIFICATION_ERROR");
        assertThat(logMessages()).anyMatch(message -> message.contains("event=problem_authoring_item")
                && message.contains("stage=END")
                && message.contains("outcome=FAILED")
                && message.contains("reason=VERIFICATION_ERROR"));
    }

    @Test
    @DisplayName("멱등 재요청으로 이미 선점된 Item은 두 번 생성하지 않는다")
    void ignoresAlreadyClaimedItem() {
        when(jobService.tryClaim(1L)).thenReturn(Optional.empty());

        worker.execute(1L);

        verifyNoInteractions(generationPort, candidateService);
    }

    private ProblemCandidateDraft candidate(UUID requestId) {
        return new ProblemCandidateDraft(
                requestId, shortInput(), List.of(),
                new CandidateProvenance(
                        CandidateSourceType.AI_GENERATE, null, List.of()));
    }

    private CandidateProcessingResult result(
            VerificationOverallStatus status,
            boolean promoted
    ) {
        return new CandidateProcessingResult(
                10L, 1, UUID.randomUUID(), status, null, promoted);
    }

    private List<String> logMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
