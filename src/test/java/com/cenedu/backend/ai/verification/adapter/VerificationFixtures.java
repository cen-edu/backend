package com.cenedu.backend.ai.verification.adapter;

import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.verification.EditVerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.GenerationVerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationRequest;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOperationType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotRubricItem;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegment;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegmentType;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotStep;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * 검증 Adapter 테스트가 쓰는 스냅샷 픽스처.
 *
 * <p>네 유형 모두 {@code SnapshotStructuralValidator} 를 통과하는 값이다. 통과하지 않는 픽스처를
 * 쓰면 {@code ANSWER_CONSISTENCY} 가 항상 FAIL 로 나와서, 정작 다른 판정의 검증이 묻힌다.
 *
 * <p>정답 문자열은 발문·보기·키에 나타나지 않는 값으로 고른다. 누출 테스트 2층이 부분 문자열까지
 * 보기 때문에, 정답이 우연히 발문에 들어 있으면 누출이 아닌데 실패한다.
 */
final class VerificationFixtures {

    static final java.util.UUID REQUEST_ID =
            java.util.UUID.fromString("3f2c9c2e-0f2a-4b8d-9c1e-5a6b7c8d9e01");

    static final String MC_ANSWER_CHOICE_KEY = "C1";
    static final String SHORT_INPUT_ANSWER = "2^2 \\times 3^2 \\times 7";
    static final String STEP_FILL_B1_ANSWER = "2^4 \\times 3";
    static final String STEP_FILL_B2_ANSWER = "\\frac{144}{6}";

    private VerificationFixtures() {
    }

