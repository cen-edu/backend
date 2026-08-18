package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemVerificationPort;
import com.cenedu.backend.domain.problem.authoring.verification.EditVerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationRequest;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ProblemVerificationPort} 구현. 생성·수정된 문제 후보를 독립적으로 검증한다.
 *
 * <p><b>저장·재시도·승격을 반환하지 않는다.</b> 전부 {@code ProblemCandidateProcessingService} 의
 * 책임이다. 여기서 "재시도하세요"를 돌려주기 시작하면 재시도 정책이 두 곳에 생긴다.
 *
 * <p>{@code verificationRequestId} 를 그대로 되돌려준다. 멱등성 전제다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다. LLM 호출은 수 초에서 수십 초가 걸리고, 그 시간 동안
 * 커넥션을 잡고 있으면 풀이 마른다. 이 Adapter 는 Repository 를 직접 조회하지도 않는다.
 *
 * <p>판정 항목마다 예외를 격리한다. 하나가 던져도 나머지 Finding 은 나와야 한다 — 한 항목의 사고로
 * 보고서 전체가 사라지면 조율측은 무엇이 통과했는지도 알 수 없다.
 */
@Component
public class ProblemVerificationAdapter implements ProblemVerificationPort {

    private static final Logger log = LoggerFactory.getLogger(ProblemVerificationAdapter.class);

    private final BlindQuestionFactory blindQuestionFactory;
    private final VerificationLlmClient llmClient;
    private final CorrectnessChecker correctnessChecker;
    private final StructuralConsistencyCheck structuralConsistencyCheck;
    private final ExpectationChecks expectationChecks;
    private final EditScopeChecks editScopeChecks;
    private final ContentIntegrityChecker contentIntegrityChecker;
    private final AssetChecks assetChecks;
    private final FindingSanitizer findingSanitizer;

    public ProblemVerificationAdapter(
            BlindQuestionFactory blindQuestionFactory,
            VerificationLlmClient llmClient,
            CorrectnessChecker correctnessChecker,
            StructuralConsistencyCheck structuralConsistencyCheck,
            ExpectationChecks expectationChecks,
            EditScopeChecks editScopeChecks,
            ContentIntegrityChecker contentIntegrityChecker,
            AssetChecks assetChecks,
            FindingSanitizer findingSanitizer
    ) {
        this.blindQuestionFactory = blindQuestionFactory;
        this.llmClient = llmClient;
        this.correctnessChecker = correctnessChecker;
        this.structuralConsistencyCheck = structuralConsistencyCheck;
        this.expectationChecks = expectationChecks;
        this.editScopeChecks = editScopeChecks;
        this.contentIntegrityChecker = contentIntegrityChecker;
        this.assetChecks = assetChecks;
        this.findingSanitizer = findingSanitizer;
    }

    @Override
    public ProblemVerificationReport verify(ProblemVerificationRequest request) {
        VerificationScope scope = request.scope();
        QuestionSnapshotV1 snapshot = snapshotOf(request);

        List<VerificationFinding> findings;
        try {
            BlindQuestionFactory.requireSupportedVersion(snapshot);
            findings = scope == VerificationScope.ASSET
                    ? assetScope(request, snapshot)
                    : contentScope(request, snapshot);
        } catch (UnsupportedSnapshotVersionException e) {
            // 모르는 버전에서는 어느 항목도 판정하지 않았다. 열 항목 모두 ERROR 로 남긴다 —
            // 일부만 남기면 "검사하지 않은 항목"과 "통과한 항목"이 구분되지 않는다.
            log.warn("검증 중단 — requestId={}, scope={}, reason={}",
                    request.verificationRequestId(), scope, e.getMessage());
            findings = allChecksErrored(e.getMessage());
        }

        // 근거에서 정답을 걷어내고 길이를 자른다. overallStatus 산출 전에 하는 이유는 없다 —
        // 정제는 status·severity 를 건드리지 않는다. 다만 보고서에 나가는 값은 전부 이 단계를 지난다.
        FindingSanitizer.Result sanitized = findingSanitizer.sanitize(findings, snapshot);
        findings = sanitized.findings();
        if (!sanitized.redacted().isEmpty()) {
            // 값은 남기지 않는다. 몇 건이 어느 항목에서 걸렸는지만 남긴다.
            log.warn("검증 근거에서 정답을 제거 — requestId={}, count={}, checkTypes={}",
                    request.verificationRequestId(), sanitized.redacted().size(),
                    sanitized.redacted());
        }

        VerificationOverallStatus overallStatus = overallStatus(findings);
        // 문항 본문·Solver 응답 전문은 남기지 않는다. 개수와 판정만으로 추적한다.
        log.info("문제 검증 완료 — requestId={}, scope={}, operationType={}, overallStatus={}, "
                        + "findings={}, fail={}, error={}",
                request.verificationRequestId(), scope, request.operationType(), overallStatus,
                findings.size(),
                count(findings, VerificationFindingStatus.FAIL),
                count(findings, VerificationFindingStatus.ERROR));

        return new ProblemVerificationReport(
                request.verificationRequestId(), scope, overallStatus, findings);
    }

