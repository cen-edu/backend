# Problem RAG Structured Authoring B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `ProblemSemanticModelV1` the validated source of truth for AI-authored middle-school mathematics problems so generation, natural-language edits, answers, explanations, and deterministic SVG assets remain synchronized.

**Architecture:** This branch is implemented only after plan A has merged. It imports A's `CurriculumScope`, adds a deterministic semantic validation/evaluation/materialization pipeline, stores semantic and render specifications beside authoring versions and finalized questions/assets, and routes teacher language through `AgentDispatcher(PROBLEM_EDIT)` into optimistic semantic patches. Semantic-capable questions use patch/materialize/render/verify; legacy or unsupported questions retain the current snapshot-merger path as an explicit fallback.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, Jackson, Spring Data JPA, PostgreSQL 17 JSONB, Flyway, deterministic Java SVG generation, JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers, ArchUnit, Gradle

**Spec:** `docs/superpowers/specs/2026-08-19-problem-rag-structured-authoring-design.md`

## Branch Synchronization Gate

This gate is mandatory and is the first execution task. Plan B must not create a second `CurriculumScope` or preserve `CurriculumContext` as a parallel semantic-authoring contract.

The post-A contract consumed by every B task is:

```java
package com.cenedu.backend.domain.problem.authoring.generation;

public record CurriculumScope(
        String curriculumRevision,
        String schoolLevel,
        int grade,
        Integer semester,
        String achievementStandardId,
        Long subUnitId,
        String majorUnitName,
        String middleUnitName,
        String subUnitName
) {}
```

`ProblemGenerationCommand` must expose it as `CurriculumScope curriculum`; B imports that accessor and does not rename it.

## Global Constraints

- Work only on branch `feat/backend-problem-edit-b` in `/Users/younder/Desktop/아이티센 AIE 부트캠프/999.프로젝트/0.최종프로젝트/cen-edu/.worktrees/backend-problem-edit-b`.
- Follow `AGENTS.md`; Problem domain contracts stay under `domain/problem/authoring`, user prompts cross `AgentDispatcher`, and system generation/extraction/verification use domain-owned ports.
- Do not add `ai.client`, `com.openai`, or `org.springframework.ai` references under `domain/problem..`.
- Do not duplicate or edit A's `CurriculumScope`; consume `com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope` exactly.
- Preserve `QuestionSnapshotV1` schema version 1 and all existing response fields. New preview fields are additive.
- Semantic schema version, diagram schema version, and patch schema version are exactly `1`.
- All semantic lists are non-null immutable copies. Logical keys match `[A-Z][A-Z0-9_]{0,63}` and are unique in their namespace.
- The fixed curriculum values are `curriculumRevision="2022_REVISED"`, `schoolLevel="MIDDLE"`, and `grade=1`.
- LLM-provided computation results, DB IDs, storage keys, schema versions, hashes, and renderer versions are never trusted; the server recomputes or supplies them.
- No arbitrary expression evaluator, SVG, HTML, CSS, JavaScript, external URL, or network call is allowed in semantic computation or rendering.
- `ProblemModificationSnapshotMerger` remains unchanged as the fallback implementation for semantic-disabled, extraction-unsupported, or legacy whole-replacement paths.
- Content and asset verification must both pass before a candidate becomes the current version; deterministic Java validation failure cannot be overridden by LLM verification.
- The only new migration is `src/main/resources/db/migration/V20260819_1000__problem_add_semantic_model.sql`; do not edit any applied migration.
- Feature flag `PROBLEM_SEMANTIC_AUTHORING_ENABLED` defaults to `false`; disabling it preserves the current generation/modification path.
- Tests do not call OpenAI or a network service. LLM and renderer ports are fakes or mocks.
- Use TDD in every implementation task: add the focused failing test, run it and observe the stated failure, add minimal production code, rerun to green, then commit.
- Repository and service methods added by this plan receive a one-line business-purpose comment as required by `AGENTS.md`.
- Do not log teacher input, problem text, answers, explanation text, system prompts, or serialized semantic models. Log IDs, lengths, hashes, enum statuses, latency, and exception class only.
- Baseline: `bash gradlew test` compiles but Spring context/live tests cascade-fail with `PlaceholderResolutionException` when `JWT_SECRET` is absent. Pure/unit tests are the normal RED/GREEN loop. Full verification injects a process-local non-secret value of at least 32 bytes and never writes it to `.env` or a tracked file.
- Every implementation commit uses `feat : backend - {Korean task summary}` and stages only files named by that task.

---

## File/Responsibility Map

| Area | Files | Single responsibility |
|---|---|---|
| Semantic contracts | `domain/problem/authoring/semantic/model/*.java` | One public record or enum per file; exact V1 source-of-truth vocabulary |
| Semantic validation | `domain/problem/authoring/semantic/validation/*.java` | Structural, unit/bounds, constraint, and assertion validation without materialization |
| Deterministic evaluation | `domain/problem/authoring/semantic/evaluation/*.java` | Exact rational arithmetic, DAG sorting, operation evaluation, normalized results |
| Materialization | `domain/problem/authoring/semantic/materialization/*.java`, `authoring/port/ProblemSemanticMaterializer.java` | Placeholder resolution and deterministic S1/asset-plan projection |
| Diagram contracts | `domain/problem/authoring/diagram/*.java` | Tagged V1 diagram families and semantic bindings only |
| Diagram rendering | `ai/problem/render/*.java`, `authoring/port/ProblemDiagramRendererPort.java` | Network-free deterministic SVG by diagram family |
| Persistence | fixed Flyway file, three entities, semantic codec/document | JSONB/hash/status storage and finalization mapping |
| Structured generation | `ai/problem/adapter/semantic/*.java` plus existing generation router | LLM semantic output, at most two repair retries, server materialization |
| Legacy extraction | semantic extraction contracts/service/adapter | On-demand conversion without changing the source snapshot on failure |
| Semantic editing | `authoring/edit/semantic/*.java` | Patch path grammar, classification, expected-old-value apply, sanitized diff |
| Agent integration | existing `ai/problem/agent` and edit DTO/gateway files | User-language classification through Dispatcher only |
| Execution/API | modification coordinator/worker, additive response DTOs/controller annotations | Confirmed patch/regeneration/fallback execution and teacher preview |

---

### Task 0: Synchronize with merged A and prove the consumed contract

**Files:**
- Verify only: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScope.java`
- Verify only: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationCommand.java`
- Verify only: A's retrieval/indexing implementation and migrations

**Interfaces:**
- Consumes: A's exact `CurriculumScope` record shown in the gate and `ProblemGenerationCommand.curriculum()`
- Produces: a clean B branch based on merged A; no B source change and no duplicate curriculum type

- [ ] **Step 1: Confirm the execution worktree and clean state**

Run:

```bash
pwd
git branch --show-current
git status --short
```

Expected: the path ends in `.worktrees/backend-problem-edit-b`, branch is `feat/backend-problem-edit-b`, and status is empty. Stop if any unrelated user change is present.

- [ ] **Step 2: Synchronize the branch after A is merged**

Run:

```bash
git fetch origin
git merge --no-edit origin/develop
```

Expected: merge succeeds and includes A's RAG contracts. If the team merged A into a different integration branch, stop and obtain the exact merged branch name; do not merge the unreviewed A feature branch by assumption.

- [ ] **Step 3: Verify the exact A contract and absence of duplicates**

Run:

```bash
test -f src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScope.java
rg -n "record CurriculumScope|String curriculumRevision|String schoolLevel|int grade|String achievementStandardId|Long subUnitId" src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScope.java
rg -n "CurriculumScope curriculum" src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationCommand.java
test "$(rg -l 'record CurriculumScope' src/main/java | wc -l | tr -d ' ')" = "1"
```

Expected: all commands exit 0 and the only definition is A's generation-package record. A signature drift is a branch-integration blocker, not permission to create a B copy.

- [ ] **Step 4: Establish compile and test baselines**

Run:

```bash
bash gradlew compileJava
bash gradlew test --tests '*ProblemGeneration*Test' --tests '*ProblemReference*Test'
```

Expected: A's production and focused tests pass without a Spring context. Record any A regression before beginning B.

- [ ] **Step 5: Do not create a task commit**

The merge commit, if Git creates one, is the synchronization record. There is no implementation commit for this gate.

---

### Task 1: Define the exact semantic model records and enums

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/ProblemSemanticModelV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticProblemIntent.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticParameter.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticValueType.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticNumericBounds.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticComputation.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticOperation.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticConstraint.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticConstraintType.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticPresentationPlan.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticChoiceTemplate.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticStepTemplate.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticSegmentTemplate.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticSegmentType.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticLearningGuideTemplate.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticRubricTemplate.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticAssertion.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model/SemanticAssertionType.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticResolvedValue.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/support/ProblemSemanticFixtures.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/model/ProblemSemanticModelV1Test.java`

**Interfaces:**
- Consumes: A's `CurriculumScope`, existing `QuestionType`, `EvaluationArea`, `CompareMethod`, and `DiagnosticType`
- Produces: the immutable semantic vocabulary used by every later task

Use these exact signatures:

```java
public record ProblemSemanticModelV1(int schemaVersion, CurriculumScope curriculum,
        SemanticProblemIntent intent, List<SemanticParameter> parameters,
        List<SemanticComputation> computations, List<SemanticConstraint> constraints,
        SemanticPresentationPlan presentation, List<DiagramSpecV1> diagrams,
        List<SemanticAssertion> assertions) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

public record SemanticProblemIntent(QuestionType questionType, String difficulty,
        EvaluationArea evaluationArea, String solutionStrategy, String targetKey,
        int expectedReasoningSteps, boolean visualRequired) {}

public record SemanticParameter(String key, SemanticValueType valueType, String value,
        String unit, boolean editable, SemanticNumericBounds bounds) {}

public record SemanticNumericBounds(String minInclusive, String maxInclusive) {}

public enum SemanticValueType { INTEGER, DECIMAL, RATIONAL, TEXT, POINT, BOOLEAN }

public record SemanticComputation(String key, SemanticOperation operation,
        List<String> operands, String literal, String unit, String result) {}

public enum SemanticOperation {
    IDENTITY, ADD, SUBTRACT, MULTIPLY, DIVIDE, NEGATE, ABS, POWER_INTEGER,
    SUM, PRODUCT, LINEAR_EVALUATE, DIRECT_PROPORTION, INVERSE_PROPORTION
}

public record SemanticConstraint(String key, SemanticConstraintType type,
        List<String> operands, String expectedValue, String message) {}

public enum SemanticConstraintType {
    INTEGER_ONLY, NON_ZERO, POSITIVE, NON_NEGATIVE, DISTINCT,
    LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
    SUM_EQUALS, TRIANGLE_INEQUALITY
}

public record SemanticPresentationPlan(String questionTemplate,
        List<SemanticChoiceTemplate> choices, List<SemanticStepTemplate> steps,
        String explanationTemplate, SemanticLearningGuideTemplate learningGuide,
        List<SemanticRubricTemplate> rubrics) {}

public record SemanticChoiceTemplate(String choiceKey, int displayOrder,
        String contentTemplate, String valueKey) {}

public record SemanticStepTemplate(String stepKey, int displayOrder,
        String labelTemplate, List<SemanticSegmentTemplate> segments) {}

public record SemanticSegmentTemplate(SemanticSegmentType type, String textTemplate,
        String unitKey, String valueKey, CompareMethod compareMethod,
        DiagnosticType diagnosticType, String displayUnitTemplate) {}

public enum SemanticSegmentType { TEXT, BLANK, ANSWER_REF }

public record SemanticLearningGuideTemplate(String conceptTitleTemplate,
        String summaryTemplate, List<String> keyPointTemplates) {}

public record SemanticRubricTemplate(String rubricKey, int displayOrder,
        String criterionTemplate, int weightPercent) {}

public record SemanticAssertion(String key, SemanticAssertionType type,
        String leftKey, String rightKey, String expectedValue) {}

public enum SemanticAssertionType {
    EQUALS, NOT_EQUALS, LESS_THAN, LESS_THAN_OR_EQUAL,
    GREATER_THAN, GREATER_THAN_OR_EQUAL, CHOICE_TARGET_EXISTS,
    RUBRIC_WEIGHT_SUM_EQUALS_100
}

public interface DiagramSpecV1 {
    int CURRENT_SCHEMA_VERSION = 1;
    int schemaVersion();
    String assetKey();
}

public record SemanticResolvedValue(SemanticValueType valueType,
        String canonicalValue, String unit) {}
```

`POINT` values use canonical `x,y`; `RATIONAL` values use canonical `numerator/denominator`; booleans use lowercase `true` or `false`. Compact constructors copy all lists with `List.copyOf`, replacing no null list silently: a null list throws `NullPointerException` so malformed provider output reaches validation/retry instead of acquiring hidden defaults. Fixture builders, not production constructors, supply empty lists.

- [x] **Step 1: Write the failing contract test** *(RED 단계는 사용자 지시에 따라 생략)*

```java
package com.cenedu.backend.domain.problem.authoring.semantic.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cenedu.backend.domain.problem.authoring.semantic.support.ProblemSemanticFixtures;
import org.junit.jupiter.api.Test;

