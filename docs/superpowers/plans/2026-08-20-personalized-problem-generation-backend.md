# Personalized Problem Generation Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 취약점 분석의 최신 재출제 제안을 서버에서 다시 검증하고, 복습·유사·응용 문항을 기존 비동기 생성·검증·수정 파이프라인으로 만드는 백엔드 API를 제공한다.

**Architecture:** `CustomProblemGenerationService`가 분석 도메인의 public `ReissueProposalService`를 호출해 최신 제안을 얻고, 문제 도메인의 전용 계획기가 단계별 `ProblemGenerationPlan`을 만든다. REVIEW는 검증된 문제은행 Snapshot을 즉시 재사용하고, SIMILAR는 벡터 검색 결과를 먼저 재사용한 뒤 부족분만 AI로 만들며, ADVANCED는 구조화된 취약 근거를 포함해 전부 AI로 만든다. 계획 이후의 Job 저장, 병렬 실행, 검증, 재시도, 작성 세션, 미리보기와 후속 AI 수정은 기존 서비스를 그대로 사용한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC/Security, Spring Data JPA, PostgreSQL 17, Flyway, JUnit 5, AssertJ, Mockito, Testcontainers, Gradle

**Spec:** `docs/superpowers/specs/2026-08-20-personalized-problem-generation-design.md`

## Global Constraints

- 작업 시작 시 현재 dirty worktree를 다시 확인한다. 특히 현재 겹쳐 있는 `ErrorCode.java`, `ProblemGenerationItem.java`, `ProblemAsyncGenerationService.java`, `ProblemGenerationWorker.java`와 AI Client 계열의 사용자 변경을 보존하고 덮어쓰지 않는다.
- 실행 시작 전에 이미 수정된 파일은 task 커밋에서 통째로 stage하지 않는다. 기존 변경이 먼저 별도 커밋되지 않았다면 해당 task의 커밋만 보류하고 테스트 결과와 맞춤 기능 diff를 유지한다.
- 다른 도메인의 Repository나 Entity를 직접 참조하지 않는다. 분석 데이터는 `ReissueProposalService#getProposal`, 교육과정 데이터는 `CurriculumUnitQueryService`, 문제 Snapshot은 문제 도메인 public Service로만 얻는다.
- 이 기능은 자유 입력이 없는 시스템 트리거다. `AgentDispatcher`를 호출하지 않고 기존 `ProblemGenerationPort` → `ai/problem/adapter` 경로를 사용한다.
- 학생 답안 원문과 필기 인식 문자열은 생성 명령, 프롬프트, 로그에 넣지 않는다.
- 모든 새 Service/Repository 메서드에는 업무 의미를 설명하는 한 줄 주석을 단다.
- 적용된 Flyway 파일은 수정하지 않고 새 타임스탬프 파일만 추가한다.
- 시간 제약상 각 task는 필요한 코드와 테스트를 먼저 모두 작성한 뒤, 해당 task의 테스트를 실행하고 다음 task로 넘어간다. 구현 전 실패 테스트를 별도로 실행하는 Red 단계는 수행하지 않는다.
- 기존 일반학습·종합평가 요청/응답 계약은 유지한다. record 필드 추가가 필요한 경우 기존 생성자를 호환 overload로 남긴다.

## Accelerated Execution Order

각 Task는 아래 순서로 실행한다.

1. 해당 Task의 main 코드, DTO, migration, 테스트 코드를 모두 구현한다.
2. 해당 Task에 적힌 테스트 명령을 실행한다.
3. 실패하면 현재 Task 안에서 수정하고 같은 테스트를 다시 실행한다.
4. 테스트가 통과한 뒤에만 다음 Task로 이동한다.

각 Task의 테스트는 코드 구현이 끝난 뒤 처음 실행한다. 구현 전에 테스트를 실행하거나 실패 상태를 확인하기 위한 별도 실행은 생략한다.

---

## Task 1: `CustomStage`를 공통 소유 경계로 이동

**Files:**

- Create: `src/main/java/com/cenedu/backend/global/common/enums/CustomStage.java`
- Delete: `src/main/java/com/cenedu/backend/domain/worksheet/entity/enums/CustomStage.java`
- Modify: `src/main/java/com/cenedu/backend/domain/worksheet/entity/WorksheetItem.java`
- Modify: `src/main/java/com/cenedu/backend/domain/worksheet/service/WorksheetCommandService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/worksheet/service/StudentWorksheetQueryService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/worksheet/dto/response/StudentAssignmentResponse.java`
- Modify: `src/main/java/com/cenedu/backend/domain/worksheet/dto/response/WorksheetResponseFormatter.java`
- Modify: `src/main/java/com/cenedu/backend/domain/analysis/service/CustomLearningQueryService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/analysis/repository/CustomLearningQueryRepository.java`
- Modify: `src/main/java/com/cenedu/backend/domain/analysis/repository/row/CustomLearningSessionRow.java`
- Modify: `src/main/java/com/cenedu/backend/domain/analysis/dto/response/CustomLearningSessionListResponse.java`
- Modify: tests returned by `rg -l 'domain\.worksheet\.entity\.enums\.CustomStage' src/test/java`

- [ ] **Step 1: 공통 enum과 소유 경계 코드를 구현하고 검증 테스트를 추가한다**

