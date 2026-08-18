# Problem RAG Generation A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the A-stage middle-school math RAG path: enriched curriculum scope, approved-problem pgvector retrieval, deterministic diverse references in generation commands, answer-safe Few-shot JSON, asynchronous indexing, and quality events usable by a later C-stage ranker.

**Architecture:** `domain.problem` owns curriculum/search/indexing contracts and orchestration, `ai.embedding` is the only new package that calls the OpenAI embeddings API, and `infra.vector` owns PostgreSQL/pgvector persistence, HNSW retrieval, MMR selection, and retrieval traces. Existing generation, verification, HITL, Snapshot V1, and finalization flows remain authoritative; feature flags default off and every retrieval/indexing failure degrades to the existing no-reference generation path.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Spring Data JPA, Spring JDBC, PostgreSQL 17, pgvector `vector(1024)`, Flyway, OpenAI Java 4.50.0, JUnit 5, Mockito, AssertJ, Testcontainers, MockMvc, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-08-19-problem-rag-structured-authoring-design.md`

## Global Constraints

- Work only on branch `feat/backend-problem-rag-a`; do not add B semantic-authoring or C hybrid/reranking behavior.
- Use exactly `src/main/resources/db/migration/V20260819_0900__problem_create_search_index.sql`; never edit an applied migration.
- Use `curriculumRevision="2022_REVISED"`, `schoolLevel="MIDDLE"`, and `grade=1`; use `achievementStandardId` when present and `subUnitId` as the logged fallback key when absent.
- Use embedding model default `text-embedding-3-small`, exactly 1024 dimensions, cosine distance, and an HNSW `vector_cosine_ops` index.
- `domain.problem` owns `ProblemReferenceRetrievalPort`, `SearchIndexingPort`, their commands/results, search-document rules, and generation orchestration. It must not import `infra.vector`, `ai.embedding`, `com.openai`, or Spring AI.
- `ai.embedding` owns the provider call. `infra.vector` may depend on `ai.embedding` and implement Problem-owned ports, but it must not import `com.openai` directly.
- Keep `ProblemGenerationCommand.references` as the generation/verification provenance contract. `ORIGIN` is caller supplied; selected corpus items become `EXAMPLE`.
- Do not put answer raw values, teacher prompt text, system prompts, or full problem text in retrieval/decision logs. The searchable index may retain the internal Snapshot JSON required to restore a selected reference.
- Keep all API limits server-side: candidate limit 40; selection limit 3 for general/comprehensive and 4 for similar/application; lambda 0.70 except application 0.55.
- Feature defaults are `PROBLEM_RAG_ENABLED=false` and `PROBLEM_RAG_INDEXING_ENABLED=false`. Disabled or failed retrieval must preserve current API responses and generation behavior.
- Repositories and services require a one-line business-purpose comment above every method, per `AGENTS.md`.
- New environment keys must be documented in `.env.example`; no credentials or temporary test JWT values may be committed.
- Prefer pure JUnit/Mockito tests. Context tests must set `app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum` in test properties.
- Baseline caveat: `bash gradlew test` compiles but, without `.env` or an OS `JWT_SECRET`, existing context/live tests cascade-fail with `PlaceholderResolutionException` for `app.jwt.secret: ${JWT_SECRET}`. Final full verification must use an inline, non-secret test value at least 32 bytes long and must not persist it.

---

## Locked File and Type Map

The executor must use these paths and responsibilities. Do not merge unrelated responsibilities into one class.

### Curriculum and Problem-owned contracts

- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScope.java`: immutable nine-field scope from the spec, fixed-scope validation, and `achievementMissing()`.
- Delete `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumContext.java`: superseded by `CurriculumScope`.
- Modify `src/main/java/com/cenedu/backend/domain/curriculum/entity/CurriculumUnit.java`: map revision, school level, and optional achievement-standard ID.
- Modify `src/main/java/com/cenedu/backend/domain/curriculum/dto/response/CurriculumPathResponse.java`: expose revision, school level, grade, semester, and achievement-standard ID to the Problem service boundary.
- Modify `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationRequirement.java`: use `CurriculumScope` and preserve caller references.
- Modify `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationCommand.java`: use `CurriculumScope` and add nullable `UUID retrievalRequestId`.
- Modify `src/main/java/com/cenedu/backend/domain/problem/authoring/verification/VerificationExpectation.java`; its exact production and test consumers are enumerated under Task 1.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQuery.java`: exact A-stage query contract and invariant validation.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievedProblemReference.java`: exact selected-reference output contract.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceRetrievalPort.java`: `List<RetrievedProblemReference> retrieve(ProblemReferenceQuery query)`.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievalFallbackReason.java`: `PORT_UNAVAILABLE`, `PROVIDER_FAILURE`, `SEARCH_TIMEOUT`, `NO_CANDIDATES`, `SNAPSHOT_RESTORE_FAILED`.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemRetrievalTracePort.java`: fallback recording and post-ID linkage methods.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingCommand.java`: immutable Snapshot-bearing indexing payload.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingPort.java`: idempotent `boolean enqueue(SearchIndexingCommand command)`.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocument.java`: canonical internal document plus hashes and source-family key.
- Create `src/main/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactory.java`: stable document/query construction and SHA-256 hashes.
- Create `src/main/java/com/cenedu/backend/domain/problem/service/SearchCorpusEligibility.java`: `READY`, `WAITING_FOR_ASSETS`, `REJECTED`.
- Create `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchCorpusEligibilityService.java`: current question/deletion/asset readiness check.
- Create `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchIndexingService.java`: construct and enqueue finalized/imported indexing commands only.
- Create `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillService.java`: bounded, validated Snapshot backfill into `SearchIndexingPort`.
- Create `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillScheduler.java`: feature-gated cursor and fixed-delay invocation only.

### Configuration and provider boundary

- Create `src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagProperties.java`: all A policy values and indexing worker settings.
- Create `src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagConfig.java`: enable `ProblemRagProperties` binding.
- Create `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingProperties.java`: model and dimension binding.
- Create `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingResult.java`: model plus immutable `List<Float>` vector.
- Create `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingClient.java`: provider-neutral `EmbeddingResult embed(String text)`.
- Create `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingCallException.java`: retryable provider failure wrapper.
- Create `src/main/java/com/cenedu/backend/ai/embedding/OpenAiEmbeddingClient.java`: sole embeddings API caller.
- Delete `src/main/java/com/cenedu/backend/ai/embedding/.gitkeep` and `src/main/java/com/cenedu/backend/infra/vector/.gitkeep` when the first classes are added.

### pgvector implementation and telemetry

- Create `src/main/java/com/cenedu/backend/infra/vector/VectorCodec.java`: pgvector text encoding/decoding and cosine similarity.
- Create `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchCandidate.java`: package-private row with metadata, Snapshot, source family, and vector.
- Create `src/main/java/com/cenedu/backend/infra/vector/DeterministicMmrSelector.java`: duplicate/source-family guards and deterministic MMR ordering.
- Create `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexJdbcRepository.java`: task claiming/state transitions and READY index upsert.
- Create `src/main/java/com/cenedu/backend/infra/vector/PgVectorSearchIndexingAdapter.java`: `SearchIndexingPort` enqueue implementation only.
- Create `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorker.java`: document/hash, skip, embedding, dimension validation, retry, and READY transitions.
- Create `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexScheduler.java`: feature-gated scheduled worker invocation only.
- Create `src/main/java/com/cenedu/backend/infra/vector/ProblemReferenceJdbcRepository.java`: hard-filtered HNSW cosine SQL and Snapshot restoration.
- Create `src/main/java/com/cenedu/backend/infra/vector/ProblemRetrievalTraceJdbcRepository.java`: trace header/candidate persistence and ID linkage.
- Create `src/main/java/com/cenedu/backend/infra/vector/ProblemVectorConfig.java`: bounded retrieval executor configuration only.
- Create `src/main/java/com/cenedu/backend/infra/vector/PgVectorProblemReferenceRetrievalAdapter.java`: query embedding, candidate retrieval, MMR, selected DTO mapping, normal/fallback trace persistence.
- Create `src/main/java/com/cenedu/backend/infra/vector/PgVectorProblemRetrievalTraceAdapter.java`: `ProblemRetrievalTracePort` implementation only.

### Generation, prompt, events, and regression seams

- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationService.java`: build full `CurriculumScope` from curriculum service output.
- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningService.java`: retrieve per shortage slot, preserve one ORIGIN, exclude already-used IDs, and fall back safely.
- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobService.java`: link retrieval request to persisted Job/Item.
- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`: preserve retrieval ID over retries and link the promoted Authoring Version.
- Create `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPrompt.java`: immutable static system prompt plus ordered user messages.
- Create `src/main/java/com/cenedu/backend/ai/problem/adapter/FewShotReferenceSerializer.java`: answer-safe JSON serialization only.
- Modify `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPromptFactory.java`: static prefix, Few-shot user message, current-request user message.
- Modify `src/main/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapter.java`: consume `ProblemGenerationPrompt` without changing structured output schema.
- Create `src/main/java/com/cenedu/backend/domain/problem/entity/enums/TeacherDecisionType.java`: `APPROVED`, `MODIFICATION_STARTED`, `RESTORED`, `REPLACED`, `DISCARDED`.
- Create `src/main/java/com/cenedu/backend/domain/problem/entity/ProblemTeacherDecisionEvent.java`: immutable append-only event entity.
- Create `src/main/java/com/cenedu/backend/domain/problem/repository/ProblemTeacherDecisionEventRepository.java`: idempotency lookup/save.
- Create `src/main/java/com/cenedu/backend/domain/problem/service/ProblemTeacherDecisionEventService.java`: deterministic event keys and answer/prompt-free payloads.
- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java`: emit approval and enqueue finalized indexing.
- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationService.java`: emit modification-started and restore events.
- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinator.java`: emit successful replacement events.
- Modify `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetCleanupService.java`: emit explicit teacher discard events.
- Modify `src/test/java/com/cenedu/backend/architecture/AiClientAccessTest.java`: enforce the new package boundaries.
- Modify `src/main/resources/application.yaml` and `.env.example`: defaults and documented overrides.

### Tests

- Create `src/test/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScopeTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQueryTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/config/ProblemRagPropertiesTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactoryTest.java`.
- Create `src/test/java/com/cenedu/backend/ai/embedding/OpenAiEmbeddingClientTest.java`.
- Create `src/test/java/com/cenedu/backend/infra/vector/DeterministicMmrSelectorTest.java`.
- Create `src/test/java/com/cenedu/backend/infra/vector/ProblemSearchSchemaMigrationTest.java`.
- Create `src/test/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorkerIntegrationTest.java`.
- Create `src/test/java/com/cenedu/backend/infra/vector/PgVectorProblemReferenceRetrievalAdapterIntegrationTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningServiceTest.java`.
- Create `src/test/java/com/cenedu/backend/ai/problem/adapter/FewShotReferenceSerializerTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/service/ProblemTeacherDecisionEventServiceTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinatorTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/service/ProblemRagFallbackTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/controller/ProblemRagGenerationApiIntegrationTest.java`.
- Create `src/test/java/com/cenedu/backend/domain/problem/controller/ProblemRagGenerationApiLiveTest.java`.
- Modify only the exact existing consumer tests listed in Tasks 1, 5, 7, 8, and 9 so constructors compile with the new signatures.

---

### Task 1: Database Foundation and Curriculum Scope

**Files:**
- Create: `src/main/resources/db/migration/V20260819_0900__problem_create_search_index.sql`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScope.java`
- Delete: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumContext.java`
- Modify: `src/main/java/com/cenedu/backend/domain/curriculum/entity/CurriculumUnit.java`
- Modify: `src/main/java/com/cenedu/backend/domain/curriculum/dto/response/CurriculumPathResponse.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationRequirement.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationCommand.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/verification/VerificationExpectation.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/ContentIntegrityChecker.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/ExpectationChecks.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationLlmClient.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationPrompts.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScopeTest.java`
- Test: `src/test/java/com/cenedu/backend/infra/vector/ProblemSearchSchemaMigrationTest.java`
- Modify: `src/test/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationOutputMapperTest.java`
- Modify: `src/test/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapterTest.java`
- Modify: `src/test/java/com/cenedu/backend/ai/verification/adapter/VerificationFixtures.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationPlanTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringEndToEndTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationServiceTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingServiceTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorkerTest.java`

**Interfaces:**
- Consumes: existing curriculum tree, Snapshot V1, authoring Job/Item/Version IDs.
- Produces: `CurriculumScope`, the exact seven-field `ProblemGenerationCommand` record shown in Step 3, and all A-stage tables.

- [ ] **Step 1: Write the failing pure scope test**

```java
package com.cenedu.backend.domain.problem.authoring.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CurriculumScopeTest {
    @Test
    void acceptsTheFixedM1ScopeAndReportsMissingAchievementStandard() {
        CurriculumScope scope = new CurriculumScope(
                "2022_REVISED", "MIDDLE", 1, 2, null, 30L,
                "수와 연산", "정수와 유리수", "정수의 덧셈과 뺄셈");

        assertThat(scope.achievementMissing()).isTrue();
        assertThat(scope.subUnitId()).isEqualTo(30L);
    }

    @Test
    void rejectsScopeOutsideTheAStageCurriculum() {
        assertThatThrownBy(() -> new CurriculumScope(
                "2015_REVISED", "MIDDLE", 1, 1, "9수01-01", 30L,
                "대", "중", "소"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A단계는 2022 개정 중학교 1학년만 지원합니다.");
    }
}
```

- [ ] **Step 2: Run the pure test and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.authoring.generation.CurriculumScopeTest'`

Expected: `compileTestJava` fails because `CurriculumScope` does not exist.

- [ ] **Step 3: Add the exact scope contract and migrate existing command consumers**

```java
package com.cenedu.backend.domain.problem.authoring.generation;

/** 검색·생성·검증이 공유하는 2022 개정 중1 교육과정 범위다. */
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
) {
    public CurriculumScope {
        if (!"2022_REVISED".equals(curriculumRevision)
                || !"MIDDLE".equals(schoolLevel) || grade != 1) {
            throw new IllegalArgumentException("A단계는 2022 개정 중학교 1학년만 지원합니다.");
        }
        if (semester != null && semester != 1 && semester != 2) {
            throw new IllegalArgumentException("학기는 1, 2 또는 null이어야 합니다.");
        }
        if (subUnitId == null || majorUnitName == null || middleUnitName == null
                || subUnitName == null) {
            throw new IllegalArgumentException("교육과정 단원 경로가 필요합니다.");
        }
        achievementStandardId = normalize(achievementStandardId);
    }

    public boolean achievementMissing() {
        return achievementStandardId == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

The new `ProblemGenerationCommand` signature is fixed as:

```java
public record ProblemGenerationCommand(
        UUID requestId,
        UUID retrievalRequestId,
        GenerationPurpose purpose,
        GenerationSpecification specification,
        CurriculumScope curriculum,
        List<GenerationReference> references,
        List<GenerationConceptEvidence> conceptEvidence
) {
    public ProblemGenerationCommand {
        references = references == null ? List.of() : List.copyOf(references);
        conceptEvidence = conceptEvidence == null ? List.of() : List.copyOf(conceptEvidence);
    }
}
```

Use `curriculum()` everywhere; do not retain a second alias named `curriculumContext()`.

Map these exact additional fields on `CurriculumUnit`: `String curriculumRevision` → `curriculum_revision`, `String schoolLevel` → `school_level`, and nullable `String achievementStandardId` → `achievement_standard_id`; existing Lombok getters remain the public read API. `CurriculumPathResponse` becomes:

```java
public record CurriculumPathResponse(
        Long majorUnitId,
        String majorUnitName,
        Long middleUnitId,
        String middleUnitName,
        Long subUnitId,
        String subUnitName,
        String curriculumRevision,
        String schoolLevel,
        short grade,
        Short semester,
        String achievementStandardId
) {}
```

`CurriculumPathResponse.from(...)` takes its scope fields from the sub-unit after validating that major/middle/sub values agree; `ProblemAsyncGenerationService.requirement(...)` copies all eleven response values into `CurriculumScope` and never hard-codes a nullable achievement ID.

- [ ] **Step 4: Write the failing migration integration test**

```java
package com.cenedu.backend.infra.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
class ProblemSearchSchemaMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsVector1024HnswAndTelemetryTables() {
        String vectorType = jdbc.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                WHERE a.attrelid = 'problem_search_index'::regclass
                  AND a.attname = 'embedding'
                """, String.class);
        String indexDefinition = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE indexname = 'idx_problem_search_index_embedding_hnsw'
                """, String.class);

        assertThat(vectorType).isEqualTo("vector(1024)");
        assertThat(indexDefinition).contains("USING hnsw", "vector_cosine_ops");
        assertThat(jdbc.queryForObject("SELECT to_regclass('problem_retrieval_trace')", String.class))
                .isEqualTo("problem_retrieval_trace");
        assertThat(jdbc.queryForObject("SELECT to_regclass('problem_teacher_decision_event')", String.class))
                .isEqualTo("problem_teacher_decision_event");
    }
}
```

- [ ] **Step 5: Run the migration test and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.infra.vector.ProblemSearchSchemaMigrationTest'`

Expected: FAIL because `problem_search_index` does not exist (or application validation fails after the entity fields are added but before the migration is present).

- [ ] **Step 6: Implement `V20260819_0900__problem_create_search_index.sql` completely**

The single migration must perform these exact operations in this order:

1. `CREATE EXTENSION IF NOT EXISTS vector`.
2. Add `curriculum_revision VARCHAR(20) NOT NULL DEFAULT '2022_REVISED'`, `school_level VARCHAR(20) NOT NULL DEFAULT 'MIDDLE'`, and nullable `achievement_standard_id VARCHAR(40)` to `curriculum_unit`; add checks restricting current rows to the fixed scope.
3. Create `problem_search_index` keyed by `question_id`, containing scope columns, type/difficulty/presentation, `source_family_key`, `document_text`, `document_hash CHAR(64)`, `duplicate_cluster_key CHAR(64)`, `concept_keys TEXT[]`, `snapshot JSONB`, embedding model/dimensions, `embedding VECTOR(1024)`, `index_status`, `deleted`, and timestamps. Add metadata B-tree indexes and `idx_problem_search_index_embedding_hnsw USING hnsw (embedding vector_cosine_ops)`.
4. Create `problem_search_index_task` with unique `question_id`, unique `idempotency_key`, `command JSONB`, statuses `PENDING|PROCESSING|RETRY_WAIT|READY|SKIPPED|FAILED`, attempt count, next-attempt/error/timestamps, and a due-task partial index.
5. Create `problem_retrieval_trace` and `problem_retrieval_candidate`, with nullable Job/Item/Version links and no text/answer columns.
6. Create `problem_teacher_decision_event` with unique `event_key`, teacher/session/version IDs, decision type, nullable JSON arrays for change natures/target types, and timestamp.

Use these decisive checks in the SQL:

```sql
CONSTRAINT ck_problem_search_index_dimensions CHECK (embedding_dimensions = 1024),
CONSTRAINT ck_problem_search_index_status CHECK (index_status IN ('READY', 'DELETED')),
CONSTRAINT ck_problem_search_task_status CHECK (
    status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'READY', 'SKIPPED', 'FAILED')
),
CONSTRAINT ck_problem_teacher_decision_type CHECK (
    decision_type IN ('APPROVED', 'MODIFICATION_STARTED', 'RESTORED', 'REPLACED', 'DISCARDED')
)
```

Do not enqueue 5,594 rows in Flyway; the feature-gated Java backfill creates validated Snapshot-bearing commands after deployment.

- [ ] **Step 7: Run scope, migration, and affected contract tests**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.authoring.generation.CurriculumScopeTest' --tests 'com.cenedu.backend.infra.vector.ProblemSearchSchemaMigrationTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationWorkerTest' --tests 'com.cenedu.backend.ai.verification.adapter.ProblemVerificationAdapterTest'`

Expected: BUILD SUCCESSFUL. The migration test starts pgvector PostgreSQL; pure tests do not start Spring.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V20260819_0900__problem_create_search_index.sql \
  src/main/java/com/cenedu/backend/domain/curriculum/entity/CurriculumUnit.java \
  src/main/java/com/cenedu/backend/domain/curriculum/dto/response/CurriculumPathResponse.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScope.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumContext.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationCommand.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationRequirement.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/verification/VerificationExpectation.java \
  src/main/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationService.java \
  src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningService.java \
  src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java \
  src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationAdapter.java \
  src/main/java/com/cenedu/backend/ai/verification/adapter/ContentIntegrityChecker.java \
  src/main/java/com/cenedu/backend/ai/verification/adapter/ExpectationChecks.java \
  src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationLlmClient.java \
  src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationPrompts.java \
  src/test/java/com/cenedu/backend/domain/problem/authoring/generation/CurriculumScopeTest.java \
  src/test/java/com/cenedu/backend/infra/vector/ProblemSearchSchemaMigrationTest.java \
  src/test/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationOutputMapperTest.java \
  src/test/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapterTest.java \
  src/test/java/com/cenedu/backend/ai/verification/adapter/VerificationFixtures.java \
  src/test/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationPlanTest.java \
  src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringEndToEndTest.java \
  src/test/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationServiceTest.java \
  src/test/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingServiceTest.java \
  src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorkerTest.java