class ProblemSemanticModelV1Test {
    @Test
    void exposesVersionOneAndCopiesLists() {
        var model = ProblemSemanticFixtures.radiusProblem();
        assertThat(model.schemaVersion()).isEqualTo(1);
        assertThat(model.parameters()).extracting(SemanticParameter::key)
                .containsExactly("RADIUS");
        assertThat(model.computations()).extracting(SemanticComputation::key)
                .containsExactly("DIAMETER");
    }

    @Test
    void rejectsNullCollectionsAtTheContractBoundary() {
        var valid = ProblemSemanticFixtures.radiusProblem();
        assertThatThrownBy(() -> new ProblemSemanticModelV1(1, valid.curriculum(),
                valid.intent(), null, valid.computations(), valid.constraints(),
                valid.presentation(), valid.diagrams(), valid.assertions()))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*ProblemSemanticModelV1Test'`

Expected: FAIL at `compileTestJava` because `ProblemSemanticModelV1` and fixture types do not exist.

- [x] **Step 3: Add the exact records/enums and a complete radius fixture**

The fixture returns a short-input radius problem with `RADIUS=3 cm`, `DIAMETER=MULTIPLY(RADIUS, literal 2)=6 cm`, target `DIAMETER`, question/explanation placeholders, no diagrams, and assertions `RADIUS > 0` and `DIAMETER = 6`. It uses A's scope values `2022_REVISED`, `MIDDLE`, grade `1`, sub-unit `1L`.

- [x] **Step 4: Run GREEN**

Run: `bash gradlew test --tests '*ProblemSemanticModelV1Test'`

Expected: PASS with two tests and no Spring context startup.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/model \
        src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecV1.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticResolvedValue.java \
        src/test/java/com/cenedu/backend/domain/problem/authoring/semantic
git commit -m "feat : backend - 문제 의미 모델 V1 계약 추가"
```

---

### Task 2: Validate semantic structure, curriculum, keys, bounds, constraints, and assertions

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/ProblemSemanticModelValidator.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/SemanticUnitAndBoundsValidator.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/SemanticConstraintValidator.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/SemanticAssertionValidator.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/SemanticValidationException.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/ProblemSemanticModelValidatorTest.java`

**Interfaces:**
- Consumes: Task 1 records and A's curriculum scope
- Produces: `void validate(ProblemSemanticModelV1 model)`, `List<String> violations(ProblemSemanticModelV1 model)`, and stable violation paths for LLM repair prompts

Exact validator method signatures are:

```text
ProblemSemanticModelValidator#validate(ProblemSemanticModelV1 model) -> void
ProblemSemanticModelValidator#violations(ProblemSemanticModelV1 model) -> List<String>
SemanticUnitAndBoundsValidator#appendDefinitionViolations(ProblemSemanticModelV1 model, List<String> violations) -> void
SemanticConstraintValidator#appendDefinitionViolations(ProblemSemanticModelV1 model, List<String> violations) -> void
SemanticConstraintValidator#validateResolved(List<SemanticConstraint> constraints, Map<String, SemanticResolvedValue> values) -> void
SemanticAssertionValidator#appendDefinitionViolations(ProblemSemanticModelV1 model, List<String> violations) -> void
SemanticAssertionValidator#validateResolved(ProblemSemanticModelV1 model, Map<String, SemanticResolvedValue> values) -> void
```

Task 2 validates constraint/assertion definitions and references. Task 3 calls the `validateResolved` methods only after the DAG has produced every computation value.

Validation decisions are exact:

- schema is `1`; curriculum fixed values are exact; `subUnitId` is positive; `achievementStandardId` may be null.
- parameter and computation keys share one namespace and may not duplicate; constraint, assertion, diagram asset, choice, step, unit, and rubric keys are unique in their own namespaces.
- `targetKey` exists in the parameter/computation namespace; every computation operand exists; every template/diagram semantic reference exists.
- `solutionStrategy` matches `[A-Z][A-Z0-9_]{0,63}`; difficulty is `low`, `mid`, or `high`; expected reasoning steps are 1 through 8.
- numeric bounds apply only to INTEGER/DECIMAL/RATIONAL, parse exactly, and satisfy min <= max; current value lies inside inclusive bounds.
- ADD/SUBTRACT/SUM operands have one identical unit. NEGATE/ABS/IDENTITY preserve the operand unit. Other operations require the declared output unit but may derive a different unit.
- `visualRequired=true` requires at least one diagram; false permits zero or more.
- MULTIPLE_CHOICE has 2 through 8 choices and exactly one `valueKey` equal to `intent.targetKey`; SHORT_INPUT has no choices/steps/rubrics; STEP_FILL has 1 through 4 steps and every BLANK has unique unit/value keys; ESSAY has 2 through 5 rubrics totaling 100 and a single materialized RUBRIC answer.
- Constraint operands follow their type arity: unary numeric for INTEGER_ONLY/NON_ZERO/POSITIVE/NON_NEGATIVE; binary for comparisons; two or more for DISTINCT/SUM_EQUALS/TRIANGLE_INEQUALITY. Triangle inequality receives exactly three positive values.
- Assertion keys are unique. Comparison assertions use `leftKey` and either `rightKey` or `expectedValue`, never both. CHOICE_TARGET_EXISTS and RUBRIC_WEIGHT_SUM_EQUALS_100 use neither comparison field.

- [x] **Step 1: Write the failing validator test** *(RED 단계는 사용자 지시에 따라 생략)*

```java
package com.cenedu.backend.domain.problem.authoring.semantic.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticComputation;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticOperation;
import com.cenedu.backend.domain.problem.authoring.semantic.support.ProblemSemanticFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProblemSemanticModelValidatorTest {
    private final ProblemSemanticModelValidator validator =
            new ProblemSemanticModelValidator(new SemanticUnitAndBoundsValidator(),
                    new SemanticConstraintValidator(), new SemanticAssertionValidator());

    @Test
    void reportsDuplicateKeyMissingOperandAndInvalidCurriculumTogether() {
        var base = ProblemSemanticFixtures.radiusProblem();
        var invalid = new com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1(
                1,
                new com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope(
                        "2015_REVISED", "MIDDLE", 2, 1, null, 1L, "수와 연산", "정수", "정수"),
                base.intent(), base.parameters(),
                List.of(new SemanticComputation("RADIUS", SemanticOperation.ADD,
                        List.of("MISSING", "RADIUS"), null, "cm", "4")),
                base.constraints(), base.presentation(), base.diagrams(), base.assertions());

        assertThatThrownBy(() -> validator.validate(invalid))
                .isInstanceOfSatisfying(SemanticValidationException.class, exception ->
                        assertThat(exception.violations())
                                .contains("curriculum.curriculumRevision: 2022_REVISED 이어야 합니다.")
                                .contains("curriculum.grade: 1 이어야 합니다.")
                                .contains("computations[0].key: RADIUS 키가 중복되었습니다.")
                                .contains("computations[0].operands[0]: MISSING 키가 존재하지 않습니다."));
    }
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*ProblemSemanticModelValidatorTest'`

Expected: FAIL at compilation because validator types do not exist.

- [x] **Step 3: Implement deterministic violation collection** *(기본 검증 범위 구현; 전체 명세 확장은 미완료)*

```java
package com.cenedu.backend.domain.problem.authoring.semantic.validation;

import java.util.List;

public final class SemanticValidationException extends IllegalArgumentException {
    private final List<String> violations;

    public SemanticValidationException(List<String> violations) {
        super(String.join("; ", List.copyOf(violations)));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
```

`ProblemSemanticModelValidator.violations` uses `ArrayList` in field order and never a hash-set iteration order, so a fixed invalid model produces a fixed repair summary. `validate` throws only after all four validators have appended their findings.

- [x] **Step 4: Add focused cases and run GREEN** *(기본 집중 테스트 통과; 전체 계획 케이스는 미완료)*

Add tests for invalid logical-key characters, duplicate keys, unknown target/operand, null list, out-of-bounds value, mixed ADD units, missing visual, rubric total, invalid constraint arity, and malformed assertion.

Run: `bash gradlew test --tests '*ProblemSemanticModelValidatorTest'`

Expected: PASS; test task does not resolve `JWT_SECRET` because no context starts.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation \
        src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/validation
git commit -m "feat : backend - 문제 의미 모델 결정적 검증 추가"
```

---

### Task 3: Evaluate the computation DAG with exact arithmetic

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticNumber.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticComputationGraph.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticComputationEngine.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticEvaluation.java`
- Verify only: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticResolvedValue.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticEvaluationException.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticComputationGraphTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation/SemanticComputationEngineTest.java`

**Interfaces:**
- Consumes: validated Task 1 model
- Produces: `List<SemanticComputation> topologicallySort(ProblemSemanticModelV1 model)` and `SemanticEvaluation evaluate(ProblemSemanticModelV1 model)`

Use these exact result signatures:

```java
public record SemanticEvaluation(ProblemSemanticModelV1 normalizedModel,
        Map<String, SemanticResolvedValue> values,
        List<String> topologicalOrder) {}

```

Exact public methods are `List<SemanticComputation> SemanticComputationGraph.topologicallySort(ProblemSemanticModelV1 model)` and `SemanticEvaluation SemanticComputationEngine.evaluate(ProblemSemanticModelV1 model)`.

`SemanticNumber` stores reduced `BigInteger numerator` and positive `BigInteger denominator`. It parses INTEGER, finite DECIMAL, and RATIONAL without floating point. Canonical output is an integer when denominator is 1 and otherwise `numerator/denominator`. POINT/TEXT/BOOLEAN are accepted as parameter values but only IDENTITY may copy them.

Operation contracts are exact:

| Operation | Operands | Literal | Calculation |
|---|---:|---|---|
| IDENTITY | 1 | null | operand 0 |
| ADD/SUBTRACT/MULTIPLY/DIVIDE | 2 | null | binary exact arithmetic; divisor non-zero |
| NEGATE/ABS | 1 | null | unary exact arithmetic |
| POWER_INTEGER | 1 | integer `-12..12` | exact integer power; zero with negative power fails |
| SUM/PRODUCT | 1..16 | null | left-to-right exact fold |
| LINEAR_EVALUATE | 3 | null | `slope * x + intercept` |
| DIRECT_PROPORTION | 2 | null | `k * x` |
| INVERSE_PROPORTION | 2 | null | `k / x`, x non-zero |

The graph uses Kahn's algorithm with declaration index as the tie-breaker. Parameter keys are roots. A missing operand fails validation; a remaining in-degree after sorting throws `SemanticEvaluationException` listing sorted cycle keys. `evaluate` always returns a new model whose computation `result` fields contain server values; it compares a nonblank provider result after canonicalization and reports a mismatch before returning.

- [x] **Step 1: Write failing cycle and operation tests** *(RED 단계는 사용자 지시에 따라 생략)*

```java
@Test
void rejectsCycleWithStableKeyList() {
    var model = ProblemSemanticFixtures.withComputations(List.of(
            computation("A", ADD, List.of("RADIUS", "B"), null, "cm", null),
            computation("B", ADD, List.of("RADIUS", "A"), null, "cm", null)));

    assertThatThrownBy(() -> new SemanticComputationGraph().topologicallySort(model))
            .isInstanceOfSatisfying(SemanticEvaluationException.class,
                    exception -> assertThat(exception.getMessage()).contains("A, B"));
}

@ParameterizedTest
@CsvSource({
        "ADD,2,3,,5", "SUBTRACT,2,3,,-1", "MULTIPLY,2,3,,6",
        "DIVIDE,2,3,,2/3", "POWER_INTEGER,2,,3,8",
        "DIRECT_PROPORTION,4,3,,12", "INVERSE_PROPORTION,4,3,,4/3"
})
void evaluatesExactOperations(SemanticOperation operation, String left, String right,
        String literal, String expected) {
    assertThat(engine.evaluate(ProblemSemanticFixtures.operationProblem(
            operation, left, right, literal)).values().get("RESULT").canonicalValue())
            .isEqualTo(expected);
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*SemanticComputationGraphTest' --tests '*SemanticComputationEngineTest'`

Expected: FAIL because evaluation classes do not exist.

- [x] **Step 3: Implement exact number, stable graph, and all 13 operations** *(기본 exact number/DAG와 일부 연산 구현; 전체 13개 연산은 미완료)*

The engine first calls `ProblemSemanticModelValidator.validate`, inserts parameter values in declaration order, evaluates the sorted computations, evaluates constraints and assertions against the final map, then constructs `normalizedModel` by replacing only each computation's `result`.

- [x] **Step 4: Run GREEN** *(기본 계산 DAG 테스트 통과; 전체 연산 테스트는 미완료)*

Run: `bash gradlew test --tests '*SemanticComputationGraphTest' --tests '*SemanticComputationEngineTest' --tests '*ProblemSemanticModelValidatorTest'`

Expected: PASS including division-by-zero, result-mismatch, cycle, all operations, constraint, and assertion cases.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation \
        src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/evaluation
git commit -m "feat : backend - 문제 계산 DAG와 정확 연산 엔진 추가"
```

---

### Task 4: Resolve placeholders and materialize deterministic snapshots

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemSemanticMaterializer.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/DefaultProblemSemanticMaterializer.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticTemplateEngine.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticPlaceholderValidator.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticSnapshotFactory.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/MaterializedProblem.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticMaterializationReport.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticMaterializationException.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticTemplateEngineTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/DefaultProblemSemanticMaterializerTest.java`

**Interfaces:**
- Consumes: Task 3 `SemanticEvaluation`, existing snapshot records and validators
- Produces: the spec contract `MaterializedProblem materialize(ProblemSemanticModelV1 model)`

```java
package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;

public interface ProblemSemanticMaterializer {
    MaterializedProblem materialize(ProblemSemanticModelV1 model);
}
```

```java
public record MaterializedProblem(QuestionSnapshotV1 snapshot,
        List<GeneratedAssetPlan> assetPlans,
        SemanticMaterializationReport report) {}

public record SemanticMaterializationReport(int semanticSchemaVersion,
        List<String> topologicalOrder, Map<String, String> resolvedValues,
        Set<String> placeholderKeys, Set<String> diagramAssetKeys) {}
```

Placeholder syntax is exactly `\$\{([A-Z][A-Z0-9_]{0,63})(?:_UNIT)?}`. `${KEY}` renders the canonical value; `${KEY_UNIT}` renders its unit or an empty string. Unknown keys, malformed placeholder tokens, and any remaining `${` fail. Parameter/computation values are escaped only at their eventual output sink; plain snapshot text remains text.

Materialization rules are exact:

- content block `CB1` is TEXT with the rendered question. This text/question-type task accepts only models with an empty diagram list; Task 5 adds the closed diagram families, asset blocks, and asset-plan projection as a separate reviewable unit.
- snapshot metadata uses intent question type/difficulty/evaluation area and curriculum sub-unit; presentation is `TEXT_ONLY`, `WITH_TABLE`, or `WITH_FIGURE`; topic and derived-from IDs are null.
- MULTIPLE_CHOICE choices preserve declared keys/order; the unique choice whose `valueKey` equals target becomes MAIN/CHOICE answer.
- SHORT_INPUT creates MAIN with the target value, VALUE compare method, and target unit.
- STEP_FILL maps TEXT/BLANK/ANSWER_REF segments. Every BLANK creates one answer unit from its `unitKey`, `valueKey`, compare method, diagnostic type, and rendered display unit.
- ESSAY creates MAIN with null raw/normalized answer and RUBRIC compare method; rubrics render and total 100.
- answer normalization is the canonical semantic value for VALUE/EXACT/SET; CHOICE stores the choice key; RUBRIC stores null.
- presentation-only patches must preserve the placeholder-key multiset for every changed template, not merely the union across the model.

- [x] **Step 1: Write failing placeholder and radius materialization tests** *(RED 단계는 사용자 지시에 따라 생략)*

```java
@Test
void rendersValueAndUnitAndRejectsUnknownPlaceholders() {
    Map<String, SemanticResolvedValue> values = Map.of(
            "RADIUS", new SemanticResolvedValue(SemanticValueType.INTEGER, "3", "cm"));
    assertThat(engine.render("반지름 ${RADIUS}${RADIUS_UNIT}", values))
            .isEqualTo("반지름 3cm");
    assertThatThrownBy(() -> engine.render("${MISSING}", values))
            .isInstanceOf(SemanticMaterializationException.class);
}

@Test
void radiusChangeRegeneratesStemAnswerAndExplanationFromOneValue() {
    MaterializedProblem result = materializer.materialize(
            ProblemSemanticFixtures.radiusProblem());
    assertThat(result.snapshot().contentBlocks().getFirst().text()).contains("3cm");
    assertThat(result.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("6");
    assertThat(result.snapshot().explanation()).contains("3 × 2 = 6cm");
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*SemanticTemplateEngineTest' --tests '*DefaultProblemSemanticMaterializerTest'`

Expected: FAIL because materialization classes do not exist.

- [x] **Step 3: Implement the template engine, type-specific snapshot factory, and materializer order** *(기본 텍스트/단답 물질화 구현; 전체 문항 유형은 미완료)*

`DefaultProblemSemanticMaterializer` executes: model validator, computation engine, assertion validation, placeholder validation, snapshot factory, `SnapshotStructuralValidator`, then `SnapshotNormalizedValidator`. It returns an empty asset-plan list for the accepted empty-diagram model. The server-normalized model is supplied to persistence by the caller through `SemanticEvaluation.normalizedModel`; the materialized report records its computed values and keys without answer text logging.

For this task's independently testable text-only boundary, `materialize` checks `model.diagrams().isEmpty()` before snapshot creation and throws `SemanticMaterializationException("DiagramSpecV1 family is not registered")` when non-empty. Task 5 replaces that branch with exhaustive closed-union validation and asset-plan creation in the same materializer.

- [x] **Step 4: Run GREEN** *(기본 placeholder 테스트 통과; 전체 물질화 테스트는 미완료)*

Run: `bash gradlew test --tests '*SemanticTemplateEngineTest' --tests '*DefaultProblemSemanticMaterializerTest' --tests '*SnapshotValidatorsTest'`

Expected: PASS for all four question types, unknown/remnant placeholders, per-template placeholder invariance, and radius synchronization.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemSemanticMaterializer.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization \
        src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization
git commit -m "feat : backend - 의미 모델 템플릿 물질화 추가"
```

---

### Task 5: Close, validate, and materialize all five DiagramSpecV1 families

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramKind.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramViewport.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramStyle.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/NumberLineDiagramSpecV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/NumberLinePointSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/NumberLineIntervalSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PointMarker.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/CoordinateGraphDiagramSpecV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/CoordinatePointSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/CoordinateSegmentSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/CoordinateLineSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/CoordinateFunctionSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/CoordinateFunctionKind.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlaneGeometryDiagramSpecV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlanePointSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlaneSegmentSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlaneAngleSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlanePolygonSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlaneCircleSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlaneArcSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/PlaneMeasurementSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/SolidGeometryDiagramSpecV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/SolidGeometryKind.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/SolidLabelSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DataTableDiagramSpecV1.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/TableCellSpec.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/TableCellAddress.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecValidator.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramValidationException.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticAssetPlanFactory.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/DefaultProblemSemanticMaterializer.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecValidatorTest.java`

**Interfaces:**
- Consumes: semantic key/value namespace from Tasks 1–3
- Produces: a closed tagged union that contains no SVG/HTML/CSS/script/URL field

Exact public methods are `void DiagramSpecValidator.validate(DiagramSpecV1 spec, Map<String, SemanticResolvedValue> values)` and `void DiagramSpecValidator.validateAll(List<DiagramSpecV1> specs, Map<String, SemanticResolvedValue> values)`.

Use this exact top-level contract and Jackson discriminator:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NumberLineDiagramSpecV1.class, name = "NUMBER_LINE"),
        @JsonSubTypes.Type(value = CoordinateGraphDiagramSpecV1.class, name = "COORDINATE_GRAPH"),
        @JsonSubTypes.Type(value = PlaneGeometryDiagramSpecV1.class, name = "PLANE_GEOMETRY"),
        @JsonSubTypes.Type(value = SolidGeometryDiagramSpecV1.class, name = "SOLID_GEOMETRY"),
        @JsonSubTypes.Type(value = DataTableDiagramSpecV1.class, name = "DATA_TABLE")
})
public sealed interface DiagramSpecV1 permits NumberLineDiagramSpecV1,
        CoordinateGraphDiagramSpecV1, PlaneGeometryDiagramSpecV1,
        SolidGeometryDiagramSpecV1, DataTableDiagramSpecV1 {
    int CURRENT_SCHEMA_VERSION = 1;
    int schemaVersion();
    String assetKey();
    DiagramKind kind();
    DiagramViewport viewport();
    DiagramStyle style();
}
```

Exact common and family signatures:

```java
public enum DiagramKind { NUMBER_LINE, COORDINATE_GRAPH, PLANE_GEOMETRY, SOLID_GEOMETRY, DATA_TABLE }
public record DiagramViewport(int width, int height, int padding) {}
public record DiagramStyle(String strokeColor, String fillColor, String accentColor,
        int strokeWidth, String fontFamily, int fontSize) {}