`src/test/java/com/cenedu/backend/architecture/CommonEnumOwnershipTest.java`에 worksheet 전용 `CustomStage`가 더 이상 존재하지 않고 공통 enum이 로드되는지 검증한다.

```java
@Test
void customStageIsOwnedByGlobalCommonEnums() throws Exception {
    assertThat(Class.forName("com.cenedu.backend.global.common.enums.CustomStage")).isNotNull();
    assertThatThrownBy(() -> Class.forName(
            "com.cenedu.backend.domain.worksheet.entity.enums.CustomStage"))
            .isInstanceOf(ClassNotFoundException.class);
}
```

- [ ] **Step 2: 공통 enum 소유 경계 테스트를 실행한다**

Run: `./gradlew test --tests '*CommonEnumOwnershipTest'`

Expected: PASS.

- [ ] **Step 3: enum을 이동하고 모든 main/test import를 기계적으로 교체한다**

```java
package com.cenedu.backend.global.common.enums;

/** 맞춤 학습의 복습·유사·응용 단계를 여러 도메인이 공유하는 저장 축이다. */
public enum CustomStage {
    REVIEW,
    SIMILAR,
    ADVANCED
}
```

DB enum 문자열은 동일하므로 migration은 만들지 않는다. worksheet API의 `retrace/basic/independent` 변환은 기존 `WorksheetResponseFormatter`와 `WorksheetCommandService`에 유지한다.

- [ ] **Step 4: 관련 분석·학습지 회귀 테스트를 실행한다**

Run: `./gradlew test --tests '*CommonEnumOwnershipTest' --tests '*CustomLearningQueryServiceTest' --tests '*CustomLearningQueryRepositoryTest'`

Expected: PASS.

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/global/common/enums/CustomStage.java src/main/java/com/cenedu/backend/domain/worksheet src/main/java/com/cenedu/backend/domain/analysis src/test/java/com/cenedu/backend/architecture/CommonEnumOwnershipTest.java src/test/java/com/cenedu/backend/domain/analysis
git commit -m "refactor : 맞춤 학습 단계를 공통 enum으로 이동"
```

## Task 2: 유사 ORIGIN이 없는 재출제 제안을 0건으로 보정

**Files:**

- Create: `src/test/java/com/cenedu/backend/domain/analysis/reissue/ReissueProposalServiceTest.java`
- Modify: `src/main/java/com/cenedu/backend/domain/analysis/reissue/ReissueProposalService.java`

- [ ] **Step 1: 유사 ORIGIN 보정 코드를 구현하고 0/0 검증 테스트를 추가한다**

`AnalysisClassQueryService`와 `ReissueProposalRepository`를 mock하고, 채점 완료·소단원 1개·오답 0개 조건을 만든다.

```java
ReissueProposalResponse response = service.getProposal(7L, 120L, 35L);

assertThat(response.subcategories().getFirst().similar().proposedCount()).isZero();
assertThat(response.subcategories().getFirst().similar().maxCount()).isZero();
assertThat(response.subcategories().getFirst().similar().referenceQuestions()).isEmpty();
```

- [ ] **Step 2: 재출제 제안 테스트를 실행한다**

Run: `./gradlew test --tests '*ReissueProposalServiceTest'`

Expected: PASS.

- [ ] **Step 3: `toSimilar`에서 ORIGIN 존재 여부를 단일 규칙으로 적용한다**

```java
int proposedCount = references.isEmpty() ? 0 : DEFAULT_SIMILAR_COUNT;
int maxCount = references.isEmpty() ? 0 : MAX_PROPOSED_COUNT;
return new ReissueProposalResponse.SimilarProposal(
        proposedCount, maxCount, DifficultyLadder.code(difficulty), references, excluded);
```

- [ ] **Step 4: ORIGIN이 있는 기존 기본값 5/10 테스트도 추가해 통과시킨다**

Run: `./gradlew test --tests '*ReissueProposalServiceTest' --tests '*ReissueGuidanceWriterTest'`

Expected: PASS.

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/analysis/reissue/ReissueProposalService.java src/test/java/com/cenedu/backend/domain/analysis/reissue/ReissueProposalServiceTest.java
git commit -m "fix : 유사 출제 근거 없는 제안을 0건으로 보정"
```

## Task 3: 맞춤 생성 요청 DTO와 서버 검증 정책 추가

**Files:**

- Create: `src/main/java/com/cenedu/backend/domain/problem/dto/request/CustomProblemGenerationRequest.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/dto/request/CustomProblemGenerationItemRequest.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationRequestValidator.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationRequestValidatorTest.java`
- Modify: `src/main/java/com/cenedu/backend/global/common/ErrorCode.java`

- [ ] **Step 1: 요청 DTO·ErrorCode·정책 Validator를 구현하고 검증 테스트를 추가한다**

Validator 공개 계약은 다음으로 고정한다.

```java
/** 최신 분석 제안과 교사 수량 요청이 맞춤 생성 정책을 만족하는지 확인한다. */
public void validate(CustomProblemGenerationRequest request,
                     ReissueProposalResponse latestProposal)
```

각 실패가 다음 `ErrorCode`를 반환하는지 검증한다.