git commit -m "feat : 문제 RAG 교육과정 범위와 검색 스키마 추가"
```

---

### Task 2: Retrieval/Indexing Contracts and Feature Configuration

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQuery.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievedProblemReference.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceRetrievalPort.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievalFallbackReason.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemRetrievalTracePort.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingCommand.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingPort.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagProperties.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagConfig.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQueryTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/config/ProblemRagPropertiesTest.java`

**Interfaces:**
- Consumes: `CurriculumScope`, `GenerationPurpose`, `QuestionType`, `QuestionSnapshotV1`.
- Produces: the stable A/C-compatible retrieval boundary, an idempotent indexing enqueue boundary, and server-only policy settings.

- [ ] **Step 1: Write failing constructor-invariant and property-binding tests**

Cover all exact rules: candidate limit 40 maximum, selection limit 1–4, excluded IDs copied, general/comprehensive forbid ORIGIN, personalized purposes require both ORIGIN ID and Snapshot, and fixed curriculum validation is inherited.

In `ProblemRagPropertiesTest`, use `ApplicationContextRunner` with only `ProblemRagConfig` and assert the default values listed in Step 4; this must not load security, JPA, Flyway, or a web context.

Representative complete test method:

```java
@Test
void personalizedQueryRequiresOneOriginSnapshot() {
    assertThatThrownBy(() -> new ProblemReferenceQuery(
            UUID.randomUUID(), GenerationPurpose.PERSONALIZED_APPLICATION,
            scope(), QuestionType.SHORT_INPUT, "mid", 10L, null,
            40, 4, Set.of(10L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("맞춤 유사·응용 검색에는 ORIGIN ID와 Snapshot이 필요합니다.");
}
```

- [ ] **Step 2: Run and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQueryTest' --tests 'com.cenedu.backend.domain.problem.config.ProblemRagPropertiesTest'`

Expected: `compileTestJava` fails because the retrieval contracts and properties classes do not exist.

- [ ] **Step 3: Implement the exact public contracts**

`ProblemReferenceQuery` has this exact signature and copies its exclusion set:

```java
public record ProblemReferenceQuery(
        UUID retrievalRequestId,
        GenerationPurpose purpose,
        CurriculumScope curriculum,
        QuestionType questionType,
        String difficulty,
        Long originQuestionId,
        QuestionSnapshotV1 originSnapshot,
        int candidateLimit,
        int selectionLimit,
        Set<Long> excludedQuestionIds
) {
    public ProblemReferenceQuery {
        excludedQuestionIds = excludedQuestionIds == null
                ? Set.of() : Set.copyOf(excludedQuestionIds);
    }
}
```

```java
public interface ProblemReferenceRetrievalPort {
    /** 교육과정 hard filter와 A 정책을 적용한 최종 참고 문제를 순서대로 반환한다. */
    List<RetrievedProblemReference> retrieve(ProblemReferenceQuery query);
}

public interface SearchIndexingPort {
    /** questionId 멱등 키로 PENDING 작업을 만들며 이미 존재하면 false를 반환한다. */
    boolean enqueue(SearchIndexingCommand command);
}

public interface ProblemRetrievalTracePort {
    /** 검색 실행 전후 실패를 원문 없이 기록한다. */
    void recordFallback(ProblemReferenceQuery query, RetrievalFallbackReason reason);
    /** 저장 뒤 확보된 생성 Job/Item을 기존 trace에 연결한다. */
    void linkGeneration(UUID retrievalRequestId, long jobId, long itemId);
    /** 생성·검증 뒤 확보된 Authoring Version을 기존 trace에 연결한다. */
    void linkAuthoringVersion(UUID retrievalRequestId, long authoringVersionId);
}
```

`SearchIndexingCommand` must have this exact signature and defensively copy `conceptKeys`:

```java
public record SearchIndexingCommand(
        UUID idempotencyKey,
        Long questionId,
        Long authoringVersionId,
        CurriculumScope curriculum,
        String sourceRef,
        QuestionSnapshotV1 snapshot,
        Set<String> conceptKeys
) {
    public SearchIndexingCommand {
        conceptKeys = conceptKeys == null ? Set.of() : Set.copyOf(conceptKeys);
    }
}
```

- [ ] **Step 4: Bind exact flags and policy values**

`ProblemRagProperties` signature:

```java
@ConfigurationProperties(prefix = "app.problem.rag")
public record ProblemRagProperties(
        boolean enabled,
        String policyVersion,
        int candidateLimit,
        int standardSelectionLimit,
        int personalizedSelectionLimit,
        double defaultLambda,
        double applicationLambda,
        Duration searchTimeout,
        Indexing indexing
) {
    public record Indexing(
            boolean enabled,
            int batchSize,
            int maxAttempts,
            Duration retryDelay,
            Duration workerDelay,
            Duration backfillDelay
    ) {}
}
```

Add these defaults under `app.problem.rag` in `application.yaml`:

```yaml
app:
  problem:
    rag:
      enabled: ${PROBLEM_RAG_ENABLED:false}
      policy-version: A_DENSE_MMR_V1
      candidate-limit: 40
      standard-selection-limit: 3
      personalized-selection-limit: 4
      default-lambda: 0.70
      application-lambda: 0.55
      search-timeout: 2s
      indexing:
        enabled: ${PROBLEM_RAG_INDEXING_ENABLED:false}
        batch-size: 20
        max-attempts: 3
        retry-delay: 30s
        worker-delay: 5s
        backfill-delay: 60s
```

Document only the two override keys in `.env.example`; policy tuning remains YAML/server-side and never becomes an API field.

- [ ] **Step 5: Run pure contracts and configuration binding test**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQueryTest' --tests 'com.cenedu.backend.domain.problem.config.ProblemRagPropertiesTest'`

Expected: BUILD SUCCESSFUL; neither test starts the application context.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQuery.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievedProblemReference.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceRetrievalPort.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievalFallbackReason.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemRetrievalTracePort.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingCommand.java \
  src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingPort.java \
  src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagProperties.java \
  src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagConfig.java \
  src/main/resources/application.yaml .env.example \
  src/test/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQueryTest.java \
  src/test/java/com/cenedu/backend/domain/problem/config/ProblemRagPropertiesTest.java
git commit -m "feat : 문제 RAG 검색과 인덱싱 계약 추가"
```

---

### Task 3: OpenAI 1024-Dimension Embedding Adapter

**Files:**
- Create: `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingProperties.java`
- Create: `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingResult.java`
- Create: `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingClient.java`
- Create: `src/main/java/com/cenedu/backend/ai/embedding/EmbeddingCallException.java`
- Create: `src/main/java/com/cenedu/backend/ai/embedding/OpenAiEmbeddingClient.java`
- Delete: `src/main/java/com/cenedu/backend/ai/embedding/.gitkeep`
- Modify: `src/main/java/com/cenedu/backend/ai/client/OpenAiClientConfig.java` (enable `EmbeddingProperties`; do not add embedding logic)
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Test: `src/test/java/com/cenedu/backend/ai/embedding/OpenAiEmbeddingClientTest.java`

**Interfaces:**
- Consumes: existing `OpenAIClient` bean and `app.ai.embedding` settings.
- Produces: `EmbeddingResult embed(String text)` with model identity and exactly 1024 floats.

- [ ] **Step 1: Write the failing provider adapter test**

Use Mockito for `OpenAIClient` and `EmbeddingService`, and SDK builders for `CreateEmbeddingResponse`/`Embedding`; verify `EmbeddingCreateParams` contains model `text-embedding-3-small`, input text, and dimensions `1024L`. Add empty-data and provider-exception cases.

- [ ] **Step 2: Run and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.ai.embedding.OpenAiEmbeddingClientTest'`

Expected: `compileTestJava` fails because `OpenAiEmbeddingClient` does not exist.

- [ ] **Step 3: Implement the complete provider call**

```java
package com.cenedu.backend.ai.embedding;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {
    private final OpenAIClient client;
    private final EmbeddingProperties properties;

    public OpenAiEmbeddingClient(OpenAIClient client, EmbeddingProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩 입력 문서는 필수입니다.");
        }
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .input(text)
                .model(properties.model())
                .dimensions((long) properties.dimensions())
                .build();
        try {
            CreateEmbeddingResponse response = client.embeddings().create(params);
            if (response.data().size() != 1) {
                throw new EmbeddingCallException("임베딩 응답 개수가 1이 아닙니다.", false);
            }
            List<Float> vector = List.copyOf(response.data().getFirst().embedding());
            if (vector.size() != properties.dimensions()) {
                throw new EmbeddingCallException("임베딩 차원이 1024가 아닙니다.", false);
            }
            return new EmbeddingResult(response.model(), vector);
        } catch (OpenAIException exception) {
            throw new EmbeddingCallException("임베딩 Provider 호출에 실패했습니다.", true, exception);
        }
    }
}
```

`EmbeddingCallException` exposes exactly `EmbeddingCallException(String message, boolean retryable)`, `EmbeddingCallException(String message, boolean retryable, Throwable cause)`, and `boolean retryable()`. `EmbeddingResult` copies its vector in the compact constructor.

Add `app.ai.embedding.model=${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}` and `dimensions: 1024`; document `OPENAI_EMBEDDING_MODEL` in `.env.example`. Do not expose a dimensions environment override because the schema is fixed at 1024.

- [ ] **Step 4: Run adapter and architecture tests**

Run: `bash gradlew test --tests 'com.cenedu.backend.ai.embedding.OpenAiEmbeddingClientTest' --tests 'com.cenedu.backend.architecture.AiClientAccessTest'`