public record NumberLineDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind,
        DiagramViewport viewport, DiagramStyle style, String minKey, String maxKey,
        String tickIntervalKey, List<NumberLinePointSpec> points,
        List<NumberLineIntervalSpec> intervals, boolean startArrow, boolean endArrow)
        implements DiagramSpecV1 {}
public record NumberLinePointSpec(String pointKey, String positionKey,
        String labelTemplate, PointMarker marker) {}
public record NumberLineIntervalSpec(String intervalKey, String startKey, String endKey,
        boolean includeStart, boolean includeEnd, String labelTemplate) {}
public enum PointMarker { OPEN_CIRCLE, CLOSED_CIRCLE, CROSS }

public record CoordinateGraphDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind,
        DiagramViewport viewport, DiagramStyle style, String xMinKey, String xMaxKey,
        String yMinKey, String yMaxKey, String xTickKey, String yTickKey,
        List<CoordinatePointSpec> points, List<CoordinateSegmentSpec> segments,
        List<CoordinateLineSpec> lines, List<CoordinateFunctionSpec> functions)
        implements DiagramSpecV1 {}
public record CoordinatePointSpec(String pointKey, String xKey, String yKey,
        String labelTemplate, PointMarker marker) {}
public record CoordinateSegmentSpec(String segmentKey, String startPointKey,
        String endPointKey, String labelTemplate) {}
public record CoordinateLineSpec(String lineKey, String pointAKey, String pointBKey,
        boolean startArrow, boolean endArrow, String labelTemplate) {}
public record CoordinateFunctionSpec(String functionKey, CoordinateFunctionKind functionKind,
        String coefficientKey, String labelTemplate) {}
public enum CoordinateFunctionKind { DIRECT_PROPORTION, INVERSE_PROPORTION }

public record PlaneGeometryDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind,
        DiagramViewport viewport, DiagramStyle style, List<PlanePointSpec> points,
        List<PlaneSegmentSpec> segments, List<PlaneAngleSpec> angles,
        List<PlanePolygonSpec> polygons, List<PlaneCircleSpec> circles,
        List<PlaneArcSpec> arcs, List<PlaneMeasurementSpec> measurements)
        implements DiagramSpecV1 {}
public record PlanePointSpec(String pointKey, String xKey, String yKey, String labelTemplate) {}
public record PlaneSegmentSpec(String segmentKey, String startPointKey,
        String endPointKey, boolean startArrow, boolean endArrow) {}
public record PlaneAngleSpec(String angleKey, String vertexPointKey,
        String startPointKey, String endPointKey, String angleValueKey,
        String labelTemplate) {}
public record PlanePolygonSpec(String polygonKey, List<String> pointKeys,
        boolean filled, String labelTemplate) {}
public record PlaneCircleSpec(String circleKey, String centerPointKey,
        String radiusKey, String labelTemplate) {}
public record PlaneArcSpec(String arcKey, String centerPointKey, String radiusKey,
        String startAngleKey, String endAngleKey, String labelTemplate) {}
public record PlaneMeasurementSpec(String measurementKey, String targetKey,
        String valueKey, String labelTemplate) {}

public record SolidGeometryDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind,
        DiagramViewport viewport, DiagramStyle style, SolidGeometryKind solidKind,
        String widthKey, String depthKey, String heightKey, String radiusKey,
        String slantHeightKey, Integer polygonSides, List<SolidLabelSpec> labels)
        implements DiagramSpecV1 {}
public enum SolidGeometryKind { RECTANGULAR_PRISM, PRISM, PYRAMID, CYLINDER, CONE, SPHERE }
public record SolidLabelSpec(String labelKey, String valueKey, String labelTemplate) {}

public record DataTableDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind,
        DiagramViewport viewport, DiagramStyle style, List<String> rowHeaderTemplates,
        List<String> columnHeaderTemplates, List<TableCellSpec> cells,
        Set<TableCellAddress> highlightedCells) implements DiagramSpecV1 {}
public record TableCellSpec(int row, int column, String valueKey, String textTemplate) {}
public record TableCellAddress(int row, int column) {}
```

Validation limits: viewport 240–1200 by 120–900, padding 8–96; font family is `sans-serif` only; colors are `#[0-9A-F]{6}` uppercase; stroke 1–8; font 10–32; labels are at most 80 Unicode code points and match letters/numbers/Korean/space/math punctuation `[-+−×÷=().,°%/:]`; no control character. All referenced semantic keys and plane/coordinate point keys exist. Ranges/ticks are positive and contain zero where axes require it. Tables are rectangular, at most 12x12, and have one cell per coordinate. Solid kinds enforce required dimensions: prism/pyramid width+depth+height, cylinder radius+height, cone radius+height+slant, sphere radius.

- [x] **Step 1: Write failing family and injection tests** *(RED 단계는 사용자 지시에 따라 생략하고 구현 후 집중 테스트로 검증)*