```java
CUSTOM_PROBLEM_EMPTY_SELECTION
CUSTOM_PROBLEM_TOTAL_LIMIT_EXCEEDED
CUSTOM_PROBLEM_SUB_UNIT_DUPLICATED
CUSTOM_PROBLEM_SUB_UNIT_NOT_PROPOSED
CUSTOM_PROBLEM_COUNT_EXCEEDS_PROPOSAL
CUSTOM_PROBLEM_SIMILAR_REFERENCE_MISSING
CUSTOM_PROBLEM_ADVANCED_NOT_ALLOWED
```

- [ ] **Step 2: Validator 테스트를 실행한다**

Run: `./gradlew test --tests '*CustomProblemGenerationRequestValidatorTest'`

Expected: PASS.

- [ ] **Step 3: Bean Validation DTO를 추가한다**

```java
public record CustomProblemGenerationRequest(
        @NotNull UUID clientRequestId,
        @NotNull @Positive Long sourceAssignmentId,
        @NotNull @Positive Long studentId,
        @NotNull @Size(max = 20) @Valid
        List<CustomProblemGenerationItemRequest> items) {
}

public record CustomProblemGenerationItemRequest(
        @NotNull @Positive Long subUnitId,
        @Min(0) @Max(10) int reviewCount,
        @Min(0) @Max(10) int similarCount,
        @Min(0) @Max(10) int advancedCount) {
    public int totalCount() {
        return reviewCount + similarCount + advancedCount;
    }
}
```

- [ ] **Step 4: `ErrorCode`의 현재 사용자 변경을 보존한 채 문제 영역에 오류를 병합한다**

HTTP 상태는 빈 선택·총량·중복·제안 불일치·ORIGIN·응용 조건 모두 `BAD_REQUEST`로 둔다. Validator는 요청을 제안의 `subUnitId` Map에 대응시키고 조용히 수량을 줄이지 않는다.

- [ ] **Step 5: Validator 테스트를 통과시킨다**

Run: `./gradlew test --tests '*CustomProblemGenerationRequestValidatorTest'`

Expected: PASS.

- [ ] **Step 6: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/dto/request src/main/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationRequestValidator.java src/main/java/com/cenedu/backend/global/common/ErrorCode.java src/test/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationRequestValidatorTest.java
git commit -m "feat : 맞춤 문제 생성 요청 검증 추가"
```

`ErrorCode.java`가 실행 시작 전부터 dirty라면 위 커밋에는 넣지 않고 기존 오류 변경과 맞춤 오류 hunk를 함께 보존한다.

## Task 4: 구조화된 취약 근거를 생성 명령과 프롬프트에 보존

**Files:**

- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/PersonalizedGenerationEvidence.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationEvaluationAreaEvidence.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationDiagnosticEvidence.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationCommand.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPromptFactory.java`
- Create: `src/test/java/com/cenedu/backend/ai/problem/adapter/PersonalizedProblemGenerationPromptTest.java`
- Modify: existing tests that instantiate `ProblemGenerationCommand` only if compilation requires it

- [ ] **Step 1: 구조화된 취약 근거 타입·명령·프롬프트 코드를 구현하고 검증 테스트를 추가한다**

`PERSONALIZED_APPLICATION` 명령을 만들고 두 Prompt Factory 결과에 아래 JSON 키가 들어가는지 검증한다.

```java
assertThat(prompt).contains("personalizedEvidence", "historicalIncorrectItemCount",
        "evaluationAreaEvidence", "diagnosticEvidence");
assertThat(prompt).doesNotContain("studentAnswer", "handwritingText");
```

- [ ] **Step 2: 프롬프트 및 Worker 회귀 테스트를 실행한다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPromptTest'`

Expected: PASS.

- [ ] **Step 3: 문제 도메인 소유의 독립 근거 타입을 추가한다**

```java
public record PersonalizedGenerationEvidence(
        int historicalIncorrectItemCount,
        int incorrectSessionCount,
        List<GenerationEvaluationAreaEvidence> evaluationAreaEvidence,
        List<GenerationDiagnosticEvidence> diagnosticEvidence) {
}

public record GenerationEvaluationAreaEvidence(
        EvaluationArea evaluationArea, int gradedItemCount,
        int incorrectItemCount, BigDecimal incorrectRate) {
}

public record GenerationDiagnosticEvidence(
        DiagnosticType diagnosticType, int gradedUnitCount,
        int incorrectUnitCount, BigDecimal incorrectRate) {
}
```

`ProblemGenerationCommand`의 마지막 필드로 nullable `PersonalizedGenerationEvidence personalizedEvidence`를 추가하고, 기존 7인자 생성자는 `null`로 위임해 일반·종합 호출부를 호환한다. 근거가 null일 수 있으므로 semantic factory도 `Map.of` 대신 `LinkedHashMap`으로 request를 조립한다.

- [ ] **Step 4: retry/enrichment 복사 경로에서 근거 필드를 누락하지 않게 전달한다**

`ProblemSemanticReferenceEnricher#enrichWithStatus`와 `ProblemGenerationWorker#commandForAttempt`의 새 명령 생성 시 `command.personalizedEvidence()`를 마지막 인자로 전달한다.

- [ ] **Step 5: 두 Prompt Factory의 CURRENT_REQUEST_JSON에 근거를 추가한다**

Legacy와 semantic 경로 모두 `personalizedEvidence`를 직렬화한다. 로그에는 근거 배열 내용이나 Snapshot 원문을 추가하지 않는다.

- [ ] **Step 6: 프롬프트와 Worker 회귀 테스트를 실행한다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPromptTest' --tests '*ProblemGenerationWorkerTest' --tests '*ProblemSemanticGenerationPipelineTest'`