Expected: BUILD SUCCESSFUL; no network call occurs.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/ai/embedding src/main/java/com/cenedu/backend/ai/client/OpenAiClientConfig.java src/main/resources/application.yaml .env.example src/test/java/com/cenedu/backend/ai/embedding
git commit -m "feat : OpenAI 1024차원 임베딩 어댑터 추가"
```

---

### Task 4: Stable Search Document, Hash, and Duplicate Keys

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocument.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactory.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactoryTest.java`

**Interfaces:**
- Consumes: `SearchIndexingCommand` or `ProblemReferenceQuery`.
- Produces: canonical document text, `documentHash`, `duplicateClusterKey`, `sourceFamilyKey`, solution strategy, and answer-free solution summary.

- [ ] **Step 1: Write failing determinism and leak tests**

The complete test class must prove:

- CRLF/trailing-space/list-order noise normalizes to the same hash.
- Changing only `SnapshotAnswerUnit.answerRaw/answerNormalized` leaves document text/hash unchanged.
- Changing the visible prompt changes document hash.
- Replacing numeric literals in otherwise identical prompts yields the same duplicate cluster key.
- No known answer sentinel appears in document text.
- Source refs `110:11319_11635` and `110:11319_27047` both map to source family `110:11319`; null source ref maps to `authored:<questionId>`.

- [ ] **Step 2: Run and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocumentFactoryTest'`

Expected: `compileTestJava` fails because the factory and result record do not exist.

- [ ] **Step 3: Implement the exact document format**

```java
public record ProblemSearchDocument(
        String documentText,
        String documentHash,
        String duplicateClusterKey,
        String sourceFamilyKey,
        String solutionStrategy,
        String solutionSummary
) {}
```

The factory's two public methods are exact:

```java
/** 인덱싱 Snapshot에서 답안 없는 정규 검색 문서와 해시를 만든다. */
public ProblemSearchDocument create(SearchIndexingCommand command);

/** 검색 요구에서 같은 레이블 순서의 답안 없는 query 문서를 만든다. */
public String createQuery(ProblemReferenceQuery query);
```

`ProblemSearchDocumentFactory.create(SearchIndexingCommand)` must emit exactly these eight newline-delimited labels in order:

```text
[교육과정] 중학교 1학년 > {major} > {middle} > {sub}
[성취기준] {achievementStandardId or MISSING:SUB_UNIT:{subUnitId}}
[유형] {questionType}
[난이도] {difficulty}
[발문] {ordered visible contentBlocks text}
[풀이전략] {learningGuide.keyPoints joined with " | " or conceptTitle}
[풀이요약] {learningGuide.summary or "구조 요약 없음"}
[표현] text-only | figure | table
```

Normalize all fields with Unicode NFKC, CRLF→LF, repeated horizontal whitespace→one space, trim each line, and preserve label/newline order. Hash UTF-8 bytes with lowercase SHA-256 hex. Build `duplicateClusterKey` from normalized prompt with decimal/integer literals replaced by `#`, plus sub-unit and question type. Never read `answerUnits` or `explanation` while constructing the document.

`createQuery(ProblemReferenceQuery)` uses the same labels. If `originSnapshot` exists, use its visible prompt/guide/presentation; otherwise use `"동일 교육과정 범위의 새 문제"`, `"동일 성취기준의 핵심 풀이 전략"`, `"동일 난이도의 풀이 구조"`, and `text-only` for the final four semantic fields.

- [ ] **Step 4: Run tests and verify green**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocumentFactoryTest'`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/search src/test/java/com/cenedu/backend/domain/problem/authoring/search
git commit -m "feat : 안정적인 문제 검색 문서와 중복 해시 추가"
```

---

### Task 5: Asynchronous Idempotent Indexing and Validated Backfill

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/SearchCorpusEligibility.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchCorpusEligibilityService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchIndexingService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillScheduler.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/repository/ProblemQuestionRepository.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/VectorCodec.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexJdbcRepository.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/PgVectorSearchIndexingAdapter.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorker.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexScheduler.java`
- Delete: `src/main/java/com/cenedu/backend/infra/vector/.gitkeep`
- Test: `src/test/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorkerIntegrationTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationServiceTest.java`

**Interfaces:**
- Consumes: finalized Question/Version Snapshot, imported reusable bank Snapshots, `EmbeddingClient`, and `SearchIndexingCommand`.
- Produces: one durable task per question, a current READY vector row, and bounded restart-safe backfill.

- [ ] **Step 1: Write failing idempotency, retry, and dimension tests**

Use the existing `PostgresTestcontainer` plus a test `@Primary EmbeddingClient`. The integration class must cover all six transitions: duplicate enqueue creates one task; finalization enqueue makes zero provider calls; success writes one READY `vector(1024)` row; unchanged `documentHash` marks the new attempt `SKIPPED` while leaving the READY row unchanged; 1023 floats marks `FAILED`; retryable provider failure marks `RETRY_WAIT` with `next_attempt_at` and succeeds on the next claim.

Representative complete test method:

```java
@Test
void duplicateEnqueueIsIdempotentAndEmbeddingRunsOnlyInWorker() {
    SearchIndexingCommand command = command(UUID.fromString(
            "3e1cb8b9-b5dd-4bbf-83e2-f30ab6630032"), 901L);

    assertThat(indexingPort.enqueue(command)).isTrue();
    assertThat(indexingPort.enqueue(command)).isFalse();
    verifyNoInteractions(embeddingClient);

    assertThat(worker.runPending()).isEqualTo(1);

    verify(embeddingClient).embed(anyString());
    assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM problem_search_index
            WHERE question_id = 901 AND index_status = 'READY'
            """, Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM problem_search_index_task
            WHERE question_id = 901
            """, Integer.class)).isEqualTo(1);
}
```

- [ ] **Step 2: Run indexing tests and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.infra.vector.ProblemSearchIndexWorkerIntegrationTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationServiceTest'`

Expected: `compileTestJava` fails because indexing services/adapters do not exist and finalization does not enqueue.

- [ ] **Step 3: Implement enqueue persistence and atomic due-task claiming**

Use `NamedParameterJdbcTemplate`; do not map vector tables as JPA entities. `ProblemSearchIndexJdbcRepository` must expose these package-private methods with a one-line business-purpose comment above each:

```java
boolean insertPending(SearchIndexingCommand command);
List<ClaimedSearchIndexTask> claimDue(Instant now, int limit);
Optional<ReadySearchIndexMetadata> findReadyMetadata(long questionId);
void upsertReady(ClaimedSearchIndexTask task, ProblemSearchDocument document,
                 EmbeddingResult embedding, String vectorLiteral);
void markReady(long taskId);
void markSkipped(long taskId);
void markRetry(long taskId, int attemptCount, Instant nextAttemptAt, String errorCode);
void markFailed(long taskId, int attemptCount, String errorCode);
```

`insertPending` uses `INSERT ... ON CONFLICT DO NOTHING`. `claimDue` runs in one `@Transactional` method using `FOR UPDATE SKIP LOCKED`, updates selected rows to `PROCESSING`, increments `attempt_count`, and returns the claimed command JSON. A crashed `PROCESSING` task is recoverable: the claim SQL also selects `PROCESSING` rows whose `updated_at < now - retryDelay`, so no task remains stuck forever.

`VectorCodec` signatures are fixed:

```java
String encode(List<Float> vector);
List<Float> decode(String vectorLiteral);
double cosineSimilarity(List<Float> left, List<Float> right);
```

Reject non-finite values and vectors not exactly 1024 elements before JDBC execution. Use PostgreSQL `CAST(:embedding AS vector)`; do not interpolate float values into SQL.

- [ ] **Step 4: Implement worker state rules**

```java
@Component
public class ProblemSearchIndexWorker {
    /** 현재 처리 가능한 작업을 설정된 batch 크기까지만 처리하고 처리 수를 반환한다. */
    public int runPending();

    /** 이미 원자적으로 선점한 한 작업을 READY, SKIPPED, RETRY_WAIT 또는 FAILED로 끝낸다. */
    public void runOne(ClaimedSearchIndexTask task);
}
```

Apply this exact order in `runOne`: eligibility check; canonical document/hash; unchanged READY hash short-circuit; provider embedding; 1024/non-finite validation; READY upsert; task READY. `WAITING_FOR_ASSETS` becomes `RETRY_WAIT`; `REJECTED` becomes `FAILED`. `EmbeddingCallException.retryable()==true` retries only while `attemptCount < maxAttempts`; all non-retryable failures become `FAILED`. Store error codes such as `ASSETS_NOT_READY`, `CORPUS_REJECTED`, `EMBEDDING_RETRYABLE`, and `EMBEDDING_DIMENSION_INVALID`, never provider messages or Snapshot text.

The current READY index row is replaced only after a complete new embedding is available. A failed or skipped task must never delete the last READY row.

- [ ] **Step 5: Enqueue finalized and imported questions without blocking the request**

`ProblemSearchIndexingService` is the only domain service that builds indexing commands:

```java
/** 최종 승인 문항을 검색 인덱싱 큐에 멱등 등록한다. */
public boolean enqueueFinalized(long questionId, long authoringVersionId,
                                QuestionSnapshotV1 snapshot);

/** 검증된 적재 문항 Snapshot을 검색 인덱싱 큐에 멱등 등록한다. */
public boolean enqueueImported(long questionId, QuestionSnapshotV1 snapshot);
```

It looks up `ProblemQuestion` and calls `CurriculumUnitQueryService.getPathsBySubUnitIds(Set.of(subUnitId))`, then constructs `CurriculumScope`. Generate the idempotency key deterministically from UTF-8 `"problem-search:" + questionId`; `authoringVersionId` may be null only for imported questions. If indexing is disabled or `SearchIndexingPort` is absent, return `false` without logging at warning level.

After `ProblemAuthoringFinalizationService` has successfully saved the finalized question and version, call `enqueueFinalized(...)` in the same service transaction. This call only inserts a small task row and must not invoke OpenAI. Preserve finalization success if enqueue returns false; let only malformed local data fail the transaction.

Add this exact repository method for cursor backfill:

```java
/** 삭제되지 않은 문항을 ID 커서 뒤에서 일정 크기로 반환한다. */
List<ProblemQuestion> findByIdGreaterThanAndDeletedAtIsNullOrderByIdAsc(
        Long afterId, Pageable pageable);
```

`ProblemSearchBackfillService` signature:

```java
/** 커서 뒤의 검증 가능한 문항을 batch 크기만큼 검사해 큐에 넣고 다음 커서를 반환한다. */
public BackfillBatchResult enqueueBatch(long afterQuestionId, int batchSize);
```

