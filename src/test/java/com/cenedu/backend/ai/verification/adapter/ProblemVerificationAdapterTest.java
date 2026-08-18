package com.cenedu.backend.ai.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cenedu.backend.ai.client.LlmUseCase;
import com.cenedu.backend.domain.grading.service.AnswerNormalizer;
import com.cenedu.backend.domain.grading.service.ExpressionEvaluator;
import com.cenedu.backend.domain.grading.service.RuleGrader;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetStatus;
import com.cenedu.backend.domain.problem.authoring.edit.EditChangeNature;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.authoring.verification.EditVerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationRequest;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;

import com.cenedu.backend.global.common.enums.QuestionType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 검증 Adapter 의 조립·판정·상태 산출을 본다. LLM 은 {@link FakeLlmClient} 로 갈아 끼운다.
 *
 * <p>판정기 단위 테스트를 따로 두지 않고 여기서 종단으로 본다. 이 Adapter 에서 실제로 틀리는 곳은
 * 판정 로직보다 <b>조립</b>이다 — Blind 를 안 쓰고 원본을 넘기거나, Finding 을 빠뜨리거나,
 * overallStatus 를 잘못 접는 실수가 그렇다. 그건 단위 테스트가 보지 못한다.
 */
class ProblemVerificationAdapterTest {

    private final FakeLlmClient fake = new FakeLlmClient();
    private final ProblemVerificationAdapter adapter = adapter(fake, true);

    private static ProblemVerificationAdapter adapter(FakeLlmClient fake, boolean contentCheck) {
        VerificationLlmClient llmClient = new VerificationLlmClient(fake, new ObjectMapper());
        return new ProblemVerificationAdapter(
                new BlindQuestionFactory(),
                llmClient,
                new CorrectnessChecker(new AnswerNormalizer(), new RuleGrader(new ExpressionEvaluator())),
                new StructuralConsistencyCheck(new SnapshotStructuralValidator()),
                new ExpectationChecks(),
                new EditScopeChecks(),
                new ContentIntegrityChecker(llmClient, new ContentCheckProperties(contentCheck)),
                new AssetChecks(llmClient),
                new FindingSanitizer());
    }