Expected: PASS.

- [ ] **Step 7: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/generation/PersonalizedGenerationEvidence.java src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationEvaluationAreaEvidence.java src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationDiagnosticEvidence.java src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationCommand.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPromptFactory.java src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPromptFactory.java src/test/java/com/cenedu/backend/ai/problem/adapter/PersonalizedProblemGenerationPromptTest.java
git commit -m "feat : 응용 문제 생성에 구조화된 취약 근거 전달"
```

`ProblemGenerationWorker.java`가 실행 시작 전부터 dirty라면 위 커밋에는 넣지 않고, 근거 전달 hunk를 작업 트리에 보존한 채 그 파일의 기존 변경이 정리된 후 별도로 커밋한다.

## Task 5: 생성 슬롯의 맞춤 단계와 ORIGIN 메타데이터 영속화

**Files:**

- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationSlotPlan.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationPlan.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/entity/ProblemGenerationItem.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationItemResult.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobService.java`
- Create: `src/main/resources/db/migration/V20260820_1200__problem_add_custom_generation_metadata.sql`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationPlanTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobServiceTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/repository/ProblemGenerationItemRepositoryTest.java`

- [x] **Step 1: 슬롯·Entity·결과 메타데이터와 migration을 구현하고 round-trip 테스트를 추가한다**

검증할 조합은 다음과 같다.

- `PERSONALIZED` Job의 모든 슬롯은 `customStage != null`
- 일반·종합 Job의 모든 슬롯은 `customStage == null && originQuestionId == null`
- REVIEW는 `BANK_REUSE`, `sourceQuestionId != null`, `originQuestionId == null`
- SIMILAR의 BANK_REUSE는 `sourceQuestionId != null`, AI는 `originQuestionId != null`
- ADVANCED는 `AI_GENERATION`, `originQuestionId != null`

- [x] **Step 2: 생성 Job 메타데이터 테스트를 실행한다**

Run: `./gradlew test --tests '*ProblemGenerationPlanTest' --tests '*ProblemGenerationJobServiceTest'`

Expected: PASS.

- [x] **Step 3: 슬롯·Entity·결과 계약에 메타데이터를 추가한다**

`ProblemGenerationSlotPlan` canonical 필드는 아래 순서로 고정한다.

```java
int slotIndex,
GenerationSlotSource source,
Long sourceQuestionId,
Long originQuestionId,
CustomStage customStage,
QuestionSnapshotV1 sourceSnapshot,
Map<String, String> sourceAssetStorageKeys,
ProblemGenerationCommand generationCommand
```

기존 4/5/6인자 편의 생성자는 `originQuestionId=null`, `customStage=null`로 유지한다. `ProblemGenerationItem`에는 `@Enumerated STRING customStage`, `originQuestionId`를 추가하고 기존 factory는 null 메타데이터로 위임한다. 맞춤 계획 저장용 factory overload는 단계와 ORIGIN을 필수 인자로 받는다.

- [x] **Step 4: Job 저장과 조회 매핑을 확장한다**

`ProblemGenerationJobService#createPlanned`가 BANK/AI 모두 `slot.customStage()`와 `slot.originQuestionId()`를 Entity에 전달한다. `toResult`는 다음 필드를 반환한다.

```java
Long itemId, Long sessionId, int itemOrder,
GenerationItemStatus status, short retryCount, String errorCode,
GenerationSlotSource source, Long sourceQuestionId,
Long originQuestionId, CustomStage customStage
```

- [x] **Step 5: 새 Flyway migration을 추가한다**

```sql
ALTER TABLE problem_generation_item ADD COLUMN custom_stage VARCHAR(20);
ALTER TABLE problem_generation_item ADD COLUMN origin_question_id BIGINT;
ALTER TABLE problem_generation_item ADD CONSTRAINT ck_problem_generation_item_custom_stage
    CHECK (custom_stage IS NULL OR custom_stage IN ('REVIEW', 'SIMILAR', 'ADVANCED'));
ALTER TABLE problem_generation_item ADD CONSTRAINT fk_problem_generation_item_origin_question
    FOREIGN KEY (origin_question_id) REFERENCES problem_question(id);
CREATE INDEX idx_problem_generation_item_origin_question
    ON problem_generation_item(origin_question_id);
```

Job 유형과 단계 조합은 item 테이블만으로 job type을 알 수 없으므로 `ProblemGenerationPlan`/`ProblemGenerationJobService`에서 검증한다. 기존 migration은 수정하지 않는다.

- [x] **Step 6: PostgreSQL 저장 round-trip과 단위 테스트를 통과시킨다**

Run: `./gradlew test --tests '*ProblemGenerationPlanTest' --tests '*ProblemGenerationJobServiceTest' --tests '*ProblemGenerationItemRepositoryTest'`

Expected: PASS, Testcontainers PostgreSQL에서 `CUSTOM_STAGE`와 ORIGIN FK 저장/조회 확인.