For each row, restore Snapshot through `ProblemBankSnapshotQueryService`; enqueue only `reusable()==true`. Asset-pending rows are allowed into the task queue and resolved by eligibility; invalid/unrestorable rows are counted as rejected. Return `BackfillBatchResult(long nextQuestionId, int scanned, int enqueued, int rejected, boolean exhausted)`. The scheduler keeps an in-memory cursor starting at `0`, resets it to `0` after exhaustion, and is harmless after restart because `question_id` is unique. It runs only when both RAG and indexing flags are true and uses `batchSize=20` by default.

- [ ] **Step 6: Run indexing and finalization tests**

Run: `bash gradlew test --tests 'com.cenedu.backend.infra.vector.ProblemSearchIndexWorkerIntegrationTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationServiceTest'`

Expected: BUILD SUCCESSFUL. The test verifies task insertion separately from provider execution and confirms retries do not remove a READY row.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/SearchCorpusEligibility.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchCorpusEligibilityService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchIndexingService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillScheduler.java src/main/java/com/cenedu/backend/domain/problem/repository/ProblemQuestionRepository.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java src/main/java/com/cenedu/backend/infra/vector/VectorCodec.java src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexJdbcRepository.java src/main/java/com/cenedu/backend/infra/vector/PgVectorSearchIndexingAdapter.java src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorker.java src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexScheduler.java src/main/java/com/cenedu/backend/infra/vector/.gitkeep src/test/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorkerIntegrationTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationServiceTest.java
git commit -m "feat : 문제 검색 인덱싱 수명주기와 백필 추가"
```

---

### Task 6: Metadata-Hard-Filtered HNSW Search, Duplicate Guards, and Deterministic MMR

**Files:**
- Create: `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchCandidate.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/DeterministicMmrSelector.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/ProblemReferenceJdbcRepository.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/ProblemRetrievalTraceJdbcRepository.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/ProblemVectorConfig.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/PgVectorProblemReferenceRetrievalAdapter.java`
- Create: `src/main/java/com/cenedu/backend/infra/vector/PgVectorProblemRetrievalTraceAdapter.java`
- Test: `src/test/java/com/cenedu/backend/infra/vector/DeterministicMmrSelectorTest.java`
- Test: `src/test/java/com/cenedu/backend/infra/vector/PgVectorProblemReferenceRetrievalAdapterIntegrationTest.java`

**Interfaces:**
- Consumes: `ProblemReferenceQuery`, canonical query document, READY vector rows, `EmbeddingClient`.
- Produces: up to the requested final references in deterministic order plus text-free trace header/candidate rows.

- [ ] **Step 1: Write failing pure MMR and pgvector integration tests**

Construct candidates with explicit dense rank, score, vector, duplicate cluster, source family, type, and difficulty. Prove: an excluded ID never appears; at most one member of each duplicate cluster/source family appears; application uses lambda 0.55; exact difficulty/type bonuses are applied; equal MMR scores resolve by `denseRank`, then `questionId`; input order changes do not change output; selection stops only at `selectionLimit`, not at an arbitrary similarity threshold.

Also create `PgVectorProblemReferenceRetrievalAdapterIntegrationTest` now, using the fixture matrix and HNSW `EXPLAIN` assertion specified in Step 6. It remains red until the repository, adapter, trace persistence, and executor exist.

Representative complete assertion:

```java
@Test
void tieBreaksByDenseRankThenQuestionIdAndGuardsFamilies() {
    List<ProblemSearchCandidate> selected = selector.select(
            List.of(candidate(13, 2, .80, "c2", "f2"),
                    candidate(12, 1, .80, "c1", "f1"),
                    candidate(11, 1, .80, "c1", "f1")),
            List.of(1f, 0f), 3, .70, QuestionType.SHORT_INPUT, "mid");

    assertThat(selected).extracting(ProblemSearchCandidate::questionId)
            .containsExactly(11L, 13L);
}
```

- [ ] **Step 2: Run MMR test and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.infra.vector.DeterministicMmrSelectorTest' --tests 'com.cenedu.backend.infra.vector.PgVectorProblemReferenceRetrievalAdapterIntegrationTest'`

Expected: `compileTestJava` fails because selector/candidate/retrieval classes do not exist.

- [ ] **Step 3: Implement hard-filtered HNSW SQL**

`ProblemReferenceJdbcRepository` exposes exactly:

```java
/** metadata hard filter 뒤 cosine 최근접 후보를 dense 순서로 반환한다. */
List<ProblemSearchCandidate> findCandidates(ProblemReferenceQuery query,
                                            String queryVectorLiteral);
```

Build one named-parameter SQL statement with these mandatory predicates inside a materialized nearest-neighbor CTE:

```sql
WITH nearest AS MATERIALIZED (
    SELECT *, embedding <=> CAST(:queryVector AS vector) AS cosine_distance
    FROM problem_search_index
    WHERE index_status = 'READY'
      AND deleted = false
      AND curriculum_revision = :curriculumRevision
      AND school_level = :schoolLevel
      AND grade = :grade
      AND (
           (:achievementStandardId IS NOT NULL
            AND achievement_standard_id = :achievementStandardId)
           OR (:achievementStandardId IS NULL AND sub_unit_id = :subUnitId)
      )
      AND difficulty BETWEEN :minimumDifficulty AND :maximumDifficulty
      AND (:allowCrossType OR question_type = :questionType)
      AND question_id NOT IN (:excludedQuestionIds)
    ORDER BY embedding <=> CAST(:queryVector AS vector)
    LIMIT :candidateLimit
)
SELECT * FROM nearest
ORDER BY cosine_distance, question_id
```

The inner `ORDER BY` contains only the bare cosine-distance operator plus `LIMIT`, which is required for pgvector HNSW use; the outer ordering supplies deterministic question-ID ties without changing nearest-neighbor access. Map `low=1`, `mid=2`, `high=3`, clamp requested difficulty ±1 to 1..3, and set `allowCrossType=true` only for `PERSONALIZED_APPLICATION`. Because an empty `NOT IN` collection is invalid SQL, choose between two complete constant SQL strings based on `excludedQuestionIds.isEmpty()`; never pass null into a SQL function or collection predicate. Return `denseScore = 1.0 - cosineDistance` and `denseRank` from outer row order. SQL decides only the dense order; exact-difficulty/type preferences belong to MMR.

- [ ] **Step 4: Implement deterministic MMR and trace persistence**

For each candidate compute:

```text
adjustedRelevance = min(1.0, denseScore
    + (candidate difficulty == requested difficulty ? 0.02 : 0.0)
    + (candidate type == requested type ? 0.01 : 0.0))
mmr = lambda * adjustedRelevance
    - (1 - lambda) * max cosineSimilarity(candidate, each selected candidate)
```

At each round discard candidates whose `duplicateClusterKey` or `sourceFamilyKey` is already selected. Sort by `mmr DESC`, `denseRank ASC`, `questionId ASC`; use `double` without rounding for choice, and store the chosen score for telemetry. The ORIGIN is excluded by ID before SQL and its vector is not a selected candidate for diversity penalty.

`ProblemRetrievalTraceJdbcRepository` exact methods:

```java
void insertStarted(ProblemReferenceQuery query, String policyVersion);
void insertCandidates(UUID retrievalRequestId, List<ProblemSearchCandidate> candidates,
                      Set<Long> selectedQuestionIds);
void complete(UUID retrievalRequestId, int candidateCount, int selectedCount,
              RetrievalFallbackReason fallbackReason, long durationMs);
void linkGeneration(UUID retrievalRequestId, long jobId, long itemId);
void linkAuthoringVersion(UUID retrievalRequestId, long authoringVersionId);
```

Trace rows contain IDs, ranks, dense/MMR scores, metadata, model/policy, counts, fallback enum, and duration only. They must not contain `document_text`, Snapshot JSON, answer, teacher input, or prompt text.

- [ ] **Step 5: Implement bounded retrieval with fallback trace**

`ProblemVectorConfig` provides a named fixed-size `ExecutorService` bean `problemRagSearchExecutor` with two daemon threads and `destroyMethod="shutdown"`. The adapter signature stays the domain port:

```java
@Override
public List<RetrievedProblemReference> retrieve(ProblemReferenceQuery query);
```

The adapter records STARTED, runs query-document creation, embedding, JDBC search, MMR, and Snapshot mapping on that executor, and uses `Future.get(properties.searchTimeout().toMillis(), MILLISECONDS)`. On timeout, cancel the future, complete the trace with `SEARCH_TIMEOUT`, and return `List.of()`. On provider failure use `PROVIDER_FAILURE`; on zero post-filter candidates use `NO_CANDIDATES`; on invalid stored Snapshot skip that candidate and use `SNAPSHOT_RESTORE_FAILED` only when none remain. Never propagate retrieval failure to generation. Preserve interruption by calling `Thread.currentThread().interrupt()` before returning fallback.

`RetrievedProblemReference` maps each selected row to:

```java
public record RetrievedProblemReference(
        long sourceQuestionId,
        QuestionSnapshotV1 snapshot,
        int denseRank,
        double denseScore,
        double mmrScore,
        String sourceFamilyKey,
        String duplicateClusterKey
) {}
```

- [ ] **Step 6: Complete and run the pgvector integration test**

Seed READY rows that deliberately differ in revision, school, grade, achievement standard/sub-unit, difficulty, type, duplicate cluster, family, deleted flag, and status. Stub only `EmbeddingClient`. Assert hard filters, application cross-type behavior, exclusion, deterministic MMR, Snapshot restoration, and text-free trace columns.

Also run this plan assertion inside the test transaction after inserting at least 200 eligible rows:

```sql
SET LOCAL enable_seqscan = off;
EXPLAIN (FORMAT TEXT)
SELECT question_id FROM problem_search_index
WHERE index_status = 'READY' AND deleted = false
ORDER BY embedding <=> CAST(:queryVector AS vector)
LIMIT 40;
```

Assert the lines contain `idx_problem_search_index_embedding_hnsw`; this guards operator-class/query-order compatibility, not production planner cost decisions on tiny fixtures.

Run: `bash gradlew test --tests 'com.cenedu.backend.infra.vector.DeterministicMmrSelectorTest' --tests 'com.cenedu.backend.infra.vector.PgVectorProblemReferenceRetrievalAdapterIntegrationTest'`

