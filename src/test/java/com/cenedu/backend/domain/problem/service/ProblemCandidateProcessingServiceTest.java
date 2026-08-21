package com.cenedu.backend.domain.problem.service;

import static com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures.shortInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingRequest;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.port.ProblemAssetProductionPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemVerificationPort;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.authoring.verification.GenerationVerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOperationType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class ProblemCandidateProcessingServiceTest {

    private ProblemAuthoringSessionRepository sessionRepository;
    private ProblemAuthoringVersionRepository versionRepository;
    private ProblemVerificationPort verificationPort;
    private ProblemAuthoringSession session;
    private AtomicReference<ProblemAuthoringVersion> savedVersion;
    private ProblemCandidateProcessingService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionRepository = mock(ProblemAuthoringSessionRepository.class);
        versionRepository = mock(ProblemAuthoringVersionRepository.class);
        verificationPort = mock(ProblemVerificationPort.class);
        ObjectProvider<ProblemVerificationPort> verificationProvider = mock(ObjectProvider.class);
        ObjectProvider<ProblemAssetProductionPort> assetProvider = mock(ObjectProvider.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(verificationProvider.getIfAvailable()).thenReturn(verificationPort);

        session = ProblemAuthoringSession.createGenerating(7L);
        ReflectionTestUtils.setField(session, "id", 31L);
        savedVersion = new AtomicReference<>();
        when(sessionRepository.findOwnedByIdForUpdate(31L, 7L))
                .thenReturn(Optional.of(session));
        when(versionRepository.findFirstBySessionIdOrderByVersionNoDesc(31L))
                .thenReturn(Optional.empty());
        when(versionRepository.saveAndFlush(any(ProblemAuthoringVersion.class)))
                .thenAnswer(invocation -> {
                    ProblemAuthoringVersion version = invocation.getArgument(0);
                    ReflectionTestUtils.setField(version, "id", 101L);
                    savedVersion.set(version);
                    return version;
                });
        when(versionRepository.findById(101L))
                .thenAnswer(invocation -> Optional.ofNullable(savedVersion.get()));
        when(versionRepository.findByIdAndSessionId(101L, 31L))
                .thenAnswer(invocation -> Optional.ofNullable(savedVersion.get()));

        SnapshotStructuralValidator structural = new SnapshotStructuralValidator();
        service = new ProblemCandidateProcessingService(
                sessionRepository,
                versionRepository,
                structural,
                new SnapshotNormalizedValidator(structural),
                new ProblemAuthoringJsonCodec(new ObjectMapper()),
                verificationProvider,
                assetProvider,
                transactionManager,
                new ProblemAiConcurrencyLimiter(4, 30));
    }

    @Test
    @DisplayName("CONTENT PASSED 후보만 current Version으로 승격한다")
    void promotesPassedCandidate() {
        when(verificationPort.verify(any())).thenAnswer(invocation -> {
            var request = (com.cenedu.backend.domain.problem.authoring.verification
                    .ProblemVerificationRequest) invocation.getArgument(0);
            return new ProblemVerificationReport(
                    request.verificationRequestId(), request.scope(),
                    VerificationOverallStatus.PASSED, List.of());
        });

        CandidateProcessingResult result = service.process(request());

        assertThat(result.promoted()).isTrue();
        assertThat(session.getCurrentVersionId()).isEqualTo(101L);
        assertThat(session.getPendingVersionId()).isNull();
        assertThat(session.getOperationStatus()).isEqualTo(AuthoringOperationStatus.IDLE);
    }

    @Test
    @DisplayName("FAILED 후보는 Version으로 남지만 current를 변경하지 않는다")
    void preservesFailedCandidateWithoutPromotion() {
        when(verificationPort.verify(any())).thenAnswer(invocation -> {
            var request = (com.cenedu.backend.domain.problem.authoring.verification
                    .ProblemVerificationRequest) invocation.getArgument(0);
            return new ProblemVerificationReport(
                    request.verificationRequestId(), request.scope(),
                    VerificationOverallStatus.FAILED, List.of());
        });

        CandidateProcessingResult result = service.process(request());

        assertThat(result.promoted()).isFalse();
        assertThat(session.getCurrentVersionId()).isNull();
        assertThat(session.getPendingVersionId()).isNull();
        assertThat(session.getOperationStatus()).isEqualTo(AuthoringOperationStatus.FAILED);
    }

    @Test
    @DisplayName("검증 처리 ERROR는 같은 후보를 다시 검증하지 않는다")
    void doesNotRepeatVerificationAfterProcessingError() {
        when(verificationPort.verify(any())).thenAnswer(invocation -> {
            var request = (com.cenedu.backend.domain.problem.authoring.verification
                    .ProblemVerificationRequest) invocation.getArgument(0);
            return new ProblemVerificationReport(
                    request.verificationRequestId(), request.scope(),
                    VerificationOverallStatus.ERROR, List.of());
        });

        CandidateProcessingResult result = service.process(request());

        assertThat(result.status()).isEqualTo(VerificationOverallStatus.ERROR);
        verify(verificationPort).verify(any());
    }

    @Test
    @DisplayName("재시도 가능한 검증 오류만 같은 후보에서 한 번 재검증한다")
    void retriesRetryableVerificationErrorOnce() {
        when(verificationPort.verify(any())).thenAnswer(new org.mockito.stubbing.Answer<ProblemVerificationReport>() {
            private int calls;

            @Override
            public ProblemVerificationReport answer(org.mockito.invocation.InvocationOnMock invocation) {
                var request = (com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationRequest)
                        invocation.getArgument(0);
                calls++;
                if (calls == 1) {
                    return new ProblemVerificationReport(request.verificationRequestId(), request.scope(),
                            VerificationOverallStatus.ERROR,
                            List.of(new VerificationFinding(
                                    VerificationScope.CONTENT == request.scope()
                                            ? com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType.CORRECTNESS
                                            : com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType.ASSET_CONSISTENCY,
                                    VerificationFindingStatus.ERROR, VerificationSeverity.ERROR,
                                    VerificationIssueCode.PROVIDER_ERROR, "검증 응답이 요구한 형식이 아닙니다.", null)));
                }
                return new ProblemVerificationReport(request.verificationRequestId(), request.scope(),
                        VerificationOverallStatus.PASSED, List.of());
            }
        });

        CandidateProcessingResult result = service.process(request());

        assertThat(result.promoted()).isTrue();
        verify(verificationPort, org.mockito.Mockito.times(2)).verify(any());
    }

    @Test
    @DisplayName("semantic deterministic 실패는 저장과 독립 검증을 모두 건너뛴다")
    void rejectsSemanticCandidateBeforeRegistration() {
        var semanticCandidate = new ProblemCandidateDraft(
                UUID.randomUUID(), shortInput(), List.of(), mock(ProblemSemanticModelV1.class),
                new CandidateProvenance(CandidateSourceType.AI_GENERATE, null, List.of()));
        var request = new CandidateProcessingRequest(
                7L, 31L, null, AuthoringOperationType.AI_GENERATE,
                VerificationOperationType.CREATE, semanticCandidate,
                new VerificationExpectation(shortInput().metadata().questionType(), "mid",
                        new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                                "수와 연산", "사칙연산", "덧셈"), null, List.of(), List.of()),
                new GenerationVerificationContext(GenerationPurpose.GENERAL_LEARNING_SHORTAGE, List.of()),
                "semantic 실패");

        assertThatThrownBy(() -> service.process(request)).isInstanceOf(RuntimeException.class);
        verify(versionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(verificationPort);
    }

    private CandidateProcessingRequest request() {
        UUID requestId = UUID.randomUUID();
        ProblemCandidateDraft candidate = new ProblemCandidateDraft(
                requestId,
                shortInput(),
                List.of(),
                new CandidateProvenance(
                        CandidateSourceType.AI_GENERATE, null, List.of()));
        CurriculumScope curriculum = new CurriculumScope(
                "2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                "수와 연산", "사칙연산", "덧셈");
        return new CandidateProcessingRequest(
                7L, 31L, null,
                AuthoringOperationType.AI_GENERATE,
                VerificationOperationType.CREATE,
                candidate,
                new VerificationExpectation(
                        shortInput().metadata().questionType(), "mid", curriculum,
                        null, List.of(), List.of()),
                new GenerationVerificationContext(
                        GenerationPurpose.GENERAL_LEARNING_SHORTAGE, List.of()),
                "생성 후보");
    }
}