- [x] **Step 7: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationSlotPlan.java src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationPlan.java src/main/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationItemResult.java src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobService.java src/main/resources/db/migration/V20260820_1200__problem_add_custom_generation_metadata.sql src/test/java/com/cenedu/backend/domain/problem/authoring/generation/ProblemGenerationPlanTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationJobServiceTest.java src/test/java/com/cenedu/backend/domain/problem/repository/ProblemGenerationItemRepositoryTest.java
git commit -m "feat : 맞춤 생성 슬롯 단계 메타데이터 저장"
```

`ProblemGenerationItem.java`가 실행 시작 전부터 dirty라면 위 커밋에는 넣지 않고 Task 4와 같은 보존 규칙을 적용한다.

## Task 6: 맞춤 계획기의 REVIEW와 단계 우선 순서 구현

**Files:**

- Create: `src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningServiceTest.java`

- [x] **Step 1: 맞춤 계획기와 REVIEW 슬롯 조립 코드를 구현하고 순서 테스트를 추가한다**

테스트 제안은 교육과정 순서가 `[subUnit 20, subUnit 30]`이고 각 단계 수량이 섞이게 만든다. 결과 순서는 모든 REVIEW → 모든 SIMILAR → 모든 ADVANCED이며, REVIEW source ID는 `candidateQuestionIds` 앞에서부터 정확히 일치해야 한다.

Planner 공개 계약은 다음으로 고정한다.

```java
/** 최신 재출제 제안과 교사 수량을 실행 가능한 맞춤 생성 계획으로 변환한다. */
public ProblemGenerationPlan plan(
        UUID clientRequestId,
        ReissueProposalResponse proposal,
        List<CustomProblemGenerationItemRequest> items,
        Map<Long, CurriculumPathResponse> curriculumPaths)
```

- [x] **Step 2: REVIEW 및 단계 순서 테스트를 실행한다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPlanningServiceTest'`

Expected: PASS.

- [x] **Step 3: 단계별 3-pass 조립 뼈대를 구현한다**

요청 Map은 수량 lookup에만 쓰고, 반복 순서는 `proposal.subcategories()` 순서를 사용한다. `appendReviewSlots`, `appendSimilarSlots`, `appendAdvancedSlots`를 순서대로 호출하고 마지막에 연속 `slotIndex`를 부여한다.

- [x] **Step 4: REVIEW Snapshot을 public 문제 Service로 조회해 BANK_REUSE로 만든다**

선택한 후보 ID를 한 번에 `ProblemBankSnapshotQueryService#getSnapshots`로 가져와 ID Map으로 만든다. 누락되거나 `reusable=false`인 후보는 `BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID)`로 거절하고 다른 문항으로 조용히 대체하지 않는다.

```java
new ProblemGenerationSlotPlan(index, BANK_REUSE, questionId, null,
        CustomStage.REVIEW, snapshot, assetKeys, null)
```

- [x] **Step 5: REVIEW와 순서 테스트를 통과시킨다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPlanningServiceTest.review*' --tests '*PersonalizedProblemGenerationPlanningServiceTest.stage*'`

Expected: PASS.

- [x] **Step 6: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java src/test/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningServiceTest.java
git commit -m "feat : 맞춤 복습 슬롯과 단계 순서 계획"
```

## Task 7: SIMILAR 벡터 재사용과 AI 부족분 구현

**Files:**

- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningServiceTest.java`

- [x] **Step 1: SIMILAR 검색·제외·은행 우선·AI 부족분 코드를 구현하고 검증 테스트를 추가한다**

다음을 한 테스트 fixture에서 검증한다.

- `similar.referenceQuestions().getFirst()`가 ORIGIN이다.
- retrieval query의 제외 집합은 `similar.excludedQuestionIds` + 현재 Job에서 이미 재사용한 ID다.
- retrieval 결과 최대 4개는 SIMILAR `BANK_REUSE` 슬롯이 된다.
- 요청 수량이 검색 결과보다 크면 차이만큼 `PERSONALIZED_SIMILAR_SHORTAGE` AI 슬롯이 생긴다.
- AI 명령 references는 ORIGIN 1개와 나머지 오답/검색 후보 EXAMPLE을 포함한다.
- AI 슬롯의 `sourceQuestionId=null`, `originQuestionId=ORIGIN ID`, `customStage=SIMILAR`이다.

- [x] **Step 2: SIMILAR 계획 테스트를 실행한다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPlanningServiceTest.similar*'`

Expected: PASS.

- [x] **Step 3: RAG 활성 상태에서 개인화 retrieval query를 만든다**

계획기는 기존 방식과 동일하게 `ObjectProvider<ProblemReferenceRetrievalPort>`와 `ProblemRagProperties`를 주입받는다. query 핵심값은 다음과 같다.

```java
new ProblemReferenceQuery(
        retrievalRequestId,
        GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE,
        curriculumScope,
        QuestionType.STEP_FILL,
        similar.difficulty(),
        originId,
        originSnapshot,
        ragProperties.candidateLimit(),
        Math.min(4, requestedCount),
        excludedIds)
```

RAG가 비활성/Provider 부재/검색 실패면 검색 결과를 빈 목록으로 간주하고 전량 AI 부족분으로 계획한다. fallback은 기존 trace port 정책을 재사용하고 HTTP 요청 전체를 실패시키지 않는다.

ORIGIN과 나머지 오답 reference ID는 `ProblemBankSnapshotQueryService#getSnapshots`로 한 번에 조회한다. ORIGIN Snapshot이 누락되거나 재사용 불가하면 `CUSTOM_PROBLEM_SIMILAR_REFERENCE_MISSING`으로 거절한다. 학생 답안 데이터는 이 조회와 명령에 포함하지 않는다.