```java
@Test
void acceptsEveryClosedDiagramFamily() {
    var model = ProblemSemanticFixtures.diagramCoverageProblem();
    assertThatCode(() -> validator.validateAll(model.diagrams(), evaluation.values()))
            .doesNotThrowAnyException();
    assertThat(model.diagrams()).extracting(DiagramSpecV1::kind)
            .containsExactly(NUMBER_LINE, COORDINATE_GRAPH, PLANE_GEOMETRY,
                    SOLID_GEOMETRY, DATA_TABLE);
}

@Test
void rejectsExternalMarkupInLabels() {
    var bad = ProblemSemanticFixtures.numberLineWithLabel("<script src=https://x>");
    assertThatThrownBy(() -> validator.validate(bad, ProblemSemanticFixtures.values()))
            .isInstanceOf(DiagramValidationException.class);
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*DiagramSpecValidatorTest'`

Expected: FAIL because the base interface is not yet a closed tagged union and the family validator does not exist.

- [x] **Step 3: Implement the closed records and validator**

The validator dispatches with an exhaustive Java 21 pattern switch. It reads values only from `Map<String, SemanticResolvedValue>` and never parses provider-supplied pixel coordinates or raw markup. `SemanticAssetPlanFactory` appends diagram content blocks in declaration order as `CB2`, `CB3`; DATA_TABLE uses TABLE and safe server markup, while other kinds use FIGURE and `assetRef=assetKey`. It creates one typed `GeneratedAssetPlan` per diagram and the materializer reruns snapshot validators after adding those blocks.

- [x] **Step 4: Run GREEN**

Run: `bash gradlew test --tests '*DiagramSpecValidatorTest' --tests '*ProblemSemanticModelValidatorTest' --tests '*DefaultProblemSemanticMaterializerTest'`

Expected: PASS for five valid families and invalid label, range, point reference, table shape, and solid-dimension cases.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/diagram \
        src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticAssetPlanFactory.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/DefaultProblemSemanticMaterializer.java \
        src/test/java/com/cenedu/backend/domain/problem/authoring/diagram
git commit -m "feat : backend - 문제 도식 명세 V1 계약 추가"
```

---

### Task 6: Render deterministic sanitized SVG for all diagram families

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemDiagramRendererPort.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramRenderContext.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/RenderedDiagram.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/ProblemDiagramRenderer.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/NumberLineSvgRenderer.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/CoordinateGraphSvgRenderer.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/PlaneGeometrySvgRenderer.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/SolidGeometrySvgRenderer.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/DataTableSvgRenderer.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/DeterministicSvgWriter.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/DeterministicLabelLayout.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/DiagramRenderException.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/render/ProblemRendererVersion.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/SafeSvgSanitizer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/asset/AssetGenerationSpecification.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/render/ProblemDiagramRendererTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/SafeSvgSanitizerTest.java`
- Create: `src/test/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapterTest.java`

**Interfaces:**
- Consumes: validated DiagramSpecV1 and evaluated semantic values
- Produces: the required rendering port and stable SHA-256 after strict sanitizer validation

```java
public interface ProblemDiagramRendererPort {
    RenderedDiagram render(DiagramSpecV1 spec, DiagramRenderContext context);
}

public record DiagramRenderContext(Map<String, SemanticResolvedValue> values) {
    public DiagramRenderContext { values = Map.copyOf(values); }
}

public record RenderedDiagram(String assetKey, String svg, String sha256,
        int widthPx, int heightPx, String rendererVersion) {}

public final class ProblemRendererVersion {
    public static final String CURRENT = "semantic-svg-v1";
    private ProblemRendererVersion() {}
}
```

Change `AssetGenerationSpecification` to this exact additive signature so legacy plans keep `renderData` while semantic plans carry the typed spec:

```java
public record AssetGenerationSpecification(int schemaVersion, String visualDescription,
        List<String> requiredElements, List<String> forbiddenElements,
        Map<String, Object> renderData, DiagramSpecV1 diagramSpec) {}
```

Provide a five-argument compatibility constructor that delegates with `diagramSpec=null`. Semantic asset plans use empty `renderData` and non-null `diagramSpec`. `LocalDraftAssetProductionAdapter` delegates typed plans to `ProblemDiagramRendererPort`; only null-spec legacy plans use the existing text placeholder renderer. Hash the UTF-8 bytes after sanitizer validation.

Rendering decisions:

- use integer viewport and a mandatory `viewBox="0 0 width height"`; format calculated coordinates to three decimals with `RoundingMode.HALF_UP`, strip trailing zeros, and normalize `-0` to `0`.
- emit elements and attributes in fixed code order; never iterate a HashMap/HashSet directly.
- scale mathematical coordinates server-side from evaluated values. Clip function paths to the plot rectangle. Split inverse-proportion paths at x=0.
- solids use a fixed 30-degree oblique projection and dashed hidden edges; no perspective randomness.
- labels try offsets `(0,-12)`, `(8,-8)`, `(8,12)`, `(0,16)`, `(-8,12)`, `(-8,-8)`, `(16,0)`, `(-16,0)` in that order. If all bounding boxes overlap or leave the viewport, throw `DiagramRenderException`.
- sanitizer parses XML with DTD and external entities disabled. Allowed elements are `svg,g,line,rect,circle,path,polyline,polygon,text,tspan,defs,marker`; allowed attributes are the fixed renderer attributes. Reject rather than remove a forbidden node/attribute so semantic elements cannot disappear silently.

- [x] **Step 1: Write failing determinism and sanitizer tests** *(RED 단계는 사용자 지시에 따라 생략)*

```java
@Test
void identicalSpecAndVersionProduceIdenticalSvgAndHash() {
    var spec = ProblemSemanticFixtures.numberLine();
    var context = new DiagramRenderContext(ProblemSemanticFixtures.values());
    RenderedDiagram first = renderer.render(spec, context);
    RenderedDiagram second = renderer.render(spec, context);
    assertThat(second.svg()).isEqualTo(first.svg());
    assertThat(second.sha256()).isEqualTo(first.sha256());
    assertThat(first.svg()).contains("viewBox=\"0 0 640 180\"");
}

@Test
void rejectsForeignObjectEventAndExternalReference() {
    assertThatThrownBy(() -> sanitizer.sanitize("<svg><foreignObject/></svg>"))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sanitizer.sanitize("<svg><path onload=\"x()\"/></svg>"))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sanitizer.sanitize("<svg><image href=\"https://x\"/></svg>"))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*ProblemDiagramRendererTest' --tests '*SafeSvgSanitizerTest'`

Expected: FAIL because typed renderers do not exist and the current regex sanitizer does not enforce an XML allowlist.

- [x] **Step 3: Implement common writer/layout, five renderers, sanitizer, and local adapter delegation** *(5종 family semantic 렌더링 및 sanitizer 허용 요소 보강 완료)*

Each family renderer has only geometry/layout logic. `ProblemDiagramRenderer` validates, dispatches, sanitizes, hashes, and constructs `RenderedDiagram`. No renderer writes files or calls the network.

- [x] **Step 4: Run GREEN** *(5종 family 상세 테스트와 기존 renderer/sanitizer 회귀 테스트 통과)*

Run: `bash gradlew test --tests '*ProblemDiagramRendererTest' --tests '*SafeSvgSanitizerTest' --tests '*LocalDraftAssetProductionAdapterTest'`

Expected: PASS for each family, repeated hash equality, label-overlap failure, malicious SVG rejection, and atomic local artifact creation.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemDiagramRendererPort.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramRenderContext.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/RenderedDiagram.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/asset/AssetGenerationSpecification.java \
        src/main/java/com/cenedu/backend/ai/problem/render \
        src/main/java/com/cenedu/backend/ai/problem/adapter/SafeSvgSanitizer.java \
        src/main/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapter.java \
        src/test/java/com/cenedu/backend/ai/problem/render \
        src/test/java/com/cenedu/backend/ai/problem/adapter/SafeSvgSanitizerTest.java \
        src/test/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapterTest.java
git commit -m "feat : backend - 결정적 문제 SVG 렌더러 추가"
```

---

### Task 7: Persist semantic models and reproducible render specifications

**Files:**
- Create: `src/main/resources/db/migration/V20260819_1000__problem_add_semantic_model.sql`
- Create: `src/main/java/com/cenedu/backend/domain/problem/entity/enums/SemanticModelStatus.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/persistence/SemanticModelDocument.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/persistence/RenderSpecDocument.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/persistence/ProblemSemanticDocumentCodec.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/entity/ProblemAuthoringVersion.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/entity/ProblemQuestion.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/entity/ProblemAsset.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/candidate/ProblemCandidateDraft.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringJsonCodec.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/support/ProblemQuestionFixtures.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/persistence/ProblemSemanticDocumentCodecTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/entity/ProblemSemanticPersistenceStateTest.java`

**Interfaces:**
- Consumes: server-normalized semantic model and DiagramSpecV1
- Produces: canonical JSON/hash documents and nullable-compatible entity storage

Use these exact records and candidate extension:

```java
public record SemanticModelDocument(int schemaVersion, String json, String sha256) {}
public record RenderSpecDocument(int schemaVersion, String json, String sha256,
        String rendererVersion) {}

public record ProblemCandidateDraft(UUID requestId, QuestionSnapshotV1 snapshot,
        List<GeneratedAssetPlan> assetPlans, ProblemSemanticModelV1 semanticModel,
        CandidateProvenance provenance) {
    public static ProblemCandidateDraft legacy(UUID requestId, QuestionSnapshotV1 snapshot,
            List<GeneratedAssetPlan> plans, CandidateProvenance provenance) {
        return new ProblemCandidateDraft(requestId, snapshot, plans, null, provenance);
    }
}
```

The codec exposes:

```java
SemanticModelDocument semanticModel(ProblemSemanticModelV1 normalizedModel);
RenderSpecDocument renderSpec(DiagramSpecV1 spec, String rendererVersion);
ProblemSemanticModelV1 readSemanticModel(String json);
DiagramSpecV1 readRenderSpec(String json);
```

It uses a private copied Jackson mapper configured for alphabetical object properties and map entries, UTF-8, and no pretty printing. SHA-256 is lowercase 64-character hex over canonical JSON bytes.

The migration content is complete and fixed:

```sql
ALTER TABLE problem_authoring_version
    ADD COLUMN semantic_model_schema_version SMALLINT,
    ADD COLUMN semantic_model JSONB,
    ADD COLUMN semantic_model_hash VARCHAR(64),
    ADD CONSTRAINT ck_problem_authoring_version_semantic_json
        CHECK (semantic_model IS NULL OR jsonb_typeof(semantic_model) = 'object'),
    ADD CONSTRAINT ck_problem_authoring_version_semantic_tuple
        CHECK ((semantic_model_schema_version IS NULL AND semantic_model IS NULL AND semantic_model_hash IS NULL)
            OR (semantic_model_schema_version = 1 AND semantic_model IS NOT NULL
                AND semantic_model_hash ~ '^[0-9a-f]{64}$'));

ALTER TABLE problem_question
    ADD COLUMN semantic_model_schema_version SMALLINT,
    ADD COLUMN semantic_model JSONB,
    ADD COLUMN semantic_model_hash VARCHAR(64),
    ADD COLUMN semantic_model_status VARCHAR(20) NOT NULL DEFAULT 'ABSENT',
    ADD CONSTRAINT ck_problem_question_semantic_status
        CHECK (semantic_model_status IN ('ABSENT', 'READY', 'UNSUPPORTED', 'FAILED')),
    ADD CONSTRAINT ck_problem_question_semantic_json
        CHECK (semantic_model IS NULL OR jsonb_typeof(semantic_model) = 'object'),
    ADD CONSTRAINT ck_problem_question_semantic_tuple
        CHECK ((semantic_model_status = 'READY' AND semantic_model_schema_version = 1
                AND semantic_model IS NOT NULL AND semantic_model_hash ~ '^[0-9a-f]{64}$')
            OR (semantic_model_status <> 'READY' AND semantic_model_schema_version IS NULL
                AND semantic_model IS NULL AND semantic_model_hash IS NULL));

ALTER TABLE problem_asset
    ADD COLUMN render_spec_schema_version SMALLINT,
    ADD COLUMN render_spec JSONB,
    ADD COLUMN render_spec_hash VARCHAR(64),
    ADD COLUMN renderer_version VARCHAR(30),
    ADD CONSTRAINT ck_problem_asset_render_spec_json
        CHECK (render_spec IS NULL OR jsonb_typeof(render_spec) = 'object'),
    ADD CONSTRAINT ck_problem_asset_render_spec_tuple
        CHECK ((render_spec_schema_version IS NULL AND render_spec IS NULL
                AND render_spec_hash IS NULL AND renderer_version IS NULL)
            OR (render_spec_schema_version = 1 AND render_spec IS NOT NULL
                AND render_spec_hash ~ '^[0-9a-f]{64}$' AND renderer_version IS NOT NULL));
