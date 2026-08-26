# Problem Edit Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 자연어 문제 수정 요청이 출력 가드, semantic 편집, 문제은행 교체, 유사 예시 기반 생성까지 성공하도록 복구한다.

**Architecture:** 기존 Dispatcher와 Problem 도메인 Port 경계를 유지한다. 문제은행 exact-match를 먼저 사용하고, miss일 때만 기존 pgvector retrieval을 호출해 정답 없는 EXAMPLE을 수정 LLM에 전달한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Jackson 2/3, Spring Data JPA, PostgreSQL 17 + pgvector, Gradle/JUnit 5/AssertJ/Mockito

**Spec:** `docs/superpowers/specs/2026-08-26-problem-edit-recovery-design.md`

## Global Constraints

- 사용자 지시에 따라 RED→GREEN 테스트 절차를 사용하지 않는다.
- 각 작업은 구현, 유지할 회귀 테스트 작성, 관련 테스트 실행, 커밋 순서로 완료한다.
- 일회성 진단 테스트는 사용 후 제거하고 영구 회귀 가치가 있는 테스트만 남긴다.
- 전체 `./gradlew build`는 모든 작업이 끝난 마지막 검증에서만 실행한다.
- 사용자 입력 원문과 정답을 로그에 남기지 않는다.
- 사용자 프롬프트 LLM 호출은 `AgentDispatcher` 경계를 유지한다.
- 새로운 DB migration과 환경 변수는 추가하지 않는다.

---

### Task 1: 요청 스펙 직렬화와 출력 가드 복구

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/RequestedProblemSpecification.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/agent/ProblemEditAgent.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/agent/ProblemEditOutputGuard.java`
- Modify: `src/test/java/com/cenedu/backend/ai/problem/agent/ProblemEditOutputGuardTest.java`
- Modify: `src/test/java/com/cenedu/backend/ai/problem/agent/ProblemEditAgentRequestedSpecificationTest.java`

**Interfaces:**
- Produces: `RequestedProblemSpecification.hasNoCriteria()` and typed-result-first output guard conversion.

- [ ] Rename `isEmpty()` to `hasNoCriteria()` and update Agent normalization.
- [ ] Make the output guard directly consume `ProblemEditConversationResult` instances and convert only Map-like values.
- [ ] Add safe exception-type logging without prompt or answer content.
- [ ] Keep permanent tests for non-null requested specification, empty-spec JSON serialization, and Map fallback conversion.
- [ ] Run only the two Agent/guard test classes.
- [ ] Commit as `fix : 문제 수정 요청 스펙 출력 가드 복구`.

### Task 2: OpenAI strict 수정 schema 복구

**Files:**
- Modify: `src/main/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemas.java`
- Create: `src/test/java/com/cenedu/backend/ai/problem/ProblemModificationOutputSchemaTest.java`

**Interfaces:**
- Produces: `modificationDeltaFor(Set<EditTargetType>)` returning a CANDIDATE-subset strict schema.

- [ ] Rebuild modification delta objects from `CANDIDATE_NODE.properties` and populate root `required`.
- [ ] Cover CHOICE, CONTENT_BLOCK, ANSWER_UNIT, empty target, and WHOLE_QUESTION behavior with permanent tests.
- [ ] Recursively assert that every object node has `additionalProperties:false`.
- [ ] Run only `ProblemModificationOutputSchemaTest` and existing repair schema tests if present.
- [ ] Commit as `fix : 문제 수정 구조화 출력 스키마 복구`.

### Task 3: Semantic 추출 계약과 1회 교정 복구

**Files:**
- Modify: `src/main/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemas.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticExtractionAdapter.java`
- Modify or create focused tests under `src/test/java/com/cenedu/backend/ai/problem/adapter/semantic/`
- Reuse: `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticSnapshotFactoryTypeCoverageTest.java`

**Interfaces:**
- Produces: parameter key schema pattern `^[A-Z][A-Z0-9_]{0,63}$` and at-most-one validation repair call.

- [ ] Constrain semantic parameter keys in the transformed provider schema.
- [ ] Add explicit numeric bounds/current-value/editable rules to the extraction prompt.
- [ ] On materializer/validator failure, issue one structured correction call containing only bounded validation findings and retry parse/materialize once.
- [ ] Preserve existing status classification when the correction also fails.
- [ ] Add permanent tests for key schema pattern, one correction success, and exactly-one correction failure.
- [ ] Run only semantic extraction, materialization type coverage, and parameter patch tests.
- [ ] Commit as `fix : semantic 추출 계약 및 교정 경로 보강`.

### Task 4: 문제은행 자산 조건 선필터

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/repository/ProblemAssetRepository.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemQuestionSelector.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinator.java`
- Create or modify focused tests under `src/test/java/com/cenedu/backend/domain/problem/service/`

**Interfaces:**
- Produces: selector overload accepting `Boolean requiresAsset`, applying asset presence before shuffle/limit.

- [ ] Add a repository projection that returns question IDs having assets for a candidate ID collection.
- [ ] Filter the full candidate pool by asset presence before shuffle and count limit.
- [ ] Pass `RequestedProblemSpecification.requiresAsset()` from bank replacement execution.
- [ ] Keep existing selector callers compatible through the current overload.
- [ ] Add a permanent test where the sole asset candidate lies outside the first eight original candidates.
- [ ] Run only selector and bank replacement tests.
- [ ] Commit as `fix : 문제은행 교체 자산 조건 선필터 적용`.

### Task 5: 문제은행 miss의 유사 예시 검색 배선

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQuery.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactory.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/edit/ProblemModificationCommand.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationExecutionCoordinator.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/ModificationPromptStrategy.java`
- Modify or create focused retrieval/modification tests.

**Interfaces:**
- Produces: optional `ProblemReferenceQuery.queryHint`, modification command curriculum/references, and `FEW_SHOT_JSON` modification prompt section.

- [ ] Add backward-compatible query constructors defaulting `queryHint` to null.
- [ ] Append bounded normalized query hint to pgvector query documents without changing indexed documents.
- [ ] Retrieve up to four EXAMPLE references only for REPLACE after bank miss or GENERATE_ONLY, with RAG-enabled/provider/origin/curriculum guards.
- [ ] Convert retrieval failure and empty results to an empty-reference fallback.
- [ ] Pass curriculum and examples through `ProblemModificationCommand`.
- [ ] Serialize examples with `FewShotReferenceSerializer` so answers remain excluded.
- [ ] Add permanent tests for bank hit no-retrieval, bank miss query hint/reference propagation, retrieval failure fallback, and prompt answer exclusion.
- [ ] Run only retrieval and modification coordinator/prompt tests.
- [ ] Commit as `feat : 문제 수정 생성 경로에 유사 예시 검색 배선`.

### Task 6: 최종 통합 검증

**Files:**
- Modify only if verification exposes a scoped regression.

**Interfaces:**
- Consumes all prior task contracts.

- [ ] Run the complete relevant problem-edit test selection once.
- [ ] Run `./gradlew build` once as the final build.
- [ ] Inspect `git diff --check`, `git status --short`, and recent commits.
- [ ] If a scoped regression requires a fix, amend through a separate final fix commit and rerun the failed verification plus final build.