- [x] **Step 4: 검색 결과를 BANK_REUSE와 AI 부족분으로 나눈다**

검색 결과는 이미 `QuestionSnapshotV1`을 포함하므로 재조회하지 않는다. BANK_REUSE asset이 필요한 경우 `ProblemBankSnapshotQueryService#getSnapshots`로 선택 ID만 조회해 `assetStorageKeys`까지 확보한다. AI 명령은 `GenerationSpecification(STEP_FILL, difficulty, null, List.of())`를 사용한다.

- [x] **Step 5: SIMILAR 테스트를 통과시킨다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPlanningServiceTest.similar*' --tests '*ProblemGenerationPlanningServiceTest'`

Expected: PASS, 일반 계획기 회귀 없음.

- [x] **Step 6: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java src/test/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningServiceTest.java
git commit -m "feat : 맞춤 유사 문제 은행 우선 생성"
```

## Task 8: ADVANCED 구조화 AI 생성 구현

**Files:**

- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningServiceTest.java`

- [x] **Step 1: ADVANCED 구조화 생성 명령 코드를 구현하고 매핑 테스트를 추가한다**

`advanced.triggered=true`, `primaryEvaluationArea=CALCULATION`, `primaryTargetStage=EXECUTE`와 분포 배열 fixture를 만들고 다음을 검증한다.

```java
assertThat(command.purpose()).isEqualTo(PERSONALIZED_APPLICATION);
assertThat(command.specification().difficulty()).isEqualTo("high");
assertThat(command.specification().questionType()).isEqualTo(STEP_FILL);
assertThat(command.specification().requiresSolutionStructure()).isTrue();
assertThat(command.specification().targetDiagnosticTypes())
        .containsExactly(DiagnosticType.EXECUTE);
assertThat(command.personalizedEvidence()).isEqualTo(expectedEvidence);
```

- [x] **Step 2: ADVANCED 및 전체 단계 순서 테스트를 실행한다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPlanningServiceTest.advanced*'`

Expected: PASS.

- [x] **Step 3: 분석 enum을 문제 enum으로 이름 기반 명시 변환한다**

분석 Entity를 참조하지 않고 DTO가 제공한 `DiagnosticStage`를 private mapper에서 `DiagnosticType.valueOf(stage.name())`로 변환한다. 두 enum 값이 달라지면 조용히 무시하지 않고 테스트/실행이 실패하게 한다.

- [x] **Step 4: 모든 ADVANCED 슬롯을 AI 명령으로 만든다**

주 ORIGIN은 유사 단계와 같은 첫 reference question을 사용한다. `PERSONALIZED_APPLICATION`, `difficulty=high`, `QuestionType.STEP_FILL` query로 얻은 검색 결과는 EXAMPLE reference로만 넣고 BANK_REUSE 슬롯으로 바꾸지 않는다. RAG 비활성/실패 시 ORIGIN만으로 AI 명령을 만든다. `customStage=ADVANCED`, `originQuestionId=originId`, `sourceQuestionId=null`을 보존한다.

- [x] **Step 5: ADVANCED와 전체 단계 순서 테스트를 통과시킨다**

Run: `./gradlew test --tests '*PersonalizedProblemGenerationPlanningServiceTest' --tests '*PersonalizedProblemGenerationPromptTest'`

Expected: PASS.

- [x] **Step 6: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java src/test/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningServiceTest.java
git commit -m "feat : 취약 근거 기반 응용 문제 생성 계획"
```

## Task 9: 맞춤 생성 오케스트레이션과 시작 API 연결

**Files:**

- Create: `src/main/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/controller/CustomProblemGenerationController.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationService.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationServiceTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationServiceTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/controller/CustomProblemGenerationControllerTest.java`

- [ ] **Step 1: 맞춤 오케스트레이션 서비스와 기존 비동기 진입점을 구현하고 서비스 테스트를 추가한다**

서비스 공개 계약은 다음으로 고정한다.

```java
/** 최신 취약 제안을 재검증하고 맞춤 생성 Job을 접수한다. */
public ProblemGenerationStartResponse start(
        long teacherId, CustomProblemGenerationRequest request)