    /** 내용 검증. 자산은 보지 않는다 — {@code ASSET} 요청이 따로 온다. */
    private List<VerificationFinding> contentScope(
            ProblemVerificationRequest request, QuestionSnapshotV1 snapshot
    ) {
        EditVerificationContext editContext = editContext(request);
        List<VerificationFinding> findings = new ArrayList<>();

        findings.add(isolate(VerificationCheckType.CORRECTNESS, () -> correctness(snapshot)));
        findings.add(isolate(VerificationCheckType.ANSWER_CONSISTENCY,
                () -> structuralConsistencyCheck.check(snapshot)));
        // 기대 유형이 없으면 Finding 을 만들지 않는다. 여기서 NOT_APPLICABLE 을 내면 같은
        // CheckType 에 담긴 구조 검사 결과까지 비대상으로 보이게 된다.
        expectationChecks.questionTypeMatch(snapshot, request.expectation())
                .ifPresent(findings::add);
        findings.add(isolate(VerificationCheckType.CURRICULUM_ALIGNMENT,
                () -> expectationChecks.curriculumAlignment(snapshot, request.expectation())));
        findings.add(isolate(VerificationCheckType.DIFFICULTY,
                () -> expectationChecks.difficulty(snapshot, request.expectation())));
        findings.add(isolate(VerificationCheckType.EVALUATION_AREA,
                () -> expectationChecks.evaluationArea(snapshot, request.expectation())));
        findings.add(isolate(VerificationCheckType.DIAGNOSTIC_TYPE,
                () -> expectationChecks.diagnosticType(snapshot, request.expectation())));
        // 원본 검사는 Finding 을 여러 건 낼 수 있다. RUBRIC_QUALITY 는 항상 1건 포함된다.
        findings.addAll(isolateMany(
                List.of(VerificationCheckType.ANSWER_CONSISTENCY,
                        VerificationCheckType.RUBRIC_QUALITY),
                () -> contentIntegrityChecker.check(snapshot)));
        findings.add(Findings.notApplicable(VerificationCheckType.ASSET_CONSISTENCY,
                "CONTENT 범위에서는 자산을 판정하지 않습니다."));
        findings.add(isolate(VerificationCheckType.EDIT_REQUIREMENT,
                () -> editScopeChecks.editRequirement(snapshot, editContext)));
        findings.add(isolate(VerificationCheckType.PROTECTED_SCOPE,
                () -> editScopeChecks.protectedScope(snapshot, editContext)));
        return List.copyOf(findings);
    }

    /**
     * 정확성 판정. Blind 변환이 여기서 일어나고, Solver 는 정답을 보지 않는다.
     *
     * <p>서술형은 Solver 를 부르지 않는다. 대조할 종점 값이 없어 답을 받아도 쓸 데가 없다.
     */
    private VerificationFinding correctness(QuestionSnapshotV1 snapshot) {
        if (!correctnessChecker.requiresSolver(snapshot)) {
            return correctnessChecker.check(snapshot, SolverAnswer.notCalled());
        }
        BlindQuestion blind = blindQuestionFactory.from(snapshot);
        return correctnessChecker.check(snapshot, llmClient.solve(blind));
    }

    /**
     * 자산 검증. 내용 항목은 전부 {@code NOT_APPLICABLE} 로 낸다.
     *
     * <p>생략하지 않는 이유는 같다 — 이 범위에서 내용을 보지 않았다는 사실이 보고서에 남아야 한다.
     */
    private List<VerificationFinding> assetScope(
            ProblemVerificationRequest request, QuestionSnapshotV1 snapshot
    ) {
        List<VerificationFinding> findings = new ArrayList<>();
        findings.add(isolate(VerificationCheckType.ASSET_CONSISTENCY,
                () -> assetChecks.manifestReadiness(request.expectation(), request.assetManifest())));
        findings.add(isolate(VerificationCheckType.ASSET_CONSISTENCY,
                () -> assetChecks.altTextIntegrity(snapshot)));

        for (VerificationCheckType checkType : VerificationCheckType.values()) {
            if (checkType != VerificationCheckType.ASSET_CONSISTENCY) {
                findings.add(Findings.notApplicable(checkType,
                        "ASSET 범위에서는 내용을 판정하지 않습니다."));
            }
        }
        return List.copyOf(findings);
    }