    static QuestionSnapshotV1 multipleChoiceSnapshot() {
        return new QuestionSnapshotV1(
                QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                new SnapshotMetadata(
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionPresentation.TEXT_ONLY,
                        "mid",
                        101L,
                        "M7-NUM-01",
                        EvaluationArea.CALCULATION,
                        null),
                List.of(text("CB1", 0, "다음 중 108의 소인수분해로 옳은 것을 고르시오.")),
                List.of(),
                List.of(
                        new SnapshotChoice("C1", 0, "2^2 \\times 3^3"),
                        new SnapshotChoice("C2", 1, "2^3 \\times 3^2"),
                        new SnapshotChoice("C3", 2, "2 \\times 3^4"),
                        new SnapshotChoice("C4", 3, "2^2 \\times 3^2")),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, MC_ANSWER_CHOICE_KEY, null,
                        CompareMethod.CHOICE, null, null)),
                "108을 2로 두 번, 3으로 세 번 나누면 몫이 1이 되므로 답은 첫 번째 보기다.",
                guide(),
                List.of());
    }

    static QuestionSnapshotV1 shortInputSnapshot() {
        return new QuestionSnapshotV1(
                QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                new SnapshotMetadata(
                        QuestionType.SHORT_INPUT,
                        QuestionPresentation.TEXT_ONLY,
                        "low",
                        102L,
                        "M7-NUM-01",
                        EvaluationArea.CALCULATION,
                        null),
                List.of(text("CB1", 0, "252를 소인수분해하여 지수를 사용해 나타내시오.")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, SHORT_INPUT_ANSWER, null,
                        CompareMethod.EXACT, null, null)),
                "252를 작은 소수부터 차례로 나누면 지수 표현을 얻는다.",
                guide(),
                List.of());
    }

    static QuestionSnapshotV1 stepFillSnapshot() {
        return new QuestionSnapshotV1(
                QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                new SnapshotMetadata(
                        QuestionType.STEP_FILL,
                        QuestionPresentation.TEXT_ONLY,
                        "mid",
                        103L,
                        "M7-NUM-02",
                        EvaluationArea.PROBLEM_SOLVING,
                        null),
                List.of(text("CB1", 0, "빈칸을 채워 48과 그 배수의 관계를 완성하시오.")),
                List.of(),
                List.of(),
                List.of(
                        new SnapshotStep("ST1", 0, "48을 소인수분해한다", List.of(
                                segmentText("48을 소인수분해하면 "),
                                blank("B1"),
                                segmentText(" 이다."))),
                        new SnapshotStep("ST2", 1, "구한 값으로 몫을 구한다", List.of(
                                answerRef("B1"),
                                segmentText(" 를 이용하면 몫은 "),
                                blank("B2"),
                                segmentText(" 이다.")))),
                List.of(
                        new SnapshotAnswerUnit("B1", "ST1", 0, STEP_FILL_B1_ANSWER, null,
                                CompareMethod.EXACT, DiagnosticType.EXECUTE, null),
                        new SnapshotAnswerUnit("B2", "ST2", 1, STEP_FILL_B2_ANSWER, null,
                                CompareMethod.VALUE, DiagnosticType.ANSWER, null)),
                "소인수분해를 지수로 쓰고, 그 결과로 나눗셈을 정리한다.",
                guide(),
                List.of());
    }

    static QuestionSnapshotV1 essaySnapshot() {
        return new QuestionSnapshotV1(
                QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                new SnapshotMetadata(
                        QuestionType.ESSAY,
                        QuestionPresentation.TEXT_ONLY,
                        "high",
                        104L,
                        "M7-NUM-03",
                        EvaluationArea.REASONING,
                        null),
                List.of(text("CB1", 0, "소인수분해를 이용해 최소공배수를 구하는 과정을 서술하시오.")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, null, null, CompareMethod.RUBRIC, null, null)),
                "두 수를 각각 소인수분해한 뒤 각 소인수의 최대 지수를 곱한다.",
                guide(),
                List.of(
                        new SnapshotRubricItem("R1", 0, "두 수를 각각 소인수분해했다", 50),
                        new SnapshotRubricItem("R2", 1, "각 소인수의 최대 지수를 곱했다", 50)));
    }

    /** WITH_FIGURE 문항. ASSET scope 판정과 assetRef 정합 검사에 쓴다. */
    static QuestionSnapshotV1 figureSnapshot() {
        return new QuestionSnapshotV1(
                QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                new SnapshotMetadata(
                        QuestionType.SHORT_INPUT,
                        QuestionPresentation.WITH_FIGURE,
                        "mid",
                        105L,
                        "M7-GEO-01",
                        EvaluationArea.UNDERSTANDING,
                        null),
                List.of(
                        text("CB1", 0, "그림을 보고 색칠한 부분의 넓이를 구하시오."),
                        new SnapshotContentBlock("CB2", SnapshotBlockKind.FIGURE, 1, null, "F1", null)),
                List.of(new SnapshotAssetReference("F1", "한 변이 a인 정사각형 안에 반지름 a의 사분원이 그려져 있다.")),
                List.of(),
                List.of(),
                List.of(new SnapshotAnswerUnit(
                        "MAIN", null, 0, "a^2 - \\frac{\\pi a^2}{4}", null,
                        CompareMethod.EXACT, null, null)),
                "정사각형 넓이에서 사분원 넓이를 뺀다.",
                guide(),
                List.of());
    }

    static QuestionSnapshotV1 withSchemaVersion(QuestionSnapshotV1 snapshot, int schemaVersion) {
        return new QuestionSnapshotV1(
                schemaVersion,
                snapshot.metadata(),
                snapshot.contentBlocks(),
                snapshot.assets(),
                snapshot.choices(),
                snapshot.steps(),
                snapshot.answerUnits(),
                snapshot.explanation(),
                snapshot.learningGuide(),
                snapshot.rubricItems());
    }

    /** 첫 answerUnit 의 answerRaw 만 바꾼다. 정답 유출 방지 테스트가 쓰는 값이다. */
    static QuestionSnapshotV1 withAnswerRaw(QuestionSnapshotV1 snapshot, String answerRaw) {
        SnapshotAnswerUnit first = snapshot.answerUnits().getFirst();
        List<SnapshotAnswerUnit> units = new java.util.ArrayList<>(snapshot.answerUnits());
        units.set(0, new SnapshotAnswerUnit(
                first.unitKey(), first.stepKey(), first.displayOrder(), answerRaw,
                first.answerNormalized(), first.compareMethod(), first.diagnosticType(),
                first.displayUnit()));
        return new QuestionSnapshotV1(
                snapshot.schemaVersion(),
                snapshot.metadata(),
                snapshot.contentBlocks(),
                snapshot.assets(),
                snapshot.choices(),
                snapshot.steps(),
                List.copyOf(units),
                snapshot.explanation(),
                snapshot.learningGuide(),
                snapshot.rubricItems());
    }

    /** 기대 문항 유형만 바꾼다. TYPE_MISMATCH 대조에 쓴다. */
    static VerificationExpectation withExpectedQuestionType(
            VerificationExpectation expectation, QuestionType questionType
    ) {
        return new VerificationExpectation(
                questionType,
                expectation.expectedDifficulty(),
                expectation.expectedCurriculum(),
                expectation.targetEvaluationArea(),
                expectation.targetDiagnosticTypes(),
                expectation.requiredAssetKeys());
    }

    /** 원본 검사가 결함을 못 찾은 응답. */
    static final String CONTENT_CHECK_CLEAN = """
            {"findings": []}""";

    /** 원본 검사 결함 하나를 담은 응답. */
    static String contentCheckResponse(String type, String kind, String location, String detail) {
        return """
                {"findings": [{"type": "%s", "kind": "%s", "location": "%s", "detail": "%s"}]}"""
                .formatted(type, kind, location, detail);
    }

    static QuestionSnapshotV1 withMetadata(QuestionSnapshotV1 snapshot, SnapshotMetadata metadata) {
        return new QuestionSnapshotV1(
                snapshot.schemaVersion(),
                metadata,
                snapshot.contentBlocks(),
                snapshot.assets(),
                snapshot.choices(),
                snapshot.steps(),
                snapshot.answerUnits(),
                snapshot.explanation(),
                snapshot.learningGuide(),
                snapshot.rubricItems());
    }

    static QuestionSnapshotV1 withFirstBlockText(QuestionSnapshotV1 snapshot, String text) {
        SnapshotContentBlock first = snapshot.contentBlocks().getFirst();
        List<SnapshotContentBlock> blocks = new java.util.ArrayList<>(snapshot.contentBlocks());
        blocks.set(0, new SnapshotContentBlock(
                first.blockKey(), first.blockKind(), first.displayOrder(), text, null, null));
        return new QuestionSnapshotV1(
                snapshot.schemaVersion(),
                snapshot.metadata(),
                List.copyOf(blocks),
                snapshot.assets(),
                snapshot.choices(),
                snapshot.steps(),
                snapshot.answerUnits(),
                snapshot.explanation(),
                snapshot.learningGuide(),
                snapshot.rubricItems());
    }


    // ── 요청 조립 ─────────────────────────────────────────────────────────────

    static ProblemVerificationRequest contentRequest(
            QuestionSnapshotV1 snapshot,
            VerificationExpectation expectation,
            VerificationContext context
    ) {
        return new ProblemVerificationRequest(
                REQUEST_ID,
                VerificationScope.CONTENT,
                context instanceof EditVerificationContext
                        ? VerificationOperationType.EDIT : VerificationOperationType.CREATE,
                new ProblemCandidateDraft(REQUEST_ID, snapshot, List.of(), null),
                DraftAssetManifest.planned(List.of()),
                expectation,
                context);
    }

    static ProblemVerificationRequest assetRequest(
            QuestionSnapshotV1 snapshot,
            VerificationExpectation expectation,
            DraftAssetManifest manifest
    ) {
        return new ProblemVerificationRequest(
                REQUEST_ID,
                VerificationScope.ASSET,
                VerificationOperationType.CREATE,
                new ProblemCandidateDraft(REQUEST_ID, snapshot, List.of(), null),
                manifest,
                expectation,
                new GenerationVerificationContext(GenerationPurpose.GENERAL_LEARNING_SHORTAGE, List.of()));
    }

    /** 스냅샷과 완전히 일치하는 기대치. 값 대조 항목이 모두 PASS 가 되는 입력이다. */
    static VerificationExpectation matchingExpectation(QuestionSnapshotV1 snapshot) {
        SnapshotMetadata metadata = snapshot.metadata();
        List<DiagnosticType> diagnosticTypes = snapshot.answerUnits().stream()
                .map(SnapshotAnswerUnit::diagnosticType)
                .filter(type -> type != null)
                .distinct()
                .toList();
        return new VerificationExpectation(
                metadata.questionType(),
                metadata.difficulty(),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null,
                        metadata.subUnitId(), "수와 연산", "소인수분해", "소인수분해"),
                metadata.evaluationArea(),
                diagnosticTypes,
                List.of());
    }

    static VerificationExpectation withExpectedDifficulty(
            VerificationExpectation expectation, String difficulty
    ) {
        return new VerificationExpectation(
                expectation.expectedQuestionType(),
                difficulty,
                expectation.expectedCurriculum(),
                expectation.targetEvaluationArea(),
                expectation.targetDiagnosticTypes(),
                expectation.requiredAssetKeys());
    }

    static VerificationExpectation withExpectedSubUnitId(
            VerificationExpectation expectation, Long subUnitId
    ) {
        CurriculumScope base = expectation.expectedCurriculum();
        return new VerificationExpectation(
                expectation.expectedQuestionType(),
                expectation.expectedDifficulty(),
                new CurriculumScope(base.curriculumRevision(), base.schoolLevel(), base.grade(),
                        base.semester(), base.achievementStandardId(), subUnitId,
                        base.majorUnitName(), base.middleUnitName(), base.subUnitName()),
                expectation.targetEvaluationArea(),
                expectation.targetDiagnosticTypes(),
                expectation.requiredAssetKeys());
    }

    static VerificationExpectation withRequiredAssetKeys(
            VerificationExpectation expectation, List<String> assetKeys
    ) {
        return new VerificationExpectation(
                expectation.expectedQuestionType(),
                expectation.expectedDifficulty(),
                expectation.expectedCurriculum(),
                expectation.targetEvaluationArea(),
                expectation.targetDiagnosticTypes(),
                assetKeys);
    }

    static EditVerificationContext editContext(
            QuestionSnapshotV1 baseSnapshot,
            List<ProblemEditInstruction> instructions,
            List<ProblemEditTargetRef> protectedTargets
    ) {
        return new EditVerificationContext(
                baseSnapshot, instructions, List.of(), List.of(), protectedTargets);
    }

    /** Solver 가 정답을 맞힌 응답. */
    static String solverResponse(String unitKey, String answer) {
        return solverResponse(Map.of(unitKey, answer));
    }

    static String solverResponse(Map<String, String> answers) {
        String entries = answers.entrySet().stream()
                .map(entry -> "{\"unitKey\": \"%s\", \"answer\": \"%s\"}"
                        // JSON 안에서는 백슬래시가 두 개여야 한다. LaTeX 정답이 전부 여기 걸린다.
                        .formatted(entry.getKey(), entry.getValue().replace("\\", "\\\\")))
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                {"solved": true, "answers": [%s], "reason": "소인수분해로 차례로 계산했다."}"""
                .formatted(entries);
    }

    static final String SOLVER_UNSOLVED =
            """
            {"solved": false, "answers": [], "reason": "조건이 부족해 풀 수 없다."}""";

    static final String RUBRIC_OK = """
            {"axis": "", "detail": ""}""";

    static final String ASSET_OK = """
            {"issue": "", "detail": ""}""";

    private static SnapshotContentBlock text(String blockKey, int order, String text) {
        return new SnapshotContentBlock(blockKey, SnapshotBlockKind.TEXT, order, text, null, null);
    }

    private static SnapshotSegment segmentText(String text) {
        return new SnapshotSegment(SnapshotSegmentType.TEXT, text, null);
    }

    private static SnapshotSegment blank(String unitKey) {
        return new SnapshotSegment(SnapshotSegmentType.BLANK, null, unitKey);
    }

    private static SnapshotSegment answerRef(String unitKey) {
        return new SnapshotSegment(SnapshotSegmentType.ANSWER_REF, null, unitKey);
    }

    private static SnapshotLearningGuide guide() {
        return new SnapshotLearningGuide(
                "소인수분해",
                "합성수를 소수의 곱으로 나타내는 방법이다.",
                List.of("소수는 1과 자신만을 약수로 가진다"));
    }
}