```

Mock 순서 검증:

1. `reissueProposalService.getProposal(teacherId, sourceAssignmentId, studentId)`
2. `validator.validate(request, proposal)`
3. `curriculumUnitQueryService.getPathsBySubUnitIds(requestedIds)`
4. `planningService.plan(clientRequestId, proposal, items, paths)`
5. `asyncGenerationService.startPersonalized(teacherId, plan)`

- [ ] **Step 2: 오케스트레이션 및 기존 생성 회귀 테스트를 실행한다**

Run: `./gradlew test --tests '*CustomProblemGenerationServiceTest'`

Expected: PASS.

- [ ] **Step 3: 기존 비동기 실행 서비스에 맞춤 계획 진입점만 공개한다**

```java
/** 검증된 맞춤 계획을 멱등 Job으로 저장하고 AI 슬롯만 비동기 실행한다. */
public ProblemGenerationStartResponse startPersonalized(
        long teacherId, ProblemGenerationPlan plan) {
    if (plan.jobType() != GenerationJobType.PERSONALIZED) {
        throw new IllegalArgumentException("맞춤 생성 계획만 접수할 수 있습니다.");
    }
    return createAndRun(teacherId, plan);
}
```

기존 private `createAndRun`의 공통 본문은 `ProblemGenerationPlan`을 직접 받는 overload로 추출한다. 일반·종합 API 동작은 유지한다.

- [ ] **Step 4: 오케스트레이션 서비스를 구현하고 테스트를 통과시킨다**

서비스 로그는 `teacherId`, `sourceAssignmentId`, `studentId`, `subUnitId`, 단계별 요청 수량과 최종 재사용/AI 슬롯 수만 기록한다. 학생 답안, 문항 본문, 정답, Snapshot JSON은 기록하지 않는다.

Run: `./gradlew test --tests '*CustomProblemGenerationServiceTest' --tests '*ProblemAsyncGenerationServiceTest'`

Expected: PASS. `ProblemAsyncGenerationServiceTest`가 아직 없으면 새로 만들고 일반·종합 시작 회귀도 함께 검증한다.

- [ ] **Step 5: 맞춤 Controller와 MockMvc 보안 테스트를 구현한다**

```java
@RestController
@RequestMapping("/api/teacher/custom-problems")
public class CustomProblemGenerationController {
    @PostMapping("/generate/async")
    public ApiResponse<ProblemGenerationStartResponse> generateAsync(
            @Valid @RequestBody CustomProblemGenerationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.start(user.memberId(), request));
    }
}
```

교사 JWT와 유효 payload는 200 및 `data.jobId/status/totalCount`, JWT 없음은 401, 학생 JWT는 403을 기대한다. 서비스는 `@MockitoBean`으로 대체하고 `user.memberId()`가 전달됐는지 검증한다.

- [ ] **Step 6: Controller 테스트를 통과시킨다**

Run: `./gradlew test --tests '*CustomProblemGenerationControllerTest'`

Expected: PASS.

- [ ] **Step 7: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationService.java src/main/java/com/cenedu/backend/domain/problem/controller/CustomProblemGenerationController.java src/test/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationServiceTest.java src/test/java/com/cenedu/backend/domain/problem/controller/CustomProblemGenerationControllerTest.java
git commit -m "feat : 맞춤 문제 비동기 생성 API 연결"
```

`ProblemAsyncGenerationService.java`가 실행 시작 전부터 dirty라면 위 커밋에는 넣지 않고 기존 변경과 맞춤 진입점 hunk를 함께 보존한다.

## Task 10: 공통 Job polling 응답에 단계·출처 메타데이터 노출

**Files:**

- Modify: `src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemGenerationSlotResponse.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/dto/response/CustomProblemStageFormatter.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationService.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/dto/response/ProblemGenerationSlotResponseTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationServiceTest.java`

- [ ] **Step 1: polling 응답 메타데이터와 formatter를 구현하고 맞춤/일반 검증 테스트를 추가한다**

맞춤 SIMILAR BANK 슬롯은 `customStage="similar"`, `sourceQuestionId` 존재, `originQuestionId=null`; 맞춤 ADVANCED AI 슬롯은 `customStage="advanced"`, `sourceQuestionId=null`, `originQuestionId` 존재; 일반 슬롯은 세 필드가 모두 null이어야 한다.

- [ ] **Step 2: polling 응답 테스트를 실행한다**

Run: `./gradlew test --tests '*ProblemGenerationSlotResponseTest' --tests '*ProblemAsyncGenerationServiceTest'`

Expected: PASS.

- [ ] **Step 3: 응답 record를 확장하고 기존 생성자를 호환한다**

```java
public record ProblemGenerationSlotResponse(
        int slotIndex,
        Long itemId,
        Long sessionId,
        String customStage,
        Long sourceQuestionId,
        Long originQuestionId,
        AuthoringSlotDisplayStatus status,
        AuthoringProblemSnapshotResponse preview,
        String errorCode,
        boolean retryable) {
}
```

기존 7인자 생성자는 세 메타데이터를 null로 위임한다. `CustomProblemStageFormatter`는 `REVIEW→review`, `SIMILAR→similar`, `ADVANCED→advanced`만 담당하며 worksheet의 별도 UI 문자열 변환을 재사용하지 않는다.

- [ ] **Step 4: `getStatus`가 ItemResult 메타데이터를 응답에 매핑하게 한다**

미리보기는 기존처럼 `SUCCEEDED` 슬롯에만 조회한다. 생성/검증 실패의 retryable 계산도 변경하지 않는다.

- [ ] **Step 5: polling 응답과 기존 DTO 테스트를 통과시킨다**

Run: `./gradlew test --tests '*ProblemGenerationSlotResponseTest' --tests '*ProblemAsyncGenerationServiceTest' --tests '*ProblemRagGenerationApiIntegrationTest'`

Expected: PASS.

- [ ] **Step 6: 커밋한다**

```bash
git add src/main/java/com/cenedu/backend/domain/problem/dto/response/ProblemGenerationSlotResponse.java src/main/java/com/cenedu/backend/domain/problem/dto/response/CustomProblemStageFormatter.java src/test/java/com/cenedu/backend/domain/problem/dto/response/ProblemGenerationSlotResponseTest.java src/test/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationServiceTest.java
git commit -m "feat : 맞춤 생성 단계와 출처 polling 응답 추가"
```

`ProblemAsyncGenerationService.java`는 실행 시작 전 dirty 여부에 따라 Task 9와 동일한 보존 규칙을 적용한다.