    /**
     * 판정 하나를 격리한다. 예외는 {@code ERROR} Finding 이 되고 나머지 항목은 계속 나온다.
     *
     * <p>{@code FAIL} 로 바꾸지 않는다. 우리 코드가 터진 것과 문항이 틀린 것은 다르다.
     */
    private VerificationFinding isolate(
            VerificationCheckType checkType, Supplier<VerificationFinding> check
    ) {
        try {
            return check.get();
        } catch (SolverResponseParseException e) {
            // 모델이 형식을 어긴 경우다. 응답 전문은 남기지 않는다.
            log.warn("검증 응답 형식 위반 — checkType={}, reason={}", checkType, e.getMessage());
            return Findings.error(checkType, "검증 응답이 요구한 형식이 아닙니다.", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("검증 항목 실패 — checkType={}", checkType, e);
            return Findings.error(checkType, "검증 항목을 처리하지 못했습니다.", e.getMessage());
        }
    }

    /**
     * Finding 을 여러 건 내는 판정을 격리한다.
     *
     * <p>실패하면 <b>관련 CheckType 전부</b>에 ERROR 를 낸다. 한 호출로 여러 항목을 보는 구조라
     * 하나만 ERROR 로 두면 나머지가 판정된 것처럼 보인다.
     */
    private List<VerificationFinding> isolateMany(
            List<VerificationCheckType> checkTypes, Supplier<List<VerificationFinding>> check
    ) {
        try {
            return check.get();
        } catch (SolverResponseParseException e) {
            log.warn("검증 응답 형식 위반 — checkTypes={}, reason={}", checkTypes, e.getMessage());
            return checkTypes.stream()
                    .map(checkType -> Findings.error(
                            checkType, "검증 응답이 요구한 형식이 아닙니다.", e.getMessage()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("검증 항목 실패 — checkTypes={}", checkTypes, e);
            return checkTypes.stream()
                    .map(checkType -> Findings.error(
                            checkType, "검증 항목을 처리하지 못했습니다.", e.getMessage()))
                    .toList();
        }
    }

    private List<VerificationFinding> allChecksErrored(String message) {
        List<VerificationFinding> findings = new ArrayList<>();
        for (VerificationCheckType checkType : VerificationCheckType.values()) {
            findings.add(Findings.error(checkType, "검증을 수행하지 못했습니다.", message));
        }
        return List.copyOf(findings);
    }

    /**
     * 처리에 실패한 항목이 하나라도 있으면 {@code ERROR}, 없고 {@code ERROR} 심각도의
     * {@code FAIL} 이 있으면 {@code FAILED} 다.
     *
     * <p>둘이 동시에 있으면 {@code ERROR} 가 이긴다. {@code ERROR} 는 그 항목을 <b>판정하지 못했다</b>는
     * 뜻이고, 판정하지 못한 항목이 남아 있는 보고서는 확정된 결과가 아니다. 그걸 {@code FAILED} 로
     * 내보내면 조율측은 "검증이 끝났고 떨어졌다"로 읽어, 아직 아무도 보지 않은 항목을 본 것으로 처리한다.
     *
     * <p>{@code WARNING} 은 {@code FAILED} 를 만들지 않는다.
     */
    private VerificationOverallStatus overallStatus(List<VerificationFinding> findings) {
        boolean processingError = findings.stream()
                .anyMatch(finding -> finding.status() == VerificationFindingStatus.ERROR);
        if (processingError) {
            return VerificationOverallStatus.ERROR;
        }
        boolean blockingFail = findings.stream().anyMatch(finding ->
                finding.status() == VerificationFindingStatus.FAIL
                        && finding.severity() == VerificationSeverity.ERROR);
        return blockingFail
                ? VerificationOverallStatus.FAILED
                : VerificationOverallStatus.PASSED;
    }

    private static QuestionSnapshotV1 snapshotOf(ProblemVerificationRequest request) {
        ProblemCandidateDraft candidate = request.candidate();
        return candidate == null ? null : candidate.snapshot();
    }

    private static EditVerificationContext editContext(ProblemVerificationRequest request) {
        // sealed interface 이므로 패턴 매칭으로 가른다. GenerationVerificationContext 에는
        // 원본이 없어 수정 관련 판정을 할 수 없다.
        return request.context() instanceof EditVerificationContext editContext ? editContext : null;
    }

    private static long count(List<VerificationFinding> findings, VerificationFindingStatus status) {
        return findings.stream().filter(finding -> finding.status() == status).count();
    }
}