Expected: BUILD SUCCESSFUL. PostgreSQL applies `vector_cosine_ops`; only eligible rows can reach MMR.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cenedu/backend/infra/vector/ProblemSearchCandidate.java src/main/java/com/cenedu/backend/infra/vector/DeterministicMmrSelector.java src/main/java/com/cenedu/backend/infra/vector/ProblemReferenceJdbcRepository.java src/main/java/com/cenedu/backend/infra/vector/ProblemRetrievalTraceJdbcRepository.java src/main/java/com/cenedu/backend/infra/vector/ProblemVectorConfig.java src/main/java/com/cenedu/backend/infra/vector/PgVectorProblemReferenceRetrievalAdapter.java src/main/java/com/cenedu/backend/infra/vector/PgVectorProblemRetrievalTraceAdapter.java src/test/java/com/cenedu/backend/infra/vector/DeterministicMmrSelectorTest.java src/test/java/com/cenedu/backend/infra/vector/PgVectorProblemReferenceRetrievalAdapterIntegrationTest.java
git commit -m "feat : pgvector hard filter 검색과 결정적 MMR 추가"
```

---

### Task 7: Populate Actual Generation References and Link Retrieval Provenance

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningServiceTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobServiceTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorkerTest.java`

**Interfaces:**
- Consumes: bank-first shortage slots, optional caller ORIGIN, `ProblemReferenceRetrievalPort`, trace-link port.
- Produces: persisted commands whose `GenerationReference` list contains real selected Snapshot V1 examples and a stable retrieval request ID.

- [ ] **Step 1: Extend planning tests first**

Write pure Mockito cases for every purpose and branch:

- `GENERAL_LEARNING_SHORTAGE` and `COMPREHENSIVE_ASSESSMENT_SHORTAGE`: no ORIGIN, select at most three `EXAMPLE`s.
- `PERSONALIZED_SIMILAR_SHORTAGE`: exactly one caller `ORIGIN`, then at most four distinct `EXAMPLE`s.
- `PERSONALIZED_APPLICATION`: same ORIGIN invariant, selection limit four, application lambda is passed through policy in the query.
- Every shortage slot gets a distinct `retrievalRequestId` and retrieval call.
- Exclusions contain bank-reused IDs, ORIGIN ID, all previously selected examples, and examples selected for earlier shortage slots.
- Disabled flag, absent port, empty result, thrown exception, and timeout fallback all produce the pre-A command (caller references only) without failing planning.

Representative complete test method:

```java
@Test
void personalizedSlotPreservesOriginAndAddsActualExamples() {
    when(retrievalPort.retrieve(any())).thenReturn(List.of(
            retrieved(201L, exampleSnapshot()), retrieved(202L, secondSnapshot())));

    ProblemGenerationPlan plan = service.plan(CLIENT_REQUEST_ID,
            GenerationJobType.PERSONALIZED, List.of(personalizedRequirement()));

    ProblemGenerationCommand command = plan.slots().getFirst().generationCommand();
    assertThat(command.references()).extracting(GenerationReference::role)
            .containsExactly(GenerationReferenceRole.ORIGIN,
                    GenerationReferenceRole.EXAMPLE, GenerationReferenceRole.EXAMPLE);
    assertThat(command.references()).extracting(GenerationReference::sourceQuestionId)
            .containsExactly(100L, 201L, 202L);
    assertThat(command.retrievalRequestId()).isNotNull();
}
```

- [ ] **Step 2: Run planning/worker tests and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationPlanningServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationWorkerTest'`

Expected: tests fail because planning never calls retrieval and retry command reconstruction loses the new retrieval ID.

- [ ] **Step 3: Implement one retrieval request per shortage slot**

Keep the existing public method unchanged:

```java
public ProblemGenerationPlan plan(UUID clientRequestId, GenerationJobType jobType,
                                  List<ProblemGenerationRequirement> requirements);
```

Add these private helpers with exact signatures:

```java
private ProblemGenerationCommand createGenerationCommand(
        ProblemGenerationRequirement requirement, Set<Long> excludedQuestionIds);
private List<GenerationReference> retrieveReferences(
        ProblemGenerationRequirement requirement, UUID retrievalRequestId,
        Set<Long> excludedQuestionIds);
private ProblemReferenceQuery createRetrievalQuery(
        ProblemGenerationRequirement requirement, UUID retrievalRequestId,
        Set<Long> excludedQuestionIds);
```

Use `ObjectProvider<ProblemReferenceRetrievalPort>` and `ObjectProvider<ProblemRetrievalTracePort>` so disabled/missing A beans cannot break boot. Validate requirements before selecting the bank: general/comprehensive references must contain no ORIGIN; personalized references must contain exactly one ORIGIN with non-null source ID and Snapshot. Do not invent an ORIGIN from a selected corpus item.

For each AI slot create one `retrievalRequestId`, retrieve, append selected rows as `new GenerationReference(EXAMPLE, sourceQuestionId, snapshot)`, and update the shared exclusion set immediately. Preserve caller reference order; append examples in MMR order. Keep that ID when retrieval ran, including empty/fallback results, so fallback traces can still link to the eventual Job/Item/Version. Use `retrievalRequestId=null` only when RAG is disabled or no retrieval port exists. The retrieval adapter owns normal fallback trace creation; planning records `PORT_UNAVAILABLE` when enabled but no port bean exists, and `PROVIDER_FAILURE` only if a nonconforming/custom port propagates a runtime failure.

This branch does not add a personalized HTTP endpoint. It makes both personalized purposes fully operable through the existing generation contracts so a later caller can use A without B or C.

- [ ] **Step 4: Link persisted Job/Item and promoted Version IDs**

Inject `ObjectProvider<ProblemRetrievalTracePort>` into `ProblemGenerationJobService`. Immediately after saving an AI item and obtaining its ID, call:

```java
tracePort.linkGeneration(command.retrievalRequestId(), job.getId(), item.getId());
```

Only call when the ID is non-null and a port exists. A linkage failure is telemetry-only: log IDs and exception type, not command/Snapshot, and preserve Job creation.

In `ProblemGenerationWorker.commandForAttempt`, change only `requestId`; copy the original `retrievalRequestId`, purpose, specification, curriculum, references, and evidence exactly. After `CandidateProcessingResult.promoted()==true`, call `linkAuthoringVersion(originalRetrievalRequestId, result.versionId())` before `jobService.succeed`; linkage failure must not reverse a promoted version.

- [ ] **Step 5: Run generation provenance tests**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationPlanningServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationJobServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationWorkerTest'`

Expected: BUILD SUCCESSFUL. Captured retry commands retain the same retrieval ID and reference Snapshots; trace linkage receives actual persisted IDs.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningServiceTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobServiceTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorkerTest.java
git commit -m "feat : 생성 명령에 실제 RAG 참고 문제와 추적 ID 연결"
```

---

### Task 8: Answer-Safe Few-shot JSON Prompt Inclusion

**Files:**
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPrompt.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/FewShotReferenceSerializer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapter.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/FewShotReferenceSerializerTest.java`
- Modify: `src/test/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapterTest.java`

**Interfaces:**
- Consumes: ORIGIN/EXAMPLE Snapshot references from the persisted command.
- Produces: a static system prompt and ordered user messages containing sanitized Few-shot JSON before the current request.

- [ ] **Step 1: Write failing serialization and message-order tests**

Use Snapshot fixtures with unmistakable sentinels in `answerRaw`, `answerNormalized`, and `explanation`. Assert all sentinels are absent, while visible prompt, choice content, step labels/text, role, source ID, curriculum scope, type/difficulty, strategy, and visual summary are present. Assert the static system prompt is byte-identical for commands with and without references; when examples exist the message order is `FEW_SHOT_JSON`, then `CURRENT_REQUEST_JSON`.

Representative complete leak assertion:

```java
@Test
void serializesUsefulStructureWithoutAnswersOrExplanation() {
    String json = serializer.serialize(scope(), List.of(referenceWithSentinels()));

    assertThat(json).contains("EXAMPLE", "보이는 발문", "보기 1", "식 세우기");
    assertThat(json).doesNotContain("ANSWER_RAW_SENTINEL",
            "ANSWER_NORMALIZED_SENTINEL", "EXPLANATION_SENTINEL");
}
```

- [ ] **Step 2: Run prompt tests and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.ai.problem.adapter.FewShotReferenceSerializerTest' --tests 'com.cenedu.backend.ai.problem.adapter.SpringAiProblemGenerationAdapterTest'`

Expected: `compileTestJava` fails for the serializer/prompt record; existing adapter exposes one dynamic system string and an ID-only reference list.

- [ ] **Step 3: Implement the answer-safe projection**

```java
public record ProblemGenerationPrompt(
        String systemPrompt,
        List<ChatMessage> messages
) {
    public ProblemGenerationPrompt {
        messages = List.copyOf(messages);
    }
}
```

`FewShotReferenceSerializer` public method is exact:

```java
/** 참고 Snapshot을 정답 없는 Few-shot JSON 배열로 직렬화한다. */
public String serialize(CurriculumScope curriculum,
                        List<GenerationReference> references);
```

Serialize dedicated private projection records, never the Snapshot directly. Each JSON item contains only: `role`, `sourceQuestionId`, `curriculum` (revision/school/grade/semester/achievement/sub-unit and names), `questionType`, `difficulty`, visible `prompt`, ordered visible `choices`, ordered steps with only labels and TEXT segment text (BLANK represented as `"<BLANK>"`), `solutionStrategy` from answer-free learning-guide key points, `visualSummary` (`text-only|figure|table`), and `directCopyForbidden=true`. It contains no `answerUnits`, normalized answers, explanation, rubric answer material, storage keys, metadata, DB version IDs, or full asset payload.

- [ ] **Step 4: Split static policy from dynamic user content**

Change the factory signature to:

```java
/** 정적 생성 정책과 Few-shot/현재 요청 메시지를 순서대로 만든다. */
public ProblemGenerationPrompt create(ProblemGenerationCommand command);
```

Move all invariant JSON/schema/type rules into one `private static final String SYSTEM_PROMPT`. Build the current request as JSON, not string concatenation, with purpose, specification, curriculum, and the sentence `참고 문제의 구조와 전략만 참고하고 수치·문장·정답을 복사하지 마라.` If references are non-empty, first add `new ChatMessage(USER, "FEW_SHOT_JSON\n" + serializedReferences)`; always add `new ChatMessage(USER, "CURRENT_REQUEST_JSON\n" + currentRequestJson)`.

`SpringAiProblemGenerationAdapter.generate` must call exactly:

```java
ProblemGenerationPrompt prompt = promptFactory.create(command);
String response = llmClient.completeStructured(
        prompt.systemPrompt(), prompt.messages(),
        ProblemStructuredOutputSchemas.CANDIDATE).text();
```

Do not change `ProblemStructuredOutputSchemas.CANDIDATE`, output mapping, structural validation, or normalized validation.

- [ ] **Step 5: Run prompt and output regressions**

Run: `bash gradlew test --tests 'com.cenedu.backend.ai.problem.adapter.FewShotReferenceSerializerTest' --tests 'com.cenedu.backend.ai.problem.adapter.SpringAiProblemGenerationAdapterTest' --tests 'com.cenedu.backend.ai.problem.adapter.ProblemGenerationOutputMapperTest'`

Expected: BUILD SUCCESSFUL. Captured LLM calls show sanitized Few-shot JSON before current-request JSON and unchanged structured output schema.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPrompt.java src/main/java/com/cenedu/backend/ai/problem/adapter/FewShotReferenceSerializer.java src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPromptFactory.java src/main/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapter.java src/test/java/com/cenedu/backend/ai/problem/adapter/FewShotReferenceSerializerTest.java src/test/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapterTest.java
git commit -m "feat : 정답 안전 Few-shot JSON 생성 프롬프트 추가"
```

---

### Task 9: Append-only Teacher Decision Events for Later C-stage Learning

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/entity/enums/TeacherDecisionType.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/entity/ProblemTeacherDecisionEvent.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/repository/ProblemTeacherDecisionEventRepository.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemTeacherDecisionEventService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinator.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetCleanupService.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemTeacherDecisionEventServiceTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinatorTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationServiceTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationServiceTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetCleanupServiceTest.java`

**Interfaces:**
- Consumes: successful teacher-owned authoring state transitions and structured edit enums.
- Produces: idempotent, append-only, prompt-free quality events that C can aggregate later without changing A behavior.

- [ ] **Step 1: Write failing event idempotency and privacy tests**

Test each enum value and integration seam. Repeating the same business transition must leave one row. Modification events must store deduplicated/sorted enum names, not `ProblemEditInstruction.instruction()`. Event payload must contain no Snapshot, answer, prompt, or free-form teacher input.

Representative complete service test:

```java
@Test
void modificationEventStoresOnlyStructuredEnumsAndIsIdempotent() {
    when(repository.existsByEventKey(any())).thenReturn(false, true);
    List<ProblemEditInstruction> instructions = List.of(
            new ProblemEditInstruction(EditTargetType.CHOICE, "C1",
                    EditChangeNature.SEMANTIC, "TEACHER_PROMPT_SENTINEL"));

    service.recordModificationStarted(7L, 30L, 41L, REQUEST_ID, instructions);
    service.recordModificationStarted(7L, 30L, 41L, REQUEST_ID, instructions);

    ArgumentCaptor<ProblemTeacherDecisionEvent> captor =
            ArgumentCaptor.forClass(ProblemTeacherDecisionEvent.class);
    verify(repository, times(1)).save(captor.capture());
    ProblemTeacherDecisionEvent event = captor.getValue();
    assertThat(event.getChangeNaturesJson()).isEqualTo("[\"SEMANTIC\"]");
    assertThat(event.getTargetTypesJson()).isEqualTo("[\"CHOICE\"]");
    assertThat(event.getChangeNaturesJson() + event.getTargetTypesJson())
            .doesNotContain("TEACHER_PROMPT_SENTINEL");
}
```

- [ ] **Step 2: Run event and affected service tests; verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.service.ProblemTeacherDecisionEventServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemEditConversationServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemModificationExecutionCoordinatorTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemDraftAssetCleanupServiceTest'`

Expected: `compileTestJava` fails because event types/service do not exist.

- [ ] **Step 3: Implement the exact append-only event API**

`TeacherDecisionType` contains exactly `APPROVED`, `MODIFICATION_STARTED`, `RESTORED`, `REPLACED`, `DISCARDED`.

```java
@Service
public class ProblemTeacherDecisionEventService {
    /** 교사가 현재 Version을 최종 승인한 결정을 기록한다. */
    public void recordApproval(long teacherId, long sessionId, long versionId);

    /** 교사가 구조화된 수정 실행을 시작한 결정을 기록한다. */
    public void recordModificationStarted(long teacherId, long sessionId, long baseVersionId,
                                          UUID requestId,
                                          List<ProblemEditInstruction> instructions);

    /** 교사가 과거 PASSED Version으로 복원한 결정을 기록한다. */
    public void recordRestore(long teacherId, long sessionId, long restoredVersionId,
                              UUID requestId);

    /** 교사가 문제 전체 교체 결과를 채택한 결정을 기록한다. */
    public void recordReplacement(long teacherId, long sessionId, long replacementVersionId,
                                  UUID requestId,
                                  List<ProblemEditInstruction> instructions);

    /** 교사가 미확정 작성 세션을 폐기한 결정을 기록한다. */
    public void recordDiscard(long teacherId, long sessionId, Long currentVersionId);
}
```

The entity is immutable after creation: no update methods. It stores `eventKey UUID`, teacher/session/version IDs, type, `changeNaturesJson`, `targetTypesJson`, and `createdAt`. The repository signature is:

```java
/** 동일 업무 이벤트 키가 이미 저장됐는지 확인한다. */
boolean existsByEventKey(UUID eventKey);
```

Derive `eventKey` with `UUID.nameUUIDFromBytes` over UTF-8 `"teacher-decision:" + type + ":" + teacherId + ":" + sessionId + ":" + (versionId or 0) + ":" + (requestId or "none")`. Serialize only sorted distinct `EditChangeNature.name()` and `EditTargetType.name()` arrays. Do not serialize `targetKey` or `instruction`.

- [ ] **Step 4: Emit only after successful state changes**

Wire exact points:

- `APPROVED`: in `ProblemAuthoringFinalizationService`, after finalization has persisted the question and accepted Version.
- `MODIFICATION_STARTED`: in `ProblemEditConversationService.confirm`, after non-restore `session.activateEdit(...)` succeeds; retries use the deterministic key.
- `RESTORED`: in `ProblemEditConversationService.confirm`, after the existing `session.completeConfirmedRestore(...)` succeeds. The current application service intentionally does not dispatch restore plans to the coordinator.
- `REPLACED`: in `ProblemModificationExecutionCoordinator`, after bank-first replacement promotes its Version; for AI replacement, capture `CandidateProcessingResult` from `modificationWorker.execute(...)` and emit only when `plan.action()==REPLACE` and `result.promoted()==true`, using `result.versionId()`.
- `DISCARDED`: only in `ProblemDraftAssetCleanupService.cancelDraft`, after `session.cancelDraft()` succeeds and before file deletion. Reorder that method to state change → event insert → file deletion within its transaction. TTL expiry is not a teacher decision and emits no event.

Events participate in the same DB transaction as their state change. A unique-key race is treated as idempotent success. Do not add an event endpoint, scoring, labels, or C-stage ranker.

- [ ] **Step 5: Run event and authoring regression tests**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.service.ProblemTeacherDecisionEventServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemEditConversationServiceTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemModificationExecutionCoordinatorTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemDraftAssetCleanupServiceTest'`

Expected: BUILD SUCCESSFUL. Existing service outcomes are unchanged and each successful transition emits one structured event.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/entity/enums/TeacherDecisionType.java src/main/java/com/cenedu/backend/domain/problem/entity/ProblemTeacherDecisionEvent.java src/main/java/com/cenedu/backend/domain/problem/repository/ProblemTeacherDecisionEventRepository.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemTeacherDecisionEventService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationService.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinator.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetCleanupService.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemTeacherDecisionEventServiceTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinatorTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationServiceTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditConversationServiceTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetCleanupServiceTest.java
git commit -m "feat : 교사 문제 선택 결정 이벤트 추가"
```

---

### Task 10: Feature-off Fallback and Package-boundary Regression Suite

**Files:**
- Modify: `src/test/java/com/cenedu/backend/domain/problem/config/ProblemRagPropertiesTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemRagFallbackTest.java`
- Modify: `src/test/java/com/cenedu/backend/architecture/AiClientAccessTest.java`

**Interfaces:**
- Consumes: all A contracts and configuration defaults.
- Produces: executable proof that disabled A is behaviorally compatible and package ownership cannot drift.

- [ ] **Step 1: Add failing feature-off and architecture tests**

`ProblemRagPropertiesTest` uses `ApplicationContextRunner` only, binds no full application, and asserts exact defaults: enabled/indexing false, limits 40/3/4, lambdas 0.70/0.55, 2-second timeout, model policy `A_DENSE_MMR_V1`, worker batch 20/max attempts 3.

`ProblemRagFallbackTest` is pure Mockito and proves:

- disabled RAG never resolves/calls retrieval and generated command has only caller references;
- enabled retrieval exception does not fail `plan()`;
- disabled indexing never resolves/calls `SearchIndexingPort` during finalization/backfill;
- a telemetry linkage exception does not fail Job creation or promoted-version completion;
- no API request field can override candidate/selection limits or lambda.

Add these exact ArchUnit ownership rules:

```java
noClasses().that().resideInAPackage("..domain.problem..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "..infra.vector..", "..ai.embedding..", "com.openai..", "org.springframework.ai..");

noClasses().that().resideInAPackage("..infra.vector..")
        .should().dependOnClassesThat().resideInAPackage("com.openai..");

noClasses().that().resideOutsideOfPackages("..ai.client..", "..ai.embedding..")
        .should().dependOnClassesThat().resideInAPackage("com.openai..");
```

- [ ] **Step 2: Run new regression tests and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.config.ProblemRagPropertiesTest' --tests 'com.cenedu.backend.domain.problem.service.ProblemRagFallbackTest' --tests 'com.cenedu.backend.architecture.AiClientAccessTest'`

Expected: FAIL until defaults, optional providers, fallback catches, and ownership rules match the implementation.

- [ ] **Step 3: Correct only boundary/fallback defects exposed by red tests**

Use `ObjectProvider` only at optional RAG seams; do not make required pre-A services optional. Catch retrieval/trace runtime failures at planning/linkage boundaries, log only request/retrieval IDs and exception class, and return the existing path. Do not catch validation, ownership, finalization, generation, or verification exceptions under the RAG fallback.

If an ownership test finds an import violation, move provider logic to `ai.embedding`, SQL logic to `infra.vector`, or the contract to `domain.problem`; do not weaken the rule or add exclusions.

- [ ] **Step 4: Run focused pre-A and A regression suite**

Run:

```bash
bash gradlew test \
  --tests 'com.cenedu.backend.architecture.AiClientAccessTest' \
  --tests 'com.cenedu.backend.domain.problem.authoring.*' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationPlanTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationPlanningServiceTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationJobServiceTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationWorkerTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemRagFallbackTest' \
  --tests 'com.cenedu.backend.ai.embedding.*' \
  --tests 'com.cenedu.backend.ai.problem.adapter.*' \
  --tests 'com.cenedu.backend.infra.vector.DeterministicMmrSelectorTest'
```

Expected: BUILD SUCCESSFUL without Spring context or provider network calls.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/cenedu/backend/domain/problem/config/ProblemRagPropertiesTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemRagFallbackTest.java src/test/java/com/cenedu/backend/architecture/AiClientAccessTest.java
git commit -m "test : 문제 RAG fallback과 패키지 경계 고정"
```

---

### Task 11: Targeted PostgreSQL API Integration and Gated Real-Provider API Test

#### Embedding content policy (full problem-bank backfill)

The full problem-bank backfill uses an answer-safe derived document. It includes the
curriculum path, achievement standard, question type, difficulty, visible prompt,
solution strategy, solution summary, and presentation mode. It must not include raw
answers, answer units, choice correctness, full explanation text, rubric scoring
guidance, teacher prompts, or storage paths. A learning guide may contribute only the
answer-free concept/strategy summary fields. This keeps retrieval useful for concept
and solution-structure similarity without leaking answer-bearing content into the
generation few-shot path.

Before a full backfill, record the source count, pending/ready/failed counts, and
embedding model/dimensions. A full backfill is idempotent and may be resumed from the
last question ID. Retrieval quality is evaluated with representative queries after
the index is populated; cost is driven by input tokens, not vector dimensionality.

**Files:**
- Create: `src/test/java/com/cenedu/backend/domain/problem/controller/ProblemRagGenerationApiIntegrationTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/controller/ProblemRagGenerationApiLiveTest.java`

**Interfaces:**
- Consumes: real teacher HTTP endpoints, JWT security, PostgreSQL/pgvector/Flyway, planning, persisted Job/Item command JSON, and optionally real OpenAI embeddings.
- Produces: endpoint-level evidence that RAG references reach actual generation commands and that the provider contract works when explicitly enabled.

- [ ] **Step 1: Write the failing deterministic API integration test**

Use `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(PostgresTestcontainer.class)`, and these properties:

```java
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h",
        "app.problem.rag.enabled=true",
        "app.problem.rag.indexing.enabled=false"
})
```

Provide a `@TestConfiguration` `@Primary EmbeddingClient` that returns a deterministic 1024-float unit vector, and `@MockitoBean ProblemGenerationAsyncRunner` so the test inspects the queued persisted command without invoking generation. Seed the complete three-level curriculum path and a READY search row whose exact bank selector tuple does not match but whose RAG hard filters do match. Issue a real teacher JWT from `JwtProvider`.

Representative complete request/assertion:

```java
@Test
void generalAsyncApiPersistsSelectedExampleSnapshot() throws Exception {
    String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();

    MvcResult result = mockMvc.perform(post("/api/teacher/problems/generate/async")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"clientRequestId":"0d70d8de-94e7-4a22-94c5-4bb2397cb992",
                             "items":[{"subUnitId":30,"difficulty":2,"count":1}]}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andReturn();

    Number jobIdValue = JsonPath.read(
            result.getResponse().getContentAsString(), "$.data.jobId");
    long jobId = jobIdValue.longValue();
    String commandJson = jdbc.queryForObject("""
            SELECT generation_command FROM problem_generation_item
            WHERE job_id = ?
            """, String.class, jobId);
    ProblemGenerationCommand command = jsonCodec.read(commandJson, ProblemGenerationCommand.class);

    assertThat(command.references()).hasSize(1);
    assertThat(command.references().getFirst().role())
            .isEqualTo(GenerationReferenceRole.EXAMPLE);
    assertThat(command.references().getFirst().snapshot().contentBlocks().getFirst().text())
            .contains("RAG 후보 발문");
}
```

Add a second method for `POST /api/teacher/assessments/generate/async` proving type hard-filtering and exclusion. Add 401 and STUDENT 403 assertions only if not already covered by the controller security suite; the focus here is a TEACHER request through real planning and PostgreSQL.

- [ ] **Step 2: Run deterministic API test and verify red**

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.controller.ProblemRagGenerationApiIntegrationTest'`

Expected: FAIL until migrations, full curriculum enrichment, vector retrieval, command persistence, and JWT endpoint wiring all work together.

- [ ] **Step 3: Make the deterministic API test green without weakening it**

Correct only wiring/transaction/fixture defects. Do not mock `ProblemGenerationPlanningService`, `ProblemReferenceRetrievalPort`, JDBC repositories, security, Flyway, or JSON command persistence. Keep only embedding deterministic and async generation suppressed.

Run: `bash gradlew test --tests 'com.cenedu.backend.domain.problem.controller.ProblemRagGenerationApiIntegrationTest'`

Expected: BUILD SUCCESSFUL with no external API call.

- [ ] **Step 4: Add a gated real OpenAI embedding API test**

`ProblemRagGenerationApiLiveTest` uses the same real endpoint and PostgreSQL stack, has no fake `EmbeddingClient`, and is gated exactly:

```java
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
```

In setup, enqueue one validated Snapshot through `SearchIndexingPort`, run `ProblemSearchIndexWorker.runPending()` to create the real 1024-dimensional row, then call `POST /api/teacher/problems/generate/async` with a teacher JWT. Keep `ProblemGenerationAsyncRunner` mocked so this test calls the real embeddings API but not the generation LLM. Assert HTTP 200, persisted EXAMPLE Snapshot, non-empty embedding model, `embedding_dimensions=1024`, and a completed retrieval trace. Do not print document text, Snapshot, token, or API key.

Run with an already-exported real key:

```bash
JWT_SECRET=cen-edu-temporary-test-secret-32-bytes-minimum \
  bash gradlew test \
  --tests 'com.cenedu.backend.domain.problem.controller.ProblemRagGenerationApiLiveTest'
```

Expected with `OPENAI_API_KEY` unset: the live test is SKIPPED. Expected with a valid exported key: BUILD SUCCESSFUL and the test performs real embedding calls. Never put either key in source, Gradle properties, `.env`, test reports, or shell history beyond this documented non-secret JWT literal.

- [ ] **Step 5: Run all targeted database/API tests together**

Run:

```bash
JWT_SECRET=cen-edu-temporary-test-secret-32-bytes-minimum \
  bash gradlew test \
  --tests 'com.cenedu.backend.infra.vector.ProblemSearchSchemaMigrationTest' \
  --tests 'com.cenedu.backend.infra.vector.ProblemSearchIndexWorkerIntegrationTest' \
  --tests 'com.cenedu.backend.infra.vector.PgVectorProblemReferenceRetrievalAdapterIntegrationTest' \
  --tests 'com.cenedu.backend.domain.problem.controller.ProblemRagGenerationApiIntegrationTest'
```

Expected: BUILD SUCCESSFUL. The inline JWT value is temporary, non-secret, longer than 32 bytes, and is not written to any file.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/cenedu/backend/domain/problem/controller/ProblemRagGenerationApiIntegrationTest.java src/test/java/com/cenedu/backend/domain/problem/controller/ProblemRagGenerationApiLiveTest.java
git commit -m "test : 문제 RAG 실제 API와 pgvector 통합 검증"
```

---

## Final Verification

- [ ] Run formatting/patch hygiene: `git diff --check`

Expected: no output and exit code 0.

- [ ] Confirm the migration filename is unique and exact:

Run: `find src/main/resources/db/migration -maxdepth 1 -name '*problem_create_search_index.sql' -print`

Expected: exactly `src/main/resources/db/migration/V20260819_0900__problem_create_search_index.sql`.

- [ ] Run the pure/unit suite without loading Spring context:

```bash
bash gradlew test \
  --tests 'com.cenedu.backend.architecture.AiClientAccessTest' \
  --tests 'com.cenedu.backend.domain.problem.authoring.*' \
  --tests 'com.cenedu.backend.domain.problem.config.ProblemRagPropertiesTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationPlanningServiceTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationJobServiceTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemGenerationWorkerTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemTeacherDecisionEventServiceTest' \
  --tests 'com.cenedu.backend.domain.problem.service.ProblemRagFallbackTest' \
  --tests 'com.cenedu.backend.ai.embedding.OpenAiEmbeddingClientTest' \
  --tests 'com.cenedu.backend.ai.problem.adapter.FewShotReferenceSerializerTest' \
  --tests 'com.cenedu.backend.ai.problem.adapter.SpringAiProblemGenerationAdapterTest' \
  --tests 'com.cenedu.backend.infra.vector.DeterministicMmrSelectorTest'
```

Expected: BUILD SUCCESSFUL with no PostgreSQL container and no external API request.

- [ ] Run all tests with the required JWT environment variable supplied only for this process:

```bash
JWT_SECRET=cen-edu-temporary-test-secret-32-bytes-minimum bash gradlew test
```

Expected: compilation and the full test suite succeed; real-provider tests are skipped unless their explicit environment gates are present. If this same command fails, treat the failure as implementation work. A bare `bash gradlew test` without `JWT_SECRET` may reproduce the pre-existing `PlaceholderResolutionException` cascade and is not the acceptance command.

- [ ] Inspect changed paths and scope:

Run: `git status --short && git diff --name-only origin/develop...HEAD`

Expected: only A-stage curriculum, Problem-owned contracts/orchestration, embedding adapter, vector infrastructure, migration/configuration, and their tests are changed. There are no B semantic-authoring packages, C reranker/scoring changes, secrets, generated test reports, or unrelated files.

- [ ] Confirm no secret or answer-bearing telemetry fields were introduced:

Run: `rg -n 'OPENAI_API_KEY=|JWT_SECRET=|answerRaw|answerNormalized|teacherPrompt|systemPrompt|documentText|snapshot' src/main/java/com/cenedu/backend/infra/vector src/main/java/com/cenedu/backend/domain/problem/entity/ProblemTeacherDecisionEvent.java`

Expected: no committed secret assignments; `answerRaw`/`answerNormalized` do not appear in index/trace/event persistence; allowed `documentText` and `snapshot` references occur only in the internal searchable index write/read path, never retrieval trace or teacher event columns.