## Task 11: API 오류·보안·멱등성과 전체 아키텍처 회귀 검증

**Files:**

- Modify: `src/test/java/com/cenedu/backend/domain/problem/controller/CustomProblemGenerationControllerTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationIdempotencyIntegrationTest.java`
- Modify: `src/test/java/com/cenedu/backend/domain/problem/repository/ProblemGenerationItemRepositoryTest.java`
- Modify: `src/test/java/com/cenedu/backend/architecture/AiClientAccessTest.java` only if the test needs the new package included explicitly

- [ ] **Step 1: 도메인 오류 API 매핑 테스트를 추가한다**

Mock service가 `BusinessException(CUSTOM_PROBLEM_TOTAL_LIMIT_EXCEEDED)`를 던질 때 400과 공통 `ApiResponse.error.code`가 반환되는지 검증한다. 같은 방식으로 기존 `ANALYSIS_REISSUE_NOT_GRADED`가 그대로 전달되는지 검증한다.

- [ ] **Step 2: 같은 교사+clientRequestId 재요청의 멱등 통합 테스트를 추가한다**

동일 계획을 두 번 `ProblemGenerationJobService#create`에 전달했을 때 같은 `jobId`가 반환되고 item 수가 늘지 않는지 PostgreSQL에서 검증한다. 다른 교사의 같은 UUID는 별도 Job이어야 한다.

- [ ] **Step 3: 관련 테스트 묶음을 실행한다**

Run:

```bash
./gradlew test \
  --tests '*ReissueProposalServiceTest' \
  --tests '*CustomProblemGeneration*Test' \
  --tests '*PersonalizedProblemGenerationPlanningServiceTest' \
  --tests '*ProblemGenerationJobServiceTest' \
  --tests '*ProblemGenerationItemRepositoryTest' \
  --tests '*ProblemAsyncGenerationServiceTest' \
  --tests '*ProblemGenerationSlotResponseTest' \
  --tests '*PersonalizedProblemGenerationPromptTest'
```

Expected: PASS.

- [ ] **Step 4: 정적 경계와 migration 품질을 확인한다**

Run: `rg -n 'domain\.analysis\..*repository|domain\.worksheet\..*repository|ai\.client|com\.openai|org\.springframework\.ai' src/main/java/com/cenedu/backend/domain/problem`

Expected: 새 맞춤 생성 파일에서 금지된 직접 Repository/AI Client 참조가 없음.

Run: `git diff --check`

Expected: 출력 없음.

- [ ] **Step 5: 전체 백엔드 빌드를 실행한다**

Run: `./gradlew build`

Expected: `BUILD SUCCESSFUL`, `AiClientAccessTest` 포함 전체 테스트 PASS.

- [ ] **Step 6: 실제 변경 범위와 사용자 변경 보존을 검토한다**

Run: `git status --short`

Run: `git diff --stat`

Expected: 계획 범위 파일만 새 커밋에 포함되고, 시작 시 존재했던 AI 호출 예산 관련 사용자 변경은 내용이 보존되어 있다.

- [ ] **Step 7: 최종 검증 테스트 변경만 커밋한다**

```bash
git add src/test/java/com/cenedu/backend/domain/problem/controller/CustomProblemGenerationControllerTest.java src/test/java/com/cenedu/backend/domain/problem/service/CustomProblemGenerationIdempotencyIntegrationTest.java src/test/java/com/cenedu/backend/domain/problem/repository/ProblemGenerationItemRepositoryTest.java
git commit -m "test : 맞춤 문제 생성 백엔드 회귀 검증"
```

커밋할 추가 변경이 없으면 빈 커밋을 만들지 않는다.

## Backend Completion Contract

- `POST /api/teacher/custom-problems/generate/async`가 최신 재출제 제안을 재검증한다.
- 전체 1~20문항, 중복 소단원 금지, 단계별 최신 상한, ORIGIN/응용 조건을 서버가 강제한다.
- REVIEW는 정확한 오답 ID를 재사용하고 LLM을 호출하지 않는다.
- SIMILAR는 기존 답변 문항과 현재 Job 선택 문항을 제외하고 벡터 은행 우선·AI 부족분 정책을 적용한다.
- ADVANCED는 조건부 전량 AI이며 학생 답안 원문 없이 구조화된 취약 근거를 사용한다.
- 슬롯은 REVIEW→SIMILAR→ADVANCED, 각 단계 안에서 교육과정 순서를 보존한다.
- 기존 Job/Session/Version/검증/재시도/미리보기/AI 수정 기반을 재사용한다.
- polling 응답에서 `customStage`, `sourceQuestionId`, `originQuestionId`를 복원한다.
- 분석 보고서 생성 상태와 무관하게 구조화된 채점/재출제 데이터만으로 동작한다.
- 일반학습·종합평가 API 회귀가 없고 전체 Gradle build가 통과한다.

## Follow-up Plan Boundary

백엔드 API가 이 계약으로 안정된 뒤 별도 프론트 계획을 작성한다. 프론트 계획은 `cen-edu-frontend`에서 재출제 제안 adapter, 20문항 기본 선택, 생성 mutation, 기존 polling/미리보기/AI 수정 UI 재사용, mock 생성 제거를 다룬다. 프론트 저장소 규칙에 따라 에이전트는 브라우저·build·test를 실행하지 않고 사용자 검증 절차를 전달한다.