    @Test
    @DisplayName("입력 verificationRequestId 를 그대로 되돌려준다 — 멱등성 전제다")
    void reportEchoesRequestId() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(report.requestId()).isEqualTo(VerificationFixtures.REQUEST_ID);
        assertThat(report.scope()).isEqualTo(VerificationScope.CONTENT);
    }

    @Test
    @DisplayName("CONTENT 요청은 CheckType 10종 전부에 Finding 을 낸다 — 생략하지 않는다")
    void contentScopeReportsEveryCheckType() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        Set<VerificationCheckType> covered = EnumSet.noneOf(VerificationCheckType.class);
        report.findings().forEach(finding -> covered.add(finding.checkType()));
        assertThat(covered).containsExactlyInAnyOrder(VerificationCheckType.values());
    }

    @Test
    @DisplayName("CREATE 요청에서는 EDIT_REQUIREMENT · PROTECTED_SCOPE 가 NOT_APPLICABLE 이다")
    void createOperationMarksEditChecksNotApplicable() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(statusOf(report, VerificationCheckType.EDIT_REQUIREMENT))
                .isEqualTo(VerificationFindingStatus.NOT_APPLICABLE);
        assertThat(statusOf(report, VerificationCheckType.PROTECTED_SCOPE))
                .isEqualTo(VerificationFindingStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("Solver 프롬프트에 정답과 저작측 의도가 실리지 않는다")
    void solverPromptCarriesNoAnswers() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.stepFillSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse(Map.of(
                        "B1", VerificationFixtures.STEP_FILL_B1_ANSWER,
                        "B2", VerificationFixtures.STEP_FILL_B2_ANSWER)),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        adapter.verify(contentRequest(snapshot));

        String prompt = fake.solverPrompt();
        assertThat(prompt).doesNotContain("answerRaw", "explanation", "subUnitId", "diagnosticType");
        assertThat(prompt).contains("빈칸을 채워");
        // 정답 문자열은 JSON 이스케이프된 형태로도 없어야 한다.
        assertThat(prompt).doesNotContain(
                VerificationFixtures.STEP_FILL_B1_ANSWER.replace("\\", "\\\\"));
    }

    @Test
    @DisplayName("검증 호출은 저작측과 다른 모델을 쓰도록 VERIFICATION useCase 로 나간다")
    void verificationCallsUseVerificationUseCase() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        adapter.verify(contentRequest(snapshot));

        assertThat(fake.useCases).isNotEmpty().allMatch(useCase -> useCase == LlmUseCase.VERIFICATION);
        assertThat(fake.seeds).allMatch(seed -> seed != null);
    }

    @Test
    @DisplayName("Solver 답이 정답과 같으면 PASSED 다")
    void matchingAnswerPasses() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.multipleChoiceSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.MC_ANSWER_CHOICE_KEY),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(statusOf(report, VerificationCheckType.CORRECTNESS))
                .isEqualTo(VerificationFindingStatus.PASS);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.PASSED);
    }

    @Test
    @DisplayName("Solver 가 다른 보기를 고르면 FAILED + ANSWER_INCORRECT 다")
    void wrongChoiceFails() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.multipleChoiceSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", "C3"),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        VerificationFinding correctness = findingOf(report, VerificationCheckType.CORRECTNESS);
        assertThat(correctness.status()).isEqualTo(VerificationFindingStatus.FAIL);
        assertThat(correctness.code()).isEqualTo(VerificationIssueCode.ANSWER_INCORRECT);
        assertThat(correctness.evidence()).contains("C3").doesNotContain("C1");
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("STEP_FILL 은 한 칸만 틀려도 FAILED 이고 어느 칸인지 남는다")
    void oneWrongBlankFailsStepFill() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.stepFillSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse(Map.of(
                        "B1", VerificationFixtures.STEP_FILL_B1_ANSWER,
                        "B2", "999")),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        VerificationFinding correctness = findingOf(report, VerificationCheckType.CORRECTNESS);
        assertThat(correctness.status()).isEqualTo(VerificationFindingStatus.FAIL);
        assertThat(correctness.message()).contains("B2").doesNotContain("B1");
        // 근거는 틀린 칸의 답이어야 한다. 맞은 칸의 답을 담으면 판정과 어긋나 보인다.
        assertThat(correctness.evidence()).contains("999");
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("Solver 가 못 풀었다고 하면 UNVERIFIABLE 이다 — 틀렸다고 하지 않는다")
    void unsolvedIsUnverifiable() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.SOLVER_UNSOLVED, VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        VerificationFinding correctness = findingOf(report, VerificationCheckType.CORRECTNESS);
        assertThat(correctness.code()).isEqualTo(VerificationIssueCode.UNVERIFIABLE);
        assertThat(correctness.status()).isEqualTo(VerificationFindingStatus.FAIL);
    }

    @Test
    @DisplayName("WARNING 항목만 FAIL 이면 PASSED 를 유지한다")
    void warningDoesNotFailTheReport() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);
        VerificationExpectation expectation = VerificationFixtures.withExpectedDifficulty(
                VerificationFixtures.matchingExpectation(snapshot), "high");

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.contentRequest(snapshot, expectation, null));

        VerificationFinding difficulty = findingOf(report, VerificationCheckType.DIFFICULTY);
        assertThat(difficulty.status()).isEqualTo(VerificationFindingStatus.FAIL);
        assertThat(difficulty.severity()).isEqualTo(VerificationSeverity.WARNING);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.PASSED);
    }

    @Test
    @DisplayName("교육과정 이탈은 ERROR 심각도라 FAILED 를 만든다")
    void curriculumMismatchFailsTheReport() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);
        VerificationExpectation expectation = VerificationFixtures.withExpectedSubUnitId(
                VerificationFixtures.matchingExpectation(snapshot), 999L);

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.contentRequest(snapshot, expectation, null));

        VerificationFinding finding = findingOf(report, VerificationCheckType.CURRICULUM_ALIGNMENT);
        assertThat(finding.code()).isEqualTo(VerificationIssueCode.CURRICULUM_MISMATCH);
        assertThat(finding.severity()).isEqualTo(VerificationSeverity.ERROR);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("서술형은 CORRECTNESS 가 UNVERIFIABLE 이라 항상 FAILED 다 — 의도된 결과다")
    void essayAlwaysFails() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.essaySnapshot();
        // Solver 는 호출하지 않는다. 원본 검사 한 번만 부른다(루브릭 절 포함).
        fake.respondWith(VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        VerificationFinding correctness = findingOf(report, VerificationCheckType.CORRECTNESS);
        assertThat(correctness.status()).isEqualTo(VerificationFindingStatus.FAIL);
        assertThat(correctness.code()).isEqualTo(VerificationIssueCode.UNVERIFIABLE);
        assertThat(statusOf(report, VerificationCheckType.RUBRIC_QUALITY))
                .isEqualTo(VerificationFindingStatus.PASS);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
        // Solver 를 부르지 않는다 — 판정에 쓰이지도 않는 호출로 문항을 외부에 보내지 않는다.
        assertThat(fake.userPrompts).as("서술형에서 Solver 를 불렀다").hasSize(1);
        assertThat(fake.systemPrompts.getFirst()).contains("채점 기준");
    }

    @Test
    @DisplayName("서술형이 아니면 RUBRIC_QUALITY 는 NOT_APPLICABLE 이고 LLM 을 부르지 않는다")
    void rubricQualityIsEssayOnly() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(statusOf(report, VerificationCheckType.RUBRIC_QUALITY))
                .isEqualTo(VerificationFindingStatus.NOT_APPLICABLE);
        // Solver + 원본 검사 2회. 루브릭 전용 호출은 없다.
        assertThat(fake.userPrompts).hasSize(2);
    }

    @Test
    @DisplayName("Solver 응답 형식 위반은 ERROR 이며 FAIL 이 아니다")
    void parseFailureIsErrorNotFail() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith("정답은 2^2 × 3^2 × 7 입니다.", VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        VerificationFinding correctness = findingOf(report, VerificationCheckType.CORRECTNESS);
        assertThat(correctness.status()).isEqualTo(VerificationFindingStatus.ERROR);
        assertThat(correctness.code()).isEqualTo(VerificationIssueCode.PROVIDER_ERROR);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.ERROR);
    }

    @Test
    @DisplayName("판정 실패와 확정 결함이 함께 있으면 ERROR 다 — 판정하지 못한 항목이 남았다")
    void processingErrorWinsOverFailed() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        // CORRECTNESS 는 형식 위반으로 ERROR, CURRICULUM_ALIGNMENT 는 ERROR 심각도의 FAIL 이 된다.
        fake.respondWith("JSON 이 아닌 응답", VerificationFixtures.CONTENT_CHECK_CLEAN);
        VerificationExpectation expectation = VerificationFixtures.withExpectedSubUnitId(
                VerificationFixtures.matchingExpectation(snapshot), 999L);

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.contentRequest(snapshot, expectation, null));

        assertThat(statusOf(report, VerificationCheckType.CORRECTNESS))
                .isEqualTo(VerificationFindingStatus.ERROR);
        assertThat(findingOf(report, VerificationCheckType.CURRICULUM_ALIGNMENT))
                .satisfies(finding -> {
                    assertThat(finding.status()).isEqualTo(VerificationFindingStatus.FAIL);
                    assertThat(finding.severity()).isEqualTo(VerificationSeverity.ERROR);
                });
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.ERROR);
    }

    @Test
    @DisplayName("코드 펜스가 붙어 와도 파싱한다")
    void codeFenceIsStripped() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                "```json\n" + VerificationFixtures.solverResponse(
                        "MAIN", VerificationFixtures.SHORT_INPUT_ANSWER) + "\n```",
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(statusOf(report, VerificationCheckType.CORRECTNESS))
                .isEqualTo(VerificationFindingStatus.PASS);
    }

    @Test
    @DisplayName("한 항목이 터져도 나머지 Finding 은 나온다")
    void oneFailingCheckDoesNotKillTheReport() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        FakeLlmClient failing = new FakeLlmClient().failWith(new IllegalStateException("provider down"));

        ProblemVerificationReport report = adapter(failing, true).verify(contentRequest(snapshot));

        assertThat(statusOf(report, VerificationCheckType.CORRECTNESS))
                .isEqualTo(VerificationFindingStatus.ERROR);
        assertThat(statusOf(report, VerificationCheckType.ANSWER_CONSISTENCY))
                .as("코드 판정은 LLM 이 죽어도 결과를 낸다")
                .isEqualTo(VerificationFindingStatus.PASS);
        assertThat(report.findings())
                .as("CheckType 10종이 모두 덮여야 한다")
                .extracting(VerificationFinding::checkType)
                .containsAll(List.of(VerificationCheckType.values()));
    }

    @Test
    @DisplayName("모르는 스키마 버전은 열 항목 모두 ERROR 이고 판정하지 않는다")
    void unsupportedSchemaVersionErrorsEveryCheck() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.withSchemaVersion(
                VerificationFixtures.shortInputSnapshot(), 99);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(report.findings()).hasSize(VerificationCheckType.values().length);
        assertThat(report.findings())
                .allMatch(finding -> finding.status() == VerificationFindingStatus.ERROR);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.ERROR);
        assertThat(fake.userPrompts).as("판정을 시도하지 않았으므로 LLM 도 부르지 않는다").isEmpty();
    }

    @Test
    @DisplayName("수정 지시가 반영되지 않으면 EDIT_REQUIREMENT 가 FAIL 이다")
    void unappliedEditInstructionFails() {
        QuestionSnapshotV1 base = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);
        EditVerificationContext context = VerificationFixtures.editContext(
                base,
                List.of(new ProblemEditInstruction(EditTargetType.CONTENT_BLOCK, "CB1",
                        EditChangeNature.SEMANTIC, "발문을 더 쉽게 고쳐라")),
                List.of());

        // 후보가 원본과 같다 — 지시를 무시했다.
        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.contentRequest(
                        base, VerificationFixtures.matchingExpectation(base), context));

        VerificationFinding finding = findingOf(report, VerificationCheckType.EDIT_REQUIREMENT);
        assertThat(finding.status()).isEqualTo(VerificationFindingStatus.FAIL);
        assertThat(finding.code()).isEqualTo(VerificationIssueCode.EDIT_REQUIREMENT_MISSING);
        assertThat(finding.evidence()).contains("CONTENT_BLOCK(CB1)");
    }

    @Test
    @DisplayName("보호 대상이 바뀌면 PROTECTED_SCOPE 가 FAIL 이다")
    void changedProtectedTargetFails() {
        QuestionSnapshotV1 base = VerificationFixtures.shortInputSnapshot();
        QuestionSnapshotV1 candidate =
                VerificationFixtures.withFirstBlockText(base, "252를 소인수분해하시오.");
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);
        EditVerificationContext context = VerificationFixtures.editContext(
                base,
                List.of(new ProblemEditInstruction(EditTargetType.EXPLANATION, null,
                        EditChangeNature.SEMANTIC, "해설을 다듬어라")),
                List.of(new ProblemEditTargetRef(EditTargetType.CONTENT_BLOCK, "CB1")));

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.contentRequest(
                        candidate, VerificationFixtures.matchingExpectation(candidate), context));

        VerificationFinding finding = findingOf(report, VerificationCheckType.PROTECTED_SCOPE);
        assertThat(finding.status()).isEqualTo(VerificationFindingStatus.FAIL);
        assertThat(finding.code()).isEqualTo(VerificationIssueCode.PROTECTED_SCOPE_CHANGED);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("ASSET 범위는 자산만 판정하고 내용 항목은 NOT_APPLICABLE 이다")
    void assetScopeJudgesAssetsOnly() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.figureSnapshot();
        fake.respondWith(VerificationFixtures.ASSET_OK);
        DraftAssetManifest manifest = new DraftAssetManifest(
                DraftAssetManifest.CURRENT_SCHEMA_VERSION,
                List.of(),
                List.of(new DraftAssetArtifact("F1", DraftAssetStatus.READY,
                        "draft/F1.png", "image/png", 400, 400, "checksum", 1, null)));
        VerificationExpectation expectation = VerificationFixtures.withRequiredAssetKeys(
                VerificationFixtures.matchingExpectation(snapshot), List.of("F1"));

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.assetRequest(snapshot, expectation, manifest));

        assertThat(report.scope()).isEqualTo(VerificationScope.ASSET);
        assertThat(report.findings())
                .filteredOn(finding -> finding.checkType() == VerificationCheckType.ASSET_CONSISTENCY)
                .hasSize(2)
                .allMatch(finding -> finding.status() == VerificationFindingStatus.PASS);
        assertThat(statusOf(report, VerificationCheckType.CORRECTNESS))
                .isEqualTo(VerificationFindingStatus.NOT_APPLICABLE);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.PASSED);
    }

    @Test
    @DisplayName("준비되지 않은 자산은 ASSET_INCONSISTENT 로 FAILED 다")
    void notReadyAssetFails() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.figureSnapshot();
        fake.respondWith(VerificationFixtures.ASSET_OK);
        DraftAssetManifest manifest = new DraftAssetManifest(
                DraftAssetManifest.CURRENT_SCHEMA_VERSION,
                List.of(),
                List.of(new DraftAssetArtifact("F1", DraftAssetStatus.GENERATING,
                        null, null, null, null, null, 1, null)));
        VerificationExpectation expectation = VerificationFixtures.withRequiredAssetKeys(
                VerificationFixtures.matchingExpectation(snapshot), List.of("F1"));

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.assetRequest(snapshot, expectation, manifest));

        assertThat(report.findings())
                .filteredOn(finding -> finding.status() == VerificationFindingStatus.FAIL)
                .singleElement()
                .satisfies(finding -> assertThat(finding.code())
                        .isEqualTo(VerificationIssueCode.ASSET_INCONSISTENT));
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("altText 검사는 원본을 본다 — 정답이 프롬프트에 들어가야 판정할 수 있다")
    void altTextJudgementSeesTheOriginal() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.figureSnapshot();
        fake.respondWith(VerificationFixtures.ASSET_OK);
        VerificationExpectation expectation =
                VerificationFixtures.matchingExpectation(snapshot);

        adapter.verify(VerificationFixtures.assetRequest(
                snapshot, expectation, DraftAssetManifest.planned(List.of())));

        assertThat(fake.userPrompts).singleElement()
                .satisfies(prompt -> assertThat(prompt)
                        .contains("a^2")
                        .contains("사분원"));
    }

    @Test
    @DisplayName("전부 PASS · NOT_APPLICABLE 이면 PASSED 다")
    void allPassOrNotApplicableIsPassed() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(report.findings())
                .extracting(VerificationFinding::status)
                .containsOnly(VerificationFindingStatus.PASS,
                        VerificationFindingStatus.NOT_APPLICABLE);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.PASSED);
    }

    @Test
    @DisplayName("요청한 유형과 다르게 나오면 TYPE_MISMATCH 로 FAILED 다")
    void questionTypeMismatchFails() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);
        VerificationExpectation expectation = VerificationFixtures.withExpectedQuestionType(
                VerificationFixtures.matchingExpectation(snapshot), QuestionType.STEP_FILL);

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.contentRequest(snapshot, expectation, null));

        assertThat(consistencyFindings(report))
                .anySatisfy(finding -> {
                    assertThat(finding.status()).isEqualTo(VerificationFindingStatus.FAIL);
                    assertThat(finding.evidence()).startsWith("TYPE_MISMATCH:");
                    assertThat(finding.severity()).isEqualTo(VerificationSeverity.ERROR);
                });
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("기대 유형이 없으면 유형 Finding 을 만들지 않는다 — 구조 검사 결과가 남아야 한다")
    void missingExpectedQuestionTypeProducesNoFinding() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);
        VerificationExpectation expectation = VerificationFixtures.withExpectedQuestionType(
                VerificationFixtures.matchingExpectation(snapshot), null);

        ProblemVerificationReport report = adapter.verify(
                VerificationFixtures.contentRequest(snapshot, expectation, null));

        assertThat(consistencyFindings(report))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.status()).isEqualTo(VerificationFindingStatus.PASS);
                    // Validator 위임 결과다. NOT_APPLICABLE 로 덮이지 않아야 한다.
                    assertThat(finding.message()).contains("논리 키 참조");
                });
    }

    @Test
    @DisplayName("개념 안내가 정답 값을 담으면 ERROR, 풀이 방향만 지정하면 WARNING 이다")
    void leakageSeveritySplits() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.contentCheckResponse("LEAKAGE", "SOLUTION_DIRECTION",
                        "learningGuide.keyPoints[0]", "풀이 순서를 지정합니다."));

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(consistencyFindings(report))
                .anySatisfy(finding -> {
                    assertThat(finding.evidence()).startsWith("LEAKAGE:");
                    assertThat(finding.severity()).isEqualTo(VerificationSeverity.WARNING);
                });
        // WARNING 은 FAILED 를 만들지 않는다.
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.PASSED);
    }

    @Test
    @DisplayName("정답 값 누출은 ERROR 이며 FAILED 를 만든다")
    void answerValueLeakageIsError() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.contentCheckResponse("LEAKAGE", "ANSWER_VALUE",
                        "learningGuide.keyPoints[0]", "최종 계산 결과를 포함합니다."));

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(consistencyFindings(report))
                .anySatisfy(finding ->
                        assertThat(finding.severity()).isEqualTo(VerificationSeverity.ERROR));
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("해설 모순은 EXPLANATION 접두어로 나간다")
    void explanationDefectUsesPrefix() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.contentCheckResponse("EXPLANATION", "",
                        "explanation", "결론이 정답과 반대입니다."));

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(consistencyFindings(report))
                .anySatisfy(finding -> assertThat(finding.evidence())
                        .startsWith("EXPLANATION:")
                        .contains("explanation"));
    }

    @Test
    @DisplayName("토글이 꺼지면 원본 검사를 하지 않고 Finding 도 만들지 않는다 — PASS 로도 안 낸다")
    void disabledToggleSkipsContentCheck() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        FakeLlmClient offFake = new FakeLlmClient().respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER));

        ProblemVerificationReport report =
                adapter(offFake, false).verify(contentRequest(snapshot));

        // Solver 1회뿐이다.
        assertThat(offFake.userPrompts).hasSize(1);
        // 구조 검사와 유형 대조는 코드 판정이라 그대로 남는다. 없어야 하는 것은 원본 검사 결과다.
        assertThat(consistencyFindings(report))
                .as("해설·누출 Finding 이 생기면 안 된다 — PASS 로도 안 된다")
                .noneMatch(finding -> finding.evidence() != null
                        && (finding.evidence().startsWith("EXPLANATION:")
                                || finding.evidence().startsWith("LEAKAGE:")));
        assertThat(consistencyFindings(report))
                .extracting(VerificationFinding::message)
                .noneMatch(message -> message.contains("해설") || message.contains("개념 안내"));
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.PASSED);
    }

    @Test
    @DisplayName("토글이 꺼져도 서술형 루브릭은 돈다 — 호출 0회가 되지 않는다")
    void disabledToggleStillRunsRubricForEssay() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.essaySnapshot();
        FakeLlmClient offFake = new FakeLlmClient().respondWith(VerificationFixtures.RUBRIC_OK);

        ProblemVerificationReport report =
                adapter(offFake, false).verify(contentRequest(snapshot));

        assertThat(offFake.userPrompts).hasSize(1);
        assertThat(offFake.systemPrompts.getFirst())
                .as("루브릭 전용 프롬프트여야 한다")
                .contains("채점 기준");
        assertThat(statusOf(report, VerificationCheckType.RUBRIC_QUALITY))
                .isEqualTo(VerificationFindingStatus.PASS);
    }

    @Test
    @DisplayName("원본 검사가 실패하면 관련 CheckType 전부 ERROR 다")
    void contentCheckFailureErrorsBothCheckTypes() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                "findings 없는 응답");

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(consistencyFindings(report))
                .anySatisfy(finding -> {
                    assertThat(finding.status()).isEqualTo(VerificationFindingStatus.ERROR);
                    assertThat(finding.code()).isEqualTo(VerificationIssueCode.PROVIDER_ERROR);
                });
        assertThat(statusOf(report, VerificationCheckType.RUBRIC_QUALITY))
                .isEqualTo(VerificationFindingStatus.ERROR);
        assertThat(report.overallStatus()).isEqualTo(VerificationOverallStatus.ERROR);
    }

    @Test
    @DisplayName("원본 검사 프롬프트에는 정답과 해설이 들어간다 — Blind 로는 판정할 수 없다")
    void contentCheckPromptCarriesTheOriginal() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.shortInputSnapshot();
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", VerificationFixtures.SHORT_INPUT_ANSWER),
                VerificationFixtures.CONTENT_CHECK_CLEAN);

        adapter.verify(contentRequest(snapshot));

        assertThat(fake.userPrompts.get(1))
                .contains("[정답]")
                .contains("[해설]")
                .contains("[개념 안내]")
                .contains(VerificationFixtures.SHORT_INPUT_ANSWER);
    }

    @Test
    @DisplayName("근거에 정답이 실려 오면 뭉갠 뒤 보고서에 담는다")
    void answerInEvidenceIsRedactedBeforeReport() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.withAnswerRaw(
                VerificationFixtures.shortInputSnapshot(), "420");
        fake.respondWith(
                VerificationFixtures.solverResponse("MAIN", "420"),
                VerificationFixtures.contentCheckResponse("LEAKAGE", "ANSWER_VALUE",
                        "learningGuide", "정답 420 을 그대로 담고 있습니다."));

        ProblemVerificationReport report = adapter.verify(contentRequest(snapshot));

        assertThat(report.findings())
                .as("어떤 Finding 에도 정답이 남아 있으면 안 된다")
                .allSatisfy(finding -> {
                    assertThat(finding.evidence() == null || !finding.evidence().contains("420"))
                            .isTrue();
                    assertThat(finding.message() == null || !finding.message().contains("420"))
                            .isTrue();
                });
        assertThat(consistencyFindings(report))
                .anySatisfy(finding ->
                        assertThat(finding.evidence()).isEqualTo(FindingSanitizer.REDACTED));
    }

    private static List<VerificationFinding> consistencyFindings(ProblemVerificationReport report) {
        return report.findings().stream()
                .filter(finding ->
                        finding.checkType() == VerificationCheckType.ANSWER_CONSISTENCY)
                .toList();
    }

    private ProblemVerificationRequest contentRequest(QuestionSnapshotV1 snapshot) {
        return VerificationFixtures.contentRequest(
                snapshot, VerificationFixtures.matchingExpectation(snapshot), null);
    }

    private static VerificationFindingStatus statusOf(
            ProblemVerificationReport report, VerificationCheckType checkType
    ) {
        return findingOf(report, checkType).status();
    }

    private static VerificationFinding findingOf(
            ProblemVerificationReport report, VerificationCheckType checkType
    ) {
        return report.findings().stream()
                .filter(finding -> finding.checkType() == checkType)
                .findFirst()
                .orElseThrow(() -> new AssertionError(checkType + " Finding 이 없다"));
    }
}