```

Entity methods are exact:

- `ProblemAuthoringVersion.create(Long sessionId, int versionNo, Long parentVersionId, UUID sourceRequestId, AuthoringOperationType operationType, Long sourceQuestionId, int snapshotSchemaVersion, String snapshot, SemanticModelDocument semanticModel, String assetManifest, String changeSummary)` stores nullable semantic fields; retain the current 11-argument factory as a delegating legacy overload that supplies null for `semanticModel`.
- `ProblemAuthoringVersion.attachSemanticModel(SemanticModelDocument document)` may fill a null semantic tuple even on a PASSED legacy version because lazy extraction must not rewrite or re-verify its snapshot. Repeating the same hash is idempotent; replacing a non-null tuple with a different hash throws `IllegalStateException`.
- `ProblemQuestion.attachSemanticModel(SemanticModelDocument document)`, `markSemanticModelUnsupported()`, and `markSemanticModelFailed()` enforce the status/tuple invariant.
- `ProblemAsset.attachRenderSpec(RenderSpecDocument document)` sets all four render columns together.

- [x] **Step 1: Write canonical-hash and entity-state tests** *(사용자 지정 방식에 따라 RED 단계는 생략)*

```java
@Test
void semanticallyIdenticalModelsHaveOneCanonicalHash() {
    SemanticModelDocument first = codec.semanticModel(ProblemSemanticFixtures.radiusProblem());
    SemanticModelDocument second = codec.semanticModel(ProblemSemanticFixtures.radiusProblem());
    assertThat(second.json()).isEqualTo(first.json());
    assertThat(second.sha256()).matches("[0-9a-f]{64}").isEqualTo(first.sha256());
}

@Test
void questionStatusChangesAtomicallyWithSemanticTuple() {
    ProblemQuestion question = ProblemQuestionFixtures.imported();
    question.attachSemanticModel(codec.semanticModel(ProblemSemanticFixtures.radiusProblem()));
    assertThat(question.getSemanticModelStatus()).isEqualTo(SemanticModelStatus.READY);
    assertThat(question.getSemanticModelHash()).hasSize(64);
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*ProblemSemanticDocumentCodecTest' --tests '*ProblemSemanticPersistenceStateTest'`

Expected: FAIL because documents, fields, and status methods do not exist.

- [x] **Step 3: Add the fixed migration, codec, candidate field, and entity invariants**

Update every existing four-argument `ProblemCandidateDraft` construction to call `ProblemCandidateDraft.legacy(requestId, snapshot, plans, provenance)` unless that path already has a server-normalized semantic model. Do not invent a semantic model for imported/bank-reuse snapshots.

- [x] **Step 4: Run GREEN and migration validation** *(Task7 순수·candidate·JPA 및 전체 테스트 통과)*

Run:

```bash
bash gradlew test --tests '*ProblemSemanticDocumentCodecTest' --tests '*ProblemSemanticPersistenceStateTest' --tests '*ProblemCandidate*Test'
docker compose up -d db
JWT_SECRET='test-only-jwt-secret-value-at-least-32-bytes' bash gradlew test --tests '*Flyway*Test' --tests '*Jpa*Test'
```

Expected: pure tests PASS; database-backed migration/JPA tests PASS with all three new tuple constraints. If the compose service is named `postgres` after A, use `docker compose config --services` and run that exact service instead of adding a second database service.

- [x] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V20260819_1000__problem_add_semantic_model.sql \
        src/main/java/com/cenedu/backend/domain/problem/entity \
        src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/persistence \
        src/main/java/com/cenedu/backend/domain/problem/authoring/candidate/ProblemCandidateDraft.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringJsonCodec.java \
        src/test/java/com/cenedu/backend/domain/problem/support/ProblemQuestionFixtures.java \
        src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/persistence \
        src/test/java/com/cenedu/backend/domain/problem/entity/ProblemSemanticPersistenceStateTest.java
git commit -m "feat : backend - 의미 모델과 렌더 명세 영속화 추가"
```

---

### Task 8: Generate semantic models first and materialize them server-side

**Files:**
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPromptFactory.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPipeline.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticOutputParser.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/LegacyProblemGenerationPipeline.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/SemanticAuthoringProperties.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/SemanticAuthoringConfig.java`
- Create: `src/main/resources/ai/problem/problem-semantic-model-v1.schema.json`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemas.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPipelineTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapterTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemasTest.java`

**Interfaces:**
- Consumes: A's generation command/references, `LlmClient.completeStructured`, Tasks 2–7
- Produces: semantic-first `ProblemCandidateDraft` or the unchanged legacy candidate when the feature flag is off

Configuration contract:

```java
@ConfigurationProperties(prefix = "app.problem-authoring.semantic")
public record SemanticAuthoringProperties(@DefaultValue("false") boolean enabled) {}
```

Add exactly:

```yaml
app:
  problem-authoring:
    semantic:
      enabled: ${PROBLEM_SEMANTIC_AUTHORING_ENABLED:false}
```

and `.env.example` line `#PROBLEM_SEMANTIC_AUTHORING_ENABLED=false` with a comment that off preserves legacy generation/editing.

`ProblemStructuredOutputSchemas.SEMANTIC_MODEL` loads the UTF-8 classpath resource once. The checked-in schema has `additionalProperties:false` at every object, requires every Task 1 field, permits only operation/constraint/assertion/diagram enum values, and expresses the five diagram payloads through `oneOf` with the `kind` discriminator. `ProblemStructuredOutputSchemasTest` parses it and recursively asserts every object has `additionalProperties:false`.

The complete routing class is:

```java
package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.ai.problem.adapter.semantic.LegacyProblemGenerationPipeline;
import com.cenedu.backend.ai.problem.adapter.semantic.ProblemSemanticGenerationPipeline;
import com.cenedu.backend.ai.problem.adapter.semantic.SemanticAuthoringProperties;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import org.springframework.stereotype.Component;

@Component
public final class SpringAiProblemGenerationAdapter implements ProblemGenerationPort {
    private final SemanticAuthoringProperties properties;
    private final ProblemSemanticGenerationPipeline semanticPipeline;
    private final LegacyProblemGenerationPipeline legacyPipeline;

    public SpringAiProblemGenerationAdapter(SemanticAuthoringProperties properties,
            ProblemSemanticGenerationPipeline semanticPipeline,
            LegacyProblemGenerationPipeline legacyPipeline) {
        this.properties = properties;
        this.semanticPipeline = semanticPipeline;
        this.legacyPipeline = legacyPipeline;
    }

    @Override
    public ProblemCandidateDraft generate(ProblemGenerationCommand command) {
        return properties.enabled()
                ? semanticPipeline.generate(command)
                : legacyPipeline.generate(command);
    }
}
```

`ProblemSemanticGenerationPipeline.generate` performs at most three LLM calls: initial plus two repair attempts. For each response it parses V1, overwrites curriculum from `command.curriculum()` and schema version from the server, validates, evaluates into a normalized model, materializes, then returns a candidate with the normalized model. On deterministic failure, the next system prompt adds at most ten violation paths/messages, each truncated to 200 characters; it does not include answer values or previous raw JSON. After the third failure it throws `SemanticGenerationException`. Candidate provenance remains `AI_GENERATE` and uses A's reference IDs.

Exact public methods are `ProblemCandidateDraft ProblemSemanticGenerationPipeline.generate(ProblemGenerationCommand command)`, `ProblemCandidateDraft LegacyProblemGenerationPipeline.generate(ProblemGenerationCommand command)`, `ProblemSemanticModelV1 ProblemSemanticOutputParser.parse(String json)`, and `String ProblemSemanticGenerationPromptFactory.create(ProblemGenerationCommand command, List<String> repairFindings)`.

- [x] **Step 1: Write two-stage, retry, schema, and routing tests** *(사용자 지정 방식에 따라 RED 단계는 생략)*

```java
@Test
void returnsServerMaterializedCandidateWithNormalizedSemanticModel() {
    when(client.completeStructured(any(), any(), eq(ProblemStructuredOutputSchemas.SEMANTIC_MODEL)))
            .thenReturn(response(ProblemSemanticFixtures.radiusJsonWithWrongResult("999")))
            .thenReturn(response(ProblemSemanticFixtures.radiusJsonWithWrongResult("6")));

    ProblemCandidateDraft candidate = pipeline.generate(command());

    assertThat(candidate.semanticModel()).isNotNull();
    assertThat(candidate.semanticModel().computations().getFirst().result()).isEqualTo("6");
    assertThat(candidate.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("6");
    verify(client, times(2)).completeStructured(any(), any(), any());
}

@Test
void disabledFlagUsesCurrentLegacyPipeline() {
    when(legacy.generate(command)).thenReturn(legacyCandidate);
    assertThat(router.generate(command)).isSameAs(legacyCandidate);
    verifyNoInteractions(semantic);
}
```

- [x] **Step 2: Run RED** *(사용자 지정 방식에 따라 생략)*

Run: `bash gradlew test --tests '*ProblemSemanticGenerationPipelineTest' --tests '*SpringAiProblemGenerationAdapterTest' --tests '*ProblemStructuredOutputSchemasTest'`

Expected: FAIL because semantic pipeline/config/schema do not exist.

- [x] **Step 3: Extract the current adapter body into the legacy pipeline and implement semantic generation**

Prompt order is stable: static authoring rules, schema/version rules, A's actual few-shot JSON, current generation request, then repair findings if present. The prompt prohibits direct copying, unsupported operations, free-form SVG, and out-of-curriculum content. It requests null-free lists and server-managed identifiers omitted.

- [x] **Step 4: Run GREEN** *(semantic schema, legacy routing, worker, prompt, and Spring context tests passed)*

Run: `bash gradlew test --tests '*ProblemSemanticGenerationPipelineTest' --tests '*SpringAiProblemGenerationAdapterTest' --tests '*ProblemStructuredOutputSchemasTest' --tests '*ProblemGenerationWorkerTest'`

Expected: PASS for enabled semantic generation, two repair retries, retry exhaustion, disabled legacy routing, and existing worker behavior.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/ai/problem/adapter/semantic \
        src/main/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapter.java \
        src/main/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemas.java \
        src/main/resources/ai/problem/problem-semantic-model-v1.schema.json \
        src/main/resources/application.yaml .env.example \
        src/test/java/com/cenedu/backend/ai/problem
git commit -m "feat : backend - 의미 모델 우선 문제 생성 추가"
```

---

### Task 9: Store semantic candidates and require deterministic, content, and asset verification

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSnapshotEntityMapper.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemQuestionPersistenceBundle.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/verification/ProblemVerificationRequest.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/verification/GenerationVerificationContext.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapter.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingServiceTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationServiceTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSnapshotEntityMapperTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapterTest.java`

**Interfaces:**
- Consumes: Task 7 semantic/render documents, existing content/asset verification ports
- Produces: immutable semantic authoring versions and finalized READY questions/assets only after every required check passes

Extend `ProblemVerificationRequest` additively:

```java
public record ProblemVerificationRequest(UUID verificationRequestId,
        VerificationScope scope, VerificationOperationType operationType,
        ProblemCandidateDraft candidate, DraftAssetManifest assetManifest,
        VerificationExpectation expectation, VerificationContext context,
        SemanticMaterializationReport semanticReport) {}
```

`ProblemCandidateProcessingService.validateRequest` order is exact:

1. required request/candidate/provenance fields;
2. if `candidate.semanticModel()!=null`, semantic validator and computation engine;
3. re-materialize the normalized semantic model and require equality of snapshot and asset plans with the candidate;
4. existing snapshot structural and normalized validators;
5. snapshot/plan key equality;
6. DiagramSpec validator for every typed asset plan;
7. register version with `SemanticModelDocument`;
8. render assets;
9. verify CONTENT;
10. verify ASSET when plans are non-empty;
11. promote only merged PASSED.

If semantic deterministic validation fails, no Version is saved and `ProblemVerificationPort` is not called. If rendering/sanitization fails, the Version remains in history with asset ERROR and does not promote. The existing LLM content verifier receives the normalized model only as a non-logged consistency aid; it cannot turn a deterministic failure into PASSED.

Finalization decisions:

- AI_GENERATE and AI_MODIFY require non-null semantic fields when semantic authoring is enabled; legacy candidates created while disabled remain nullable-compatible.
- `ProblemSnapshotEntityMapper.map` gains `SemanticModelDocument semanticModel` and `Map<String, RenderSpecDocument> renderSpecs` arguments. It attaches the semantic document to `ProblemQuestion` and the matching render document to each typed `ProblemAsset`.
- every snapshot asset key must have one manifest artifact and one plan; a plan with `diagramSpec()!=null` must also have one render document whose hash equals canonical DiagramSpec JSON and renderer version equals `semantic-svg-v1`.
- final question status is READY only for a stored model; legacy final questions remain ABSENT.

- [ ] **Step 1: Add failing short-circuit and finalization tests**

```java
@Test
void deterministicSemanticFailureNeverCallsIndependentVerification() {
    CandidateProcessingRequest request = requestWith(
            ProblemSemanticFixtures.cyclicCandidate());

    assertThatThrownBy(() -> service.process(request))
            .isInstanceOf(SemanticEvaluationException.class);
    verifyNoInteractions(verificationPort);
    verify(versionRepository, never()).saveAndFlush(any());
}

@Test
void finalizationCopiesSemanticAndRenderDocumentsToQuestionAndAsset() {
    var result = service.finalizeForWorksheet(7L, List.of(31L));
    assertThat(result).hasSize(1);
    assertThat(savedQuestion.get().getSemanticModelStatus()).isEqualTo(SemanticModelStatus.READY);
    assertThat(savedAsset.get().getRendererVersion()).isEqualTo("semantic-svg-v1");
    assertThat(savedAsset.get().getRenderSpecHash()).hasSize(64);
}
```

- [ ] **Step 2: Run RED**

Run: `bash gradlew test --tests '*ProblemCandidateProcessingServiceTest' --tests '*ProblemAuthoringFinalizationServiceTest' --tests '*ProblemSnapshotEntityMapperTest'`

Expected: FAIL because candidate processing ignores semantic data and finalization does not persist semantic/render documents.

- [ ] **Step 3: Implement deterministic short-circuit, request report, and finalization propagation**

Retain the current transaction boundaries: no LLM, rendering, or storage I/O inside registration/finalization DB transactions. The renderer runs through the existing asset production phase; finalization only copies canonical render metadata and schedules existing storage tasks.

- [ ] **Step 4: Run GREEN**

Run: `bash gradlew test --tests '*ProblemCandidateProcessingServiceTest' --tests '*ProblemAuthoringFinalizationServiceTest' --tests '*ProblemSnapshotEntityMapperTest' --tests '*ProblemVerificationAdapterTest'`

Expected: PASS for semantic short-circuit, content-only, content+asset, asset failure, semantic persistence, legacy null compatibility, and finalized render provenance.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingService.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemSnapshotEntityMapper.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemQuestionPersistenceBundle.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/verification \
        src/main/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapter.java \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingServiceTest.java \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationServiceTest.java \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemSnapshotEntityMapperTest.java \
        src/test/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapterTest.java
git commit -m "feat : backend - 의미 문제 내용 자산 검증과 최종화 연결"
```

---

### Task 10: Lazily extract semantic models for legacy questions

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/extraction/SemanticExtractionCommand.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/extraction/SemanticExtractionResult.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/extraction/SemanticExtractionStatus.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemSemanticExtractionPort.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticExtractionService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionAdapter.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationReference.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/repository/ProblemQuestionRepository.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticExtractionServiceTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricherTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionAdapterTest.java`

**Interfaces:**
- Consumes: legacy QuestionSnapshotV1, A's CurriculumScope, system LLM client path
- Produces: persisted READY/UNSUPPORTED/FAILED extraction state without changing the source snapshot

Exact contracts:

```java
public record SemanticExtractionCommand(UUID requestId, Long questionId,
        CurriculumScope curriculum, QuestionSnapshotV1 snapshot) {}

public enum SemanticExtractionStatus {
    EXTRACTED, UNSUPPORTED, INVALID_SOURCE, TECHNICAL_ERROR
}

public record SemanticExtractionResult(SemanticExtractionStatus status,
        ProblemSemanticModelV1 semanticModel, List<String> findings) {
    public SemanticExtractionResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}

public interface ProblemSemanticExtractionPort {
    SemanticExtractionResult extract(SemanticExtractionCommand command);
}

public record GenerationReference(GenerationReferenceRole role, Long sourceQuestionId,
        QuestionSnapshotV1 snapshot, ProblemSemanticModelV1 semanticModel) {
    public GenerationReference(GenerationReferenceRole role, Long sourceQuestionId,
            QuestionSnapshotV1 snapshot) {
        this(role, sourceQuestionId, snapshot, null);
    }
}
```

`ProblemSemanticExtractionService` methods:

```java
SemanticExtractionResult ensureVersionSemantic(long ownerTeacherId,
        long sessionId, long versionId, CurriculumScope curriculum);
SemanticExtractionResult ensureQuestionSemantic(long questionId,
        CurriculumScope curriculum, QuestionSnapshotV1 snapshot);
```

Add the repository method `Optional<ProblemQuestion> ProblemQuestionRepository.findByIdForUpdate(Long id)` implemented with `@Lock(LockModeType.PESSIMISTIC_WRITE)` and a JPQL query by ID. The extraction service acquires that lock only for the short status/document update after the provider call.

Behavior is exact:

- a stored READY model is decoded, validated, and returned without calling the port; a stored UNSUPPORTED status returns UNSUPPORTED without another provider call;
- ABSENT or FAILED calls the extraction port outside a transaction, validates/evaluates/materializes EXTRACTED output, then stores canonical model on the version and source question in a short transaction. FAILED is retryable only when a new edit/ORIGIN request explicitly invokes this service;
- UNSUPPORTED sets question status UNSUPPORTED; INVALID_SOURCE sets FAILED and preserves findings without source/answer text; TECHNICAL_ERROR sets FAILED for observability but a later teacher request may retry;
- extraction never overwrites `snapshot`, `content_blocks`, answers, explanation, or current version pointer;
- `ProblemSemanticReferenceEnricher.enrich(ProblemGenerationCommand command)` extracts every ORIGIN with a null semantic model. EXAMPLE models remain lazy unless the semantic prompt asks for solution structure; then extract at most two EXAMPLEs in reference order. Failed examples are retained as snapshot-only references; a failed ORIGIN causes semantic generation to return UNSUPPORTED and the caller uses the existing generation fallback.
- `ProblemGenerationWorker` calls the enricher before `ProblemGenerationPort.generate`; retry request IDs remain deterministic.

- [ ] **Step 1: Write failing idempotence and failure-preservation tests**

```java
@Test
void extractsOnceAndNeverOverwritesLegacySnapshot() {
    when(port.extract(any())).thenReturn(extracted(ProblemSemanticFixtures.radiusProblem()));
    SemanticExtractionResult first = service.ensureQuestionSemantic(41L, scope(), snapshot());
    SemanticExtractionResult second = service.ensureQuestionSemantic(41L, scope(), snapshot());
    assertThat(first.status()).isEqualTo(EXTRACTED);
    assertThat(second.status()).isEqualTo(EXTRACTED);
    verify(port, times(1)).extract(any());
    assertThat(question.getContentBlocks()).isEqualTo(originalContentBlocks);
}

@Test
void unsupportedOriginKeepsSnapshotAndSignalsLegacyFallback() {
    when(port.extract(any())).thenReturn(new SemanticExtractionResult(
            UNSUPPORTED, null, List.of("operation: 지원하지 않음")));
    var enriched = enricher.enrich(commandWithOrigin());
    assertThat(enriched.references().getFirst().semanticModel()).isNull();
    assertThat(enriched.references().getFirst().snapshot()).isEqualTo(originSnapshot);
}
```

- [ ] **Step 2: Run RED**

Run: `bash gradlew test --tests '*ProblemSemanticExtractionServiceTest' --tests '*ProblemSemanticReferenceEnricherTest' --tests '*ProblemSemanticExtractionAdapterTest'`

Expected: FAIL because extraction contracts and services do not exist.

- [ ] **Step 3: Implement extraction port/adapter, persistence, and generation-reference enrichment**

The adapter uses the semantic schema from Task 8, does not pass through Dispatcher, and never logs the snapshot or model. It returns UNSUPPORTED for unsupported operation/diagram types, INVALID_SOURCE when the materialized answer cannot match the source snapshot, and TECHNICAL_ERROR for provider/parse failures.

- [ ] **Step 4: Run GREEN**

Run: `bash gradlew test --tests '*ProblemSemanticExtractionServiceTest' --tests '*ProblemSemanticReferenceEnricherTest' --tests '*ProblemSemanticExtractionAdapterTest' --tests '*ProblemGenerationWorkerTest'`

Expected: PASS for READY idempotence, all four statuses, source preservation, ORIGIN enrichment, optional EXAMPLE enrichment, and fallback signaling.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/extraction \
        src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemSemanticExtractionPort.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticExtractionService.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationReference.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java \
        src/main/java/com/cenedu/backend/domain/problem/repository/ProblemQuestionRepository.java \
        src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionAdapter.java \
        src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionPromptFactory.java \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticExtractionServiceTest.java \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricherTest.java \
        src/test/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionAdapterTest.java
git commit -m "feat : backend - 기존 문제 지연 의미 구조화 추가"
```

---

### Task 11: Classify and apply optimistic semantic patches

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticPatch.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/SemanticEditMode.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/SemanticPatchOperation.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/SemanticPatchOperationType.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticPatchClassifier.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticPatchPath.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticPatchApplier.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/SemanticPatchConflictException.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticDiff.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/SemanticValueChange.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/SemanticImpactArea.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticDiffFactory.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticPatchClassifierTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticPatchApplierTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemSemanticDiffFactoryTest.java`

**Interfaces:**
- Consumes: validated base semantic model and base version ID
- Produces: PRESENTATIONAL/PARAMETRIC/STRUCTURAL classification, expected-old-value conflict protection, patched model, and answer-free diff

Exact contracts:

```java
public record ProblemSemanticPatch(int schemaVersion, UUID requestId,
        Long baseVersionId, SemanticEditMode mode,
        List<SemanticPatchOperation> operations, String assistantMessage) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

public enum SemanticEditMode {
    PRESENTATIONAL_PATCH, PARAMETRIC_PATCH, STRUCTURAL_REGENERATION,
    RESTORE, REJECTED
}

public record SemanticPatchOperation(SemanticPatchOperationType type,
        String path, String expectedOldValue, String newValue) {}

public enum SemanticPatchOperationType {
    SET_PARAMETER_VALUE, SET_PARAMETER_UNIT, SET_TEMPLATE_TEXT,
    SET_DIAGRAM_STYLE, SET_LABEL_TEXT
}

public record SemanticValueChange(String key, String oldValue, String newValue,
        String oldUnit, String newUnit) {}

public enum SemanticImpactArea {
    STEM, CHOICES, STEPS, ANSWERS, EXPLANATION, LEARNING_GUIDE,
    RUBRICS, ASSETS
}

public record ProblemSemanticDiff(List<SemanticValueChange> parameterChanges,
        Set<SemanticImpactArea> impactedAreas, boolean structuralChange,
        boolean revalidationRequired) {}
```

Allowed path grammar is closed:

```text
/parameters/{KEY}/value
/parameters/{KEY}/unit
/presentation/questionTemplate
/presentation/choices/{CHOICE_KEY}/contentTemplate
/presentation/steps/{STEP_KEY}/labelTemplate
/presentation/steps/{STEP_KEY}/segments/{zeroBasedIndex}/textTemplate
/presentation/explanationTemplate
/presentation/learningGuide/conceptTitleTemplate
/presentation/learningGuide/summaryTemplate
/presentation/learningGuide/keyPointTemplates/{zeroBasedIndex}
/presentation/rubrics/{RUBRIC_KEY}/criterionTemplate
/diagrams/{ASSET_KEY}/style/strokeColor
/diagrams/{ASSET_KEY}/style/fillColor
/diagrams/{ASSET_KEY}/style/accentColor
/diagrams/{ASSET_KEY}/style/strokeWidth
/diagrams/{ASSET_KEY}/style/fontSize
/diagrams/{ASSET_KEY}/labels/{LABEL_KEY}
```

Exact public methods are `SemanticEditMode ProblemSemanticPatchClassifier.classify(ProblemSemanticPatch patch)`, `SemanticEditMode ProblemSemanticPatchClassifier.classifyRequestedPath(String path)`, `ProblemSemanticModelV1 ProblemSemanticPatchApplier.apply(ProblemSemanticModelV1 model, ProblemSemanticPatch patch)`, and `ProblemSemanticDiff ProblemSemanticDiffFactory.create(ProblemSemanticModelV1 baseModel, ProblemSemanticModelV1 changedModel, SemanticEditMode mode)`.

Classification is server-owned:

- only SET_TEMPLATE_TEXT/SET_DIAGRAM_STYLE/SET_LABEL_TEXT operations => PRESENTATIONAL_PATCH;
- only SET_PARAMETER_VALUE/SET_PARAMETER_UNIT operations => PARAMETRIC_PATCH;
- mixed presentational/parametric operations are rejected so parameter changes cannot smuggle template edits;
- STRUCTURAL_REGENERATION, RESTORE, and REJECTED require an empty operation list;
- question type, solution strategy, computation operation/operand, diagram kind, parameter/computation add/remove, and curriculum paths are not in the grammar and therefore require STRUCTURAL_REGENERATION.

The applier verifies `patch.schemaVersion`, non-null request/base IDs, classifier result equals declared mode, every parameter is editable, and every `expectedOldValue` exactly equals the server's canonical scalar at the path. A mismatch throws `SemanticPatchConflictException(path, expected, actual)` before any model is returned. It copies records; it never mutates lists. After apply it runs semantic validation/evaluation/materialization. PRESENTATIONAL patches additionally require identical per-template placeholder sets and unchanged normalized semantic value map. PARAMETRIC patches permit dependent changes and require complete re-materialization/re-render.

- [ ] **Step 1: Write failing classification and optimistic conflict tests**

```java
@Test
void classifiesParameterValueAsParametricAndQuestionTypeAsStructural() {
    assertThat(classifier.classify(patch(SET_PARAMETER_VALUE,
            "/parameters/RADIUS/value", "3", "5")))
            .isEqualTo(PARAMETRIC_PATCH);
    assertThat(classifier.classifyRequestedPath("/intent/questionType"))
            .isEqualTo(STRUCTURAL_REGENERATION);
}

@Test
void rejectsStaleExpectedOldValueWithoutPartialApply() {
    ProblemSemanticPatch stale = patch(SET_PARAMETER_VALUE,
            "/parameters/RADIUS/value", "4", "5");
    assertThatThrownBy(() -> applier.apply(ProblemSemanticFixtures.radiusProblem(), stale))
            .isInstanceOfSatisfying(SemanticPatchConflictException.class,
                    exception -> assertThat(exception.path())
                            .isEqualTo("/parameters/RADIUS/value"));
    assertThat(ProblemSemanticFixtures.radiusProblem().parameters().getFirst().value())
            .isEqualTo("3");
}
```

- [ ] **Step 2: Run RED**

Run: `bash gradlew test --tests '*ProblemSemanticPatchClassifierTest' --tests '*ProblemSemanticPatchApplierTest' --tests '*ProblemSemanticDiffFactoryTest'`

Expected: FAIL because patch contracts and services do not exist.

- [ ] **Step 3: Implement path parser, classifier, copy-on-write applier, and sanitized diff**

`ProblemSemanticDiffFactory.create(baseModel, patchedModel, mode)` derives impact areas from placeholder references and diagram bindings. It includes editable parameter values/units but excludes computed answer values, answer-unit raw values, explanation text, and rubric model answers.

- [ ] **Step 4: Run GREEN**

Run: `bash gradlew test --tests '*ProblemSemanticPatchClassifierTest' --tests '*ProblemSemanticPatchApplierTest' --tests '*ProblemSemanticDiffFactoryTest' --tests '*DefaultProblemSemanticMaterializerTest'`

Expected: PASS for all five operations, every legal path family, illegal/structural paths, stale values, non-editable parameters, placeholder invariance, and deterministic impact sets.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic \
        src/test/java/com/cenedu/backend/domain/problem/authoring/edit/semantic
git commit -m "feat : backend - 낙관적 의미 패치와 변경 분류 추가"
```

---

### Task 12: Return normalized semantic patches through AgentDispatcher

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemEditAgentPayload.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemEditConversationResult.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemEditAgentResultEnvelope.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditAgentGateway.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/agent/ProblemEditAgent.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/agent/ProblemEditPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/agent/ProblemEditOutputGuard.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemas.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/agent/ProblemEditAgentTest.java`
- Create: `src/test/java/com/cenedu/backend/ai/problem/agent/ProblemEditOutputGuardTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditAgentGatewayTest.java`
- Test: `src/test/java/com/cenedu/backend/architecture/AiClientAccessTest.java`

**Interfaces:**
- Consumes: current snapshot plus nullable semantic model, teacher input via Dispatcher
- Produces: server-rebound `ProblemSemanticPatch` for semantic models and existing instruction deltas for fallback models

Use additive contracts:

```java
public record ProblemEditAgentPayload(int schemaVersion, UUID requestId,
        Long sessionId, Long baseVersionId,
        AuthoringInteractionStatus interactionStatus,
        ProblemEditTargetRef selectedTarget, QuestionSnapshotV1 currentSnapshot,
        ProblemSemanticModelV1 currentSemanticModel,
        List<ProblemEditInstruction> accumulatedInstructions) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
}

public record ProblemEditConversationResult(EditConversationAction action,
        List<ProblemEditInstruction> instructionDeltas,
        ProblemSemanticPatch semanticPatch, String assistantMessage) {}
```

Keep `ProblemEditAgentResultEnvelope.RESPONSE_KEY="problemEditResult"`; bump its schema version to 2. The EDIT_TURN schema requires `semanticPatch` as an object or null. The patch object requires mode, operations, and assistantMessage, but omits trusted requestId/baseVersionId/schemaVersion from provider output. `ProblemEditAgent` constructs those three fields from payload and replaces any provider echo. It also normalizes selected targets as today for legacy instruction output.

Prompt classification examples are explicit:

- “반지름을 3cm에서 5cm로” => PARAMETRIC_PATCH + `/parameters/RADIUS/value`, expected `3`, new `5`.
- “말을 더 간결하게” => PRESENTATIONAL_PATCH + exact template path with unchanged placeholders.
- “문항을 객관식으로” or “원을 삼각형으로” => STRUCTURAL_REGENERATION, empty operations.
- “지난 버전으로” => RESTORE, empty operations.
- out-of-curriculum, contradictory, unsupported path => REJECTED, empty operations.

The output guard blocks missing patch for a semantic payload, non-empty structural/restore/rejected operations, unknown path/type, request/base mismatch after normalization, answer-bearing assistant messages, and response text containing system prompt markers. It does not execute or persist the patch.

- [ ] **Step 1: Write failing Dispatcher-agent tests**

```java
@Test
void rebindsProviderPatchToServerRequestAndBaseVersion() {
    UUID requestId = UUID.randomUUID();
    when(client.completeStructured(any(), any(), any())).thenReturn(response("""
            {"schemaVersion":2,"problemEditResult":{"action":"REQUEST_CONFIRMATION",
             "instructionDeltas":[],"semanticPatch":{"mode":"PARAMETRIC_PATCH",
             "operations":[{"type":"SET_PARAMETER_VALUE",
             "path":"/parameters/RADIUS/value","expectedOldValue":"3","newValue":"5"}],
             "assistantMessage":"반지름 변경을 확인해 주세요."},
             "assistantMessage":"반지름 변경을 확인해 주세요."}}
            """));
    var result = semanticResult(agent.handle(request(payload(requestId, 20L))));
    assertThat(result.semanticPatch().requestId()).isEqualTo(requestId);
    assertThat(result.semanticPatch().baseVersionId()).isEqualTo(20L);
    assertThat(result.semanticPatch().schemaVersion()).isEqualTo(1);
}
```

- [ ] **Step 2: Run RED**

Run: `bash gradlew test --tests '*ProblemEditAgentTest' --tests '*ProblemEditOutputGuardTest' --tests '*ProblemEditAgentGatewayTest'`

Expected: FAIL because the current agent result has no semantic patch.

- [ ] **Step 3: Extend schema/payload/result, normalize server fields, and harden the output guard**

Do not place semantic interpretation in `AgentDispatcher`; it remains generic. Do not inject Dispatcher into any Agent. `ProblemEditAgentGateway` remains the sole domain call site for `dispatch(PROBLEM_EDIT)`.

- [ ] **Step 4: Run GREEN and architecture tests**

Run: `bash gradlew test --tests '*ProblemEditAgentTest' --tests '*ProblemEditOutputGuardTest' --tests '*ProblemEditAgentGatewayTest' --tests '*AiClientAccessTest'`

Expected: PASS; architecture tests prove domain code does not call `ai.client`, and no domain code references `ai.problem.agent` directly.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemEditAgentPayload.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemEditConversationResult.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemEditAgentResultEnvelope.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditAgentGateway.java \
        src/main/java/com/cenedu/backend/ai/problem/agent \
        src/main/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemas.java \
        src/test/java/com/cenedu/backend/ai/problem/agent \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditAgentGatewayTest.java \
        src/test/java/com/cenedu/backend/architecture/AiClientAccessTest.java
git commit -m "feat : backend - 자연어 수정 의미 패치 디스패처 연결"
```

---

### Task 13: Execute confirmed patches, structural regeneration, restore, and legacy fallback

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticModificationService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemStructuralRegenerationService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/semantic/ProblemModificationExecutionResult.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/PendingProblemEditCommand.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ConfirmedProblemEditCommand.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemEditExecutionPlan.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemModificationCommand.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditPolicy.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinator.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationWorker.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/global/common/ErrorCode.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticModificationServiceTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemStructuralRegenerationServiceTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationServiceTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinatorTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationSnapshotMergerTest.java`

**Interfaces:**
- Consumes: confirmed `ProblemSemanticPatch`, current PASSED version/model, semantic materializer, existing generation and legacy modification ports
- Produces: a verified preview version and answer-free execution diff, or immediate restore/rejection

Exact result and command extensions:

```java
public record ProblemModificationExecutionResult(Long previewVersionId,
        SemanticEditMode mode, ProblemSemanticDiff diff,
        boolean promoted, boolean legacyFallback) {}

public record ProblemModificationCommand(UUID requestId, ProblemEditExecutionPlan plan,
        QuestionSnapshotV1 baseSnapshot, ProblemSemanticModelV1 baseSemanticModel) {}
```

Add nullable `ProblemSemanticPatch semanticPatch` to `PendingProblemEditCommand`, `ConfirmedProblemEditCommand`, and `ProblemEditExecutionPlan`. Existing instruction fields remain. Pending/confirmed equality includes the whole patch. `ProblemEditPolicy.plan` checks the patch's `baseVersionId` against the current version and executes Task 11's server classifier before deriving the action.

`ProblemSemanticModificationService` exact method:

```java
ProblemModificationExecutionResult apply(long ownerTeacherId, long sessionId,
        ProblemAuthoringVersion baseVersion, ProblemSemanticPatch patch);
```

Execution rules:

- PRESENTATIONAL_PATCH and PARAMETRIC_PATCH: decode base model, optimistic apply, evaluate/materialize, produce candidate with normalized patched model and AI_MODIFY provenance, run `ProblemCandidateProcessingService.process`, and return its Version ID plus diff. No LLM call occurs after confirmation.
- PRESENTATIONAL_PATCH: snapshot answer units and canonical value map must equal base; DiagramSpec canonical hash must remain equal unless the operation is style/label-only, in which case only affected asset hashes may change.
- PARAMETRIC_PATCH: regenerate stem, choices, steps, answers, explanation, learning guide, rubrics, and every diagram bound to a changed key. The full content+asset verification path runs.
- STRUCTURAL_REGENERATION: `ProblemStructuralRegenerationService.regenerate(long ownerTeacherId, ProblemAuthoringVersion baseVersion, ProblemEditExecutionPlan plan, ProblemSemanticModelV1 baseModel)` builds a sanitized `ProblemGenerationCommand` from current curriculum/intent and requested specification, uses the current question as ORIGIN, invokes `ProblemGenerationPort`, requires a semantic candidate, runs full verification, and returns `structuralChange=true`. It never forwards raw teacher text.
- RESTORE: retain the existing PASSED-version pointer switch; return the restored version ID, empty parameter changes, impacts for all snapshot areas, and no new verification.
- REJECTED: throw `BusinessException(PROBLEM_SEMANTIC_EDIT_REJECTED)` before changing session state.
- semantic model null while feature enabled: call Task 10 extraction. EXTRACTED continues semantically. UNSUPPORTED/INVALID_SOURCE/TECHNICAL_ERROR set `legacyFallback=true` and route the existing confirmed plan through `ProblemModificationWorker`/`ProblemModificationAdapter`/`ProblemModificationSnapshotMerger` unchanged. A structural legacy request remains whole replacement, never a fabricated parametric patch.
- stale expected value or base version maps to existing `PROBLEM_EDIT_COMMAND_STALE` (409). Add `PROBLEM_SEMANTIC_EDIT_REJECTED` (400), `PROBLEM_SEMANTIC_MODEL_UNSUPPORTED` (409), `PROBLEM_SEMANTIC_MODEL_INVALID` (422), and `PROBLEM_DIAGRAM_RENDER_FAILED` (422) at the end of the Problem error-code block.

- [ ] **Step 1: Write failing synchronized-regeneration and fallback tests**

```java
@Test
void parameterPatchRegeneratesEveryDependentArtifactWithoutModificationLlm() {
    ProblemModificationExecutionResult result = service.apply(7L, 31L, baseVersion,
            patch("/parameters/RADIUS/value", "3", "5"));
    ProblemCandidateDraft candidate = candidateCaptor.getValue();
    assertThat(candidate.snapshot().contentBlocks().getFirst().text()).contains("5cm");
    assertThat(candidate.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("10");
    assertThat(candidate.snapshot().explanation()).contains("5 × 2 = 10cm");
    assertThat(candidate.assetPlans().getFirst().specification().diagramSpec()).isNotNull();
    assertThat(result.diff().impactedAreas()).contains(STEM, ANSWERS, EXPLANATION, ASSETS);
    verifyNoInteractions(legacyModificationPort);
}

@Test
void unsupportedLegacyExtractionUsesCurrentSnapshotMergerPath() {
    when(extraction.ensureVersionSemantic(anyLong(), anyLong(), anyLong(), any()))
            .thenReturn(unsupported());
    coordinator.execute(7L, legacyPlan(), baseSnapshot);
    verify(modificationWorker).execute(eq(7L), argThat(command ->
            command.baseSemanticModel() == null));
}
```

- [ ] **Step 2: Run RED**

Run: `bash gradlew test --tests '*ProblemSemanticModificationServiceTest' --tests '*ProblemStructuralRegenerationServiceTest' --tests '*ProblemModificationExecutionCoordinatorTest'`

Expected: FAIL because confirmed execution cannot apply semantic patches or return a typed preview result.

- [ ] **Step 3: Implement semantic execution first, then structural and legacy branches**

Keep `ProblemModificationSnapshotMerger` production code unchanged. Adapt only its callers and regression tests so it remains a visible, tested fallback rather than silently participating in semantic patches.

- [ ] **Step 4: Run GREEN**

Run: `bash gradlew test --tests '*ProblemSemanticModificationServiceTest' --tests '*ProblemStructuralRegenerationServiceTest' --tests '*ProblemEditConversationServiceTest' --tests '*ProblemModificationExecutionCoordinatorTest' --tests '*ProblemModificationSnapshotMergerTest' --tests '*ProblemModificationWorkerTest'`

Expected: PASS for presentational, parametric, structural, restore, rejected, stale, extraction-success, extraction-failure fallback, and current merger regression cases.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticModificationService.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemStructuralRegenerationService.java \
        src/main/java/com/cenedu/backend/domain/problem/authoring/edit \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditPolicy.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationService.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinator.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationWorker.java \
        src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationAdapter.java \
        src/main/java/com/cenedu/backend/global/common/ErrorCode.java \
        src/test/java/com/cenedu/backend/domain/problem/service \
        src/test/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationSnapshotMergerTest.java
git commit -m "feat : backend - 의미 패치 의존 재생성과 레거시 폴백 연결"
```

---

### Task 14: Expose modification preview/diff and document Swagger/API cases

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemParameterChangeResponse.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemModificationPreviewResponse.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemEditTurnResponse.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditApplicationService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/controller/ProblemEditController.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditApplicationServiceTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/controller/ProblemEditControllerTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticAuthoringScenarioTest.java`

**Interfaces:**
- Consumes: Task 13 typed execution result and current edit-turn endpoint
- Produces: additive, answer-free teacher preview and complete OpenAPI examples for success/error paths

Exact additive response types:

```java
public record ProblemParameterChangeResponse(String key, String oldValue,
        String newValue, String oldUnit, String newUnit) {
    public static ProblemParameterChangeResponse from(SemanticValueChange change) {
        return new ProblemParameterChangeResponse(change.key(), change.oldValue(),
                change.newValue(), change.oldUnit(), change.newUnit());
    }
}

public record ProblemModificationPreviewResponse(Long previewVersionId,
        SemanticEditMode mode, List<ProblemParameterChangeResponse> parameterChanges,
        Set<SemanticImpactArea> impactedAreas, boolean structuralChange,
        boolean revalidationRequired, boolean legacyFallback) {
    public static ProblemModificationPreviewResponse from(
            ProblemModificationExecutionResult result) {
        return new ProblemModificationPreviewResponse(result.previewVersionId(), result.mode(),
                result.diff().parameterChanges().stream()
                        .map(ProblemParameterChangeResponse::from).toList(),
                Set.copyOf(result.diff().impactedAreas()),
                result.diff().structuralChange(), result.diff().revalidationRequired(),
                result.legacyFallback());
    }
}

public record ProblemEditTurnResponse(EditConversationAction action,
        List<ProblemEditInstruction> instructionDeltas,
        ProblemSemanticPatch semanticPatch, String assistantMessage,
        ProblemModificationPreviewResponse preview) {}
```

`preview` is null until CONFIRM_EXECUTION completes. The response never adds answer raw/normalized values, computations, explanation text, semantic JSON, or SVG. The existing `GET /{sessionId}/preview` remains the authorized way for a teacher to inspect the resulting full snapshot.

Annotate `ProblemEditController.handleTurn` with `@Operation`, request/response `@ApiResponse`, and named `@ExampleObject` cases:

- parametric request then confirmation with preview version and impacts;
- presentational request preserving placeholders;
- structural regeneration request;
- restore request;
- rejected curriculum request (400);
- stale expected value/base (409);
- unsupported extraction with `legacyFallback=true`;
- deterministic semantic/diagram validation error (422).

The controller still receives `@AuthenticationPrincipal AuthenticatedUser`; teacher ID is never added to request body/query. All envelopes remain `ApiResponse<T>`.

`ProblemSemanticAuthoringScenarioTest` is a pure parameterized service test and covers the ten spec scenarios with these exact expectations:

| Case | Patch/change | Required synchronized result |
|---|---|---|
| 1 | number-line marked point -2 -> 4 | stem, answer, point position, SVG hash change |
| 2 | coordinate point (2,3) -> (4,5) | stem, answer, point, graph hash change |
| 3 | direct proportion k 2 -> 3 | equation/answer and direct graph path change |
| 4 | inverse proportion k 6 -> 12 | equation/answer and split inverse path change |
| 5 | triangle side 3 -> 5 | bound label, explanation, geometry hash change; triangle constraint passes |
| 6 | circle radius 3 -> 5 | diameter/circumference dependent values and circle label/hash change |
| 7 | rectangular prism width/depth/height | volume answer, explanation, three labels/hash change |
| 8 | one table cell 4 -> 7 | table text/hash, computed answer, explanation change |
| 9 | essay criterion wording | rubric weights still total 100 and no answer disclosure in diff |
| 10 | SHORT_INPUT -> MULTIPLE_CHOICE | STRUCTURAL_REGENERATION, no patch operations, teacher confirmation required |

- [ ] **Step 1: Write failing response/API/scenario tests**

```java
@Test
void confirmedParameterEditReturnsAnswerFreePreview() {
    ProblemEditTurnResponse response = service.handleTurn(7L, 31L, confirmationRequest());
    assertThat(response.preview().previewVersionId()).isEqualTo(103L);
    assertThat(response.preview().parameterChanges()).containsExactly(
            new ProblemParameterChangeResponse("RADIUS", "3", "5", "cm", "cm"));
    assertThat(response.preview().impactedAreas()).contains(STEM, ANSWERS, EXPLANATION, ASSETS);
    assertThat(objectMapper.writeValueAsString(response.preview()))
            .doesNotContain("answerRaw", "answerNormalized", "semanticModel", "svg");
}

@ParameterizedTest(name = "{0}")
@MethodSource("semanticScenarios")
void keepsAllDependentOutputsSynchronized(String name, SemanticScenario scenario) {
    var result = harness.apply(scenario.base(), scenario.patch());
    scenario.assertion().accept(result);
}
```

- [ ] **Step 2: Run RED**

Run: `bash gradlew test --tests '*ProblemEditApplicationServiceTest' --tests '*ProblemSemanticAuthoringScenarioTest'`

Expected: FAIL because edit responses do not include patch/preview and the ten scenario harness expectations are not satisfied.

- [ ] **Step 3: Add preview mapping, typed execution response, Swagger annotations, and all ten scenario fixtures**

`ProblemEditApplicationService.handleTurn` captures the coordinator result only for CONFIRM_EXECUTION and passes it to `ProblemEditTurnResponse.from(result, executionResult)`. REQUEST_CONFIRMATION returns the normalized semantic patch so the UI can show requested parameter names/values before confirmation; REJECTED maps through `BusinessException` and does not store a pending command.

- [ ] **Step 4: Run GREEN including the web contract**

Run:

```bash
bash gradlew test --tests '*ProblemEditApplicationServiceTest' --tests '*ProblemSemanticAuthoringScenarioTest'
JWT_SECRET='test-only-jwt-secret-value-at-least-32-bytes' bash gradlew test --tests '*ProblemEditControllerTest'
```

Expected: pure service/scenario tests PASS without context; MockMvc/OpenAPI controller tests PASS with the process-local JWT value and assert teacher principal, ApiResponse envelope, additive fields, status codes, and examples.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemParameterChangeResponse.java \
        src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemModificationPreviewResponse.java \
        src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemEditTurnResponse.java \
        src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditApplicationService.java \
        src/main/java/com/cenedu/backend/domain/problem/controller/ProblemEditController.java \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditApplicationServiceTest.java \
        src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticAuthoringScenarioTest.java \
        src/test/java/com/cenedu/backend/domain/problem/controller/ProblemEditControllerTest.java
git commit -m "feat : backend - 문제 수정 미리보기와 API 계약 추가"
```

---

### Task 15: Run targeted, migration, architecture, and full regression verification

**Files:**
- Verify only: all production and test files from Tasks 1–14
- Verify only: `src/main/resources/db/migration/V20260819_1000__problem_add_semantic_model.sql`
- Verify only: `build/reports/tests/test/index.html`

**Interfaces:**
- Consumes: completed B implementation
- Produces: evidence that focused behavior passes and any environment baseline is separated from implementation regressions

- [ ] **Step 1: Run all pure semantic/edit unit tests without Spring context**

Run:

```bash
bash gradlew test \
  --tests '*ProblemSemanticModelV1Test' \
  --tests '*ProblemSemanticModelValidatorTest' \
  --tests '*SemanticComputationGraphTest' \
  --tests '*SemanticComputationEngineTest' \
  --tests '*SemanticTemplateEngineTest' \
  --tests '*DefaultProblemSemanticMaterializerTest' \
  --tests '*DiagramSpecValidatorTest' \
  --tests '*ProblemDiagramRendererTest' \
  --tests '*ProblemSemanticDocumentCodecTest' \
  --tests '*ProblemSemanticGenerationPipelineTest' \
  --tests '*ProblemSemanticExtractionServiceTest' \
  --tests '*ProblemSemanticPatchClassifierTest' \
  --tests '*ProblemSemanticPatchApplierTest' \
  --tests '*ProblemSemanticModificationServiceTest' \
  --tests '*ProblemSemanticAuthoringScenarioTest'
```

Expected: PASS without requiring `JWT_SECRET`, Docker, database, OpenAI key, or network.

- [ ] **Step 2: Run existing Problem and architecture regressions**

Run:

```bash
bash gradlew test \
  --tests 'com.cenedu.backend.domain.problem.*' \
  --tests 'com.cenedu.backend.ai.problem.*' \
  --tests '*AiClientAccessTest'
```

Expected: PASS for existing generation, TEXT_ONLY, bank reuse, edit merger fallback, worksheet-facing finalization, asset URL/storage, and package boundaries. If a selected wildcard starts a context test, rerun this exact command with the temporary JWT assignment from Step 3 and record that it was environment-only.

- [ ] **Step 3: Run context, Flyway/JPA, and API tests with an ephemeral non-secret JWT value**

Run:

```bash
JWT_SECRET='test-only-jwt-secret-value-at-least-32-bytes' bash gradlew test \
  --tests '*ContextTest' \
  --tests '*ProblemEditControllerTest' \
  --tests '*Flyway*Test' \
  --tests '*Jpa*Test'
```

Expected: PASS. The assignment exists only in this process invocation; do not write it to `.env`, `application.yaml`, shell profile, CI secrets, or source control.

- [ ] **Step 4: Reproduce and document the known no-secret baseline separately**

Run: `env -u JWT_SECRET bash gradlew test`

Expected on the stated checkout baseline: Java/test compilation completes, then Spring context/live tests cascade-fail with `PlaceholderResolutionException` resolving `app.jwt.secret: ${JWT_SECRET}`. This command is diagnostic and is not the B acceptance gate. If it unexpectedly passes because the shell imports a local `.env`, report that `.env` supplied the value without reading or printing it.

- [ ] **Step 5: Run the full acceptance suite and build with the temporary value**

Run:

```bash
JWT_SECRET='test-only-jwt-secret-value-at-least-32-bytes' bash gradlew test
JWT_SECRET='test-only-jwt-secret-value-at-least-32-bytes' bash gradlew build
```

Expected: both commands PASS. Live tests must skip unless their existing explicit live-test prerequisites are present; they must never call OpenAI merely because `JWT_SECRET` is set.

- [ ] **Step 6: Check migration uniqueness, formatting, and changed-file scope**

Run:

```bash
test "$(find src/main/resources/db/migration -name 'V20260819_1000__problem_add_semantic_model.sql' | wc -l | tr -d ' ')" = "1"
git diff --check
git status --short
git log --oneline --decorate -15
```

Expected: one fixed migration, no whitespace errors, no uncommitted implementation files, and one focused commit for each Task 1–14. Task 0 and Task 15 intentionally create no implementation commit.

- [ ] **Step 7: Review the final diff against completion conditions**

Confirm all statements with code/test evidence:

- every enabled new AI generation stores a validated semantic model;
- legacy conversion occurs only on edit/ORIGIN/explicit semantic few-shot use;
- parameter changes synchronize stem, choices/steps, answers, explanation, rubrics, and assets;
- presentational changes preserve semantic value hash and unaffected diagram hashes;
- structural changes require full regeneration and teacher confirmation;
- five diagram kinds render deterministic, sanitized SVG;
- content and asset verification both gate current-version promotion;
- feature disabled preserves current generation and snapshot-merger behavior;
- API preview has no answer/model/SVG leakage;
- A's `CurriculumScope` remains the only curriculum scope definition.

There is no commit for this verification-only task. If verification requires a code change, return to the owning task, add a RED regression test, amend with a new focused fix commit, and repeat all verification steps.

---

## Execution Notes

- Implement in task order. Tasks 1–4 establish the semantic core; Tasks 5–6 establish visual determinism; Task 7 establishes storage; later tasks depend on all three.
- At each task boundary, review the named files only and ensure the task's commit is independently testable.
- Do not squash while executing; task commits are the review checkpoints. Squashing, PR creation, or branch integration is handled only after all Task 15 evidence passes.
