# 조건부 시각 문항 생성 및 초안 미리보기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 문제 풀이에 시각 자료가 필수인 경우에만 검증 가능한 SVG를 생성하고, 교사가 S3 최종 저장 전에 이미지를 검토하며, 유사·응용 문제와 RAG 검색이 원본 시각 유형을 보존하도록 한다.

**Architecture:** 의미 모델을 문제 본문·정답·해설·도식의 단일 정본으로 사용하고, 서버의 시각 정책이 생성 mode와 diagram allowlist를 강제한다. 평가된 semantic value를 deterministic SVG renderer에 전달해 local draft를 만든 뒤 인증된 preview API로 제공하며, 교사 확정 이후에만 기존 S3 영구 저장 흐름을 사용한다. RAG에는 visual kind를 색인·필터링하고, 반복 백필은 저빈도·제한 batch·비순환 cursor로 바꾼다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Gradle Groovy DSL, Spring Security JWT, JPA/Hibernate, PostgreSQL 17, pgvector, Flyway, Jackson, JUnit 5, AssertJ, Mockito, MockMvc

**Spec:** `docs/superpowers/specs/2026-08-21-conditional-visual-problem-authoring-design.md`

**Status:** Task 1~9 구현 및 관련 테스트 완료. 아래 checkbox는 실행 상태를 기록한다.

## Global Constraints

- 사용자가 명시적으로 승인하기 전에는 Task 1을 포함한 구현 코드·테스트 코드·설정·DB migration을 변경하거나 Gradle 명령을 실행하지 않는다.
- 사용자 지시에 따라 red-green 테스트 순서를 사용하지 않는다.
- Task 1~8에서 구현 코드를 모두 작성한 뒤 Task 9에서 테스트 코드를 작성·보강한다.
- 관련 테스트 실행은 Task 9에서만 시작한다.
- 전체 `bash gradlew build`는 모든 작업의 마지막 Task 10에서 한 번만 실행한다.
- 운영 기본 allowlist는 `COORDINATE_GRAPH,DATA_TABLE`이다.
- 로컬 experimental allowlist는 5개 `DiagramKind` 전체를 설정으로 허용할 수 있어야 한다.
- 초기 visual 생성 문항 유형은 `MULTIPLE_CHOICE,SHORT_INPUT`으로 제한하고 `STEP_FILL,ESSAY`는 Legacy를 유지한다.
- 한 문항의 신규 시각 자산은 최대 1개다.
- LLM이 raw SVG를 만들지 않고 서버 renderer와 `SafeSvgSanitizer`만 SVG를 만든다.
- 교사 draft preview는 S3에 의존하지 않는다.
- draft preview는 `TEACHER` principal과 session 소유권을 검증한다.
- draft preview는 symbolic link와 draft root 이탈을 거절하고 `Cache-Control: no-store`를 반환한다.
- 운영 draft root는 backend 전용 named volume으로 보존하고 모든 draft consumer가 같은 typed 설정을 사용한다.
- 현재 운영 배포는 backend 단일 인스턴스를 전제로 하며 local volume 상태로 수평 확장하지 않는다.
- 사용자 프롬프트를 처리하는 문제 수정 경로는 계속 `AgentDispatcher`를 통과한다.
- `domain.problem..`은 `ai.client..`, `com.openai..`, `org.springframework.ai..`를 직접 참조하지 않는다.
- 다른 도메인의 Repository와 Entity를 직접 참조하지 않는다.
- API JSON 응답은 `ApiResponse<T>`로 감싼다.
- 새 설정 키는 `application.yaml`, 필요한 local override는 `application-local.yaml`, 사용법은 `.env.example`에 함께 기록한다.
- DB 변경은 새 Flyway timestamp migration으로만 수행하고 기존 migration을 수정하지 않는다.
- 문제 원문, 정답, 사용자 입력, SVG 전문, local draft 경로를 로그에 남기지 않는다.
- 기존 사용자 변경인 `.env.example` 수정분과 `docs/PROBLEM_API_SPEC.md`는 덮어쓰거나 제거하지 않는다.
- commit 시 `git add -A`를 사용하지 않고 Task 소유 hunk만 선별한다. 기존에 수정된 `.env.example`은
  task hunk만 분리해 stage하고, 현재 untracked인 `docs/PROBLEM_API_SPEC.md` 전체를 새 파일로
  commit하는 것은 사용자에게 별도 확인한 뒤 진행한다.
- 이 저장소에는 프론트엔드가 없으므로 API 계약까지 작성하고, 실제 교사 UI 연동은 프론트 저장소의 후속 작업으로 명시한다.

---

## 1. 구현 의미와 요구사항 요약

### 1.1 왜 조건부 이미지 생성인가

이미지는 문제를 꾸미기 위한 결과물이 아니라 학생이 읽어야 하는 문제 데이터다. 따라서 이미지가
없어도 풀 수 있는 문제에 그림을 붙이지 않고, 이미지가 필요한 문제는 이미지 없이 READY가 되지
않아야 한다. 모델의 `visualRequired` boolean 하나만 신뢰하지 않고 발문·diagram·asset reference·
allowlist를 서버가 함께 검증한다.

### 1.2 왜 semantic value를 renderer까지 전달하는가

문제 정답이 계수 3을 전제로 하는데 그래프가 기본 계수 1로 그려지면 문제 전체가 오답이다. Snapshot과
SVG가 같은 `SemanticComputationEngine` 평가 결과를 소비해야 문제·정답·해설·이미지가 일치한다.

### 1.3 왜 draft preview가 S3보다 먼저인가

교사 확정은 문제 본문과 이미지를 함께 검토한 뒤 이루어져야 한다. S3는 확정된 문제의 영구 저장소이고,
검토 중 draft의 필수 의존성이 아니다. preview API는 local draft를 소유권 검증 후 data URL로 전달한다.

### 1.4 왜 visual kind를 RAG에 넣는가

“다음 그림”이라는 발문만으로는 좌표 그래프인지 도형인지 알 수 없다. 검색·유사 생성·응용 생성은
원본의 `diagramKind`와 render spec을 사용해야 한다. 의미가 없는 `UNKNOWN_FIGURE`는 임의 생성하지
않는다.

### 1.5 왜 백필 주기를 낮추는가

신규 최종 문항은 이미 즉시 인덱싱 큐에 등록된다. 백필은 누락된 과거 문항을 보정하는 보조 경로이므로
1분마다 전체 문제은행을 반복 순회할 필요가 없다. 최초 10분 지연, 1시간 간격, 회당 50개, cursor 비초기화로
부하를 제한한다.

### 1.6 상세 설계 추적 표

| 상세 설계 요구 | 구현 Task | 검증 Task |
|---|---|---|
| 시각 필수 조건·mode·allowlist·문항 유형 | Task 1, 3, 6 | Task 9 Step 1, 6 |
| semantic resolved value와 SVG 일치 | Task 2, 7 | Task 9 Step 2~4 |
| 객관식 choiceKey 정답 재료화 | Task 2 | Task 9 Step 3, 9 |
| Snapshot block·assetRef·role·presentation 일치 | Task 2, 3 | Task 9 Step 3, 9 |
| S3 이전 소유권 기반 draft preview | Task 4 | Task 9 Step 5, 9; Task 10 Step 2 |
| symbolic link·checksum·크기·cache 보안 | Task 4 | Task 9 Step 4~5 |
| 컨테이너 재생성 간 draft 보존·단일 typed root | Task 1, 4 | Task 9 Step 1, 5, 9; Task 10 Step 1~2 |
| SIMILAR·APPLICATION origin visual 보존 | Task 5, 6 | Task 9 Step 6~7 |
| generic figure 및 원본 의미 복원 실패 처리 | Task 3, 5, 6 | Task 9 Step 5~7 |
| RAG visual kind 색인·hard filter | Task 5 | Task 9 Step 7 |
| 기존 v1 작업의 v2 재색인 | Task 5, 8 | Task 9 Step 7~8 |
| 늦은 v1 작업의 v2 index downgrade 차단 | Task 5 | Task 9 Step 7 |
| 5개 family local experimental 렌더링 | Task 7 | Task 9 Step 2~4; Task 10 Step 3 |
| 백필 10분/1시간/50개·cursor 유지 | Task 8 | Task 9 Step 8; Task 10 Step 5 |
| 확정 후 S3 영속화·draft 삭제 | Task 3 | Task 9 Step 9; Task 10 Step 4 |
| 구현 후 테스트·최종 전체 빌드 1회 | Task 9 | Task 10 Step 7 |

## 2. 변경 파일 지도

### 2.1 새 파일

| 파일 | 책임 |
|---|---|
| `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualGenerationMode.java` | `NONE`, `AUTO`, `PRESERVE_ORIGIN` 생성 mode |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualReferenceKind.java` | 검색·원본 판정용 visual kind |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualReferenceDescriptor.java` | origin의 kind, assetKey, altText, diagram spec 요약 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualGenerationRequirement.java` | 생성 명령의 mode와 origin kind 계약 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualGenerationPolicy.java` | allowlist와 semantic model 불변조건 검증 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualPolicyViolationException.java` | 시각 정책 위반 목록을 보존하는 domain 예외 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualSnapshotConsistencyValidator.java` | 발문·block·assetRef·plan·presentation 사후 정합성 검증 |
| `src/main/java/com/cenedu/backend/domain/problem/config/ProblemVisualAuthoringProperties.java` | visual enable, allowed kinds, max assets 설정 |
| `src/main/java/com/cenedu/backend/domain/problem/config/ProblemDraftStorageProperties.java` | 생성·preview·정리·S3 worker가 공유하는 draft root와 preview 크기 설정 |
| `src/main/java/com/cenedu/backend/domain/problem/config/ProblemVisualAuthoringConfig.java` | visual configuration properties 등록 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftPathResolver.java` | draft root 경계·symbolic link·정규 파일 검증을 한 곳에서 수행 |
| `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/VisualAuthoringConfigurationValidator.java` | visual enabled인데 semantic disabled인 잘못된 기동 설정 차단 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemVisualReferenceQueryService.java` | 저장된 semantic model/render spec에서 시각 정본 조회 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetPreviewService.java` | draft 소유권·상태·경로·checksum 검증과 data URL 생성 |
| `src/main/java/com/cenedu/backend/domain/problem/dto/response/DraftAssetPreviewResponse.java` | preview API JSON 응답 |
| `src/main/java/com/cenedu/backend/domain/problem/controller/ProblemDraftAssetPreviewController.java` | 교사 draft preview endpoint |
| `src/main/resources/db/migration/V20260821_1800__problem_add_visual_search_kind.sql` | 검색 인덱스 `visual_kind` 컬럼·제약·인덱스 추가 |

### 2.2 주요 수정 파일

| 파일 | 변경 책임 |
|---|---|
| `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationSpecification.java` | visual requirement 추가 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationReference.java` | visual descriptor와 semantic model 보존 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQuery.java` | required visual kind 전달 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievedProblemReference.java` | 검색 결과 visual kind 전달 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingCommand.java` | visual kind 색인 입력 추가 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationService.java` | 일반·평가 `AUTO` 정책 생성 |
| `src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java` | SIMILAR·APPLICATION origin 보존 정책 생성 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java` | origin visual semantic 보강과 실패 판정 |
| `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPromptFactory.java` | 생성 조건과 origin semantic model 전달 |
| `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPipeline.java` | 시각 정책 검증 연결 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/DefaultProblemSemanticMaterializer.java` | 평가값 공유, presentation·block·asset 연결 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticAssetPlanFactory.java` | resolved values와 role을 자산 계획에 저장 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/asset/AssetGenerationSpecification.java` | typed resolved values 추가 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/SemanticAssertionValidator.java` | 객관식 target과 choice value 유일성 검증 |
| `src/main/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapter.java` | 실제 평가값 렌더링과 width/height 기록 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetCleanupService.java` | typed draft root 사용과 기존 TTL 정리 유지 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAssetStorageWorker.java` | typed draft root에서 S3 재시도 원본 조회 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecValidator.java` | family별 구조·참조·범위 검증 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingService.java` | 정책 검증 및 resolved context 검증 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSnapshotEntityMapper.java` | 자산 계획 role 영속화 |
| `src/main/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapter.java` | asset manifest를 시각 필요성 검증에 전달 |
| `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationLlmClient.java` | Snapshot과 자산 구조 요약으로 자산 판정 호출 |
| `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationStructuredOutputSchemas.java` | visual necessity 이슈 enum 확장 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchIndexingService.java` | visual kind 색인 명령 생성 |
| `src/main/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactory.java` | `[시각유형]` 문서 레이블 |
| `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexJdbcRepository.java` | visual kind upsert |
| `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorker.java` | 낮은 schema task를 embedding 호출 전에 skip |
| `src/main/java/com/cenedu/backend/infra/vector/ProblemReferenceJdbcRepository.java` | visual kind hard filter 및 조회 |
| `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillScheduler.java` | 저빈도·비순환 cursor 스케줄 |
| `src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagProperties.java` | backfill 전용 설정 추가 |
| `src/main/resources/application.yaml` | 운영 visual allowlist와 backfill 기본값 |
| `src/main/resources/application-local.yaml` | local experimental 설정 문서화 |
| `.env.example` | visual·backfill 환경 변수 설명 추가 |
| `deploy/.env.prod.example` | 운영 visual·backfill 권장값 예시 |
| `deploy/docker-compose.prod.yml` | backend 전용 `problem-drafts` named volume mount |
| `Dockerfile` | non-root 실행 사용자가 운영 draft mount 경로를 쓸 수 있게 준비 |
| `src/main/java/com/cenedu/backend/global/common/ErrorCode.java` | visual·preview 오류 추가 |
| `docs/PROBLEM_API_SPEC.md` | preview API와 visual asset 계약 추가, 기존 사용자 내용 보존 |

### 2.3 테스트 파일

모든 구현 완료 후 Task 9에서 다음 테스트를 생성·수정한다.

| 파일 | 검증 책임 |
|---|---|
| `src/test/java/com/cenedu/backend/domain/problem/authoring/visual/VisualGenerationPolicyTest.java` | mode·allowlist·diagram 불변조건 |
| `src/test/java/com/cenedu/backend/domain/problem/authoring/visual/VisualSnapshotConsistencyValidatorTest.java` | 구체 발문·generic 발문·asset key·role·presentation |
| `src/test/java/com/cenedu/backend/domain/problem/config/ProblemVisualAuthoringPropertiesTest.java` | enabled·allowlist·지원 문항 유형 binding |
| `src/test/java/com/cenedu/backend/domain/problem/config/ProblemDraftStoragePropertiesTest.java` | draft root·preview 크기 binding과 단일 설정 계약 |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemDraftPathResolverTest.java` | read/write/delete 경로의 root 이탈·parent symlink 차단 |
| `src/test/java/com/cenedu/backend/ai/problem/adapter/semantic/VisualAuthoringConfigurationValidatorTest.java` | visual on + semantic off 기동 실패 |
| `src/test/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecValidatorTest.java` | family별 구조 검증 |
| `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/DefaultProblemSemanticMaterializerVisualTest.java` | presentation·block·asset·resolved value |
| `src/test/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticSnapshotFactoryVisualChoiceTest.java` | semantic target에서 유일한 객관식 choiceKey 정답 생성 |
| `src/test/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapterTest.java` | 실제 값 SVG·checksum·크기 |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetPreviewServiceTest.java` | 소유권·경로·checksum·data URL |
| `src/test/java/com/cenedu/backend/domain/problem/controller/ProblemDraftAssetPreviewControllerTest.java` | JWT와 ApiResponse 계약 |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricherTest.java` | visual origin 의미 보강·실패 |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemVisualReferenceQueryServiceTest.java` | semantic model→render spec→alt text 정본 우선순위 |
| `src/test/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPromptFactoryTest.java` | mode와 origin diagram prompt 입력 |
| `src/test/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningServiceTest.java` | SIMILAR·APPLICATION kind 보존 |
| `src/test/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactoryTest.java` | visual kind 문서화 |
| `src/test/java/com/cenedu/backend/infra/vector/ProblemReferenceJdbcRepositoryTest.java` | visual kind hard filter |
| `src/test/java/com/cenedu/backend/infra/vector/ProblemSearchIndexJdbcRepositoryTest.java` | index schema v2 task 등록·visual kind upsert·낮은 schema downgrade 차단 |
| `src/test/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorkerTest.java` | 높은 READY schema가 있으면 낮은 task를 embedding 없이 skip |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSearchIndexingServiceTest.java` | v2 idempotency key로 기존 문항 재큐잉 |
| `src/test/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapterVisualRoutingTest.java` | visual mode별 semantic/Legacy 라우팅 |
| `src/test/java/com/cenedu/backend/ai/verification/adapter/AssetChecksVisualNecessityTest.java` | 불필요·중복·generic 시각 판정 |
| `src/test/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapterTest.java` | asset manifest가 necessity 검증으로 전달되는지 보강 |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSnapshotEntityMapperTest.java` | TABLE/FIGURE role 영속화 보강 |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationServiceTest.java` | READY artifact 전제·PENDING asset·storage task 보강 |
| `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillSchedulerTest.java` | initial delay 설정·cursor 비초기화·batch 50 |
| `src/test/java/com/cenedu/backend/domain/problem/controller/ProblemVisualAuthoringFlowIntegrationTest.java` | 생성 Job→preview→최종화 전 흐름 |

---

## 3. 구현 단계

### Task 1: 시각 생성 정책과 설정 계약 구현

**이 Task의 작업:** 문제에 이미지가 필수인지, 어떤 시각 유형을 생성할 수 있는지를 서버 정책으로 고정한다.

**현재 문제:** `visualRequired` 값이 선언되어 있어도 생성 가능 유형과 반려 조건을 서버가 일관되게 검증하지 않는다.

**개선 방향:** `VisualGenerationPolicy`/설정 속성이 `NONE`, `AUTO`, `PRESERVE_ORIGIN` 모드와 운영·실험 allowlist를 검증하고, 한 문항에 최대 1개 자산만 허용한다.

**주요 구현체 담당:** `VisualReferenceKind` 는 시각 유형 값, `VisualGenerationRequirement` 는 생성 요구, `VisualGenerationPolicy` 는 생성 가능여부와 불변조건, `ProblemVisualAuthoringProperties` 는 시각 정책 설정, `ProblemDraftStorageProperties` 는 모든 draft 경로 소비자가 공유할 저장 설정을 담당한다.

**기대 방향:** text-only 문제는 자산을 생성하지 않고, 운영에서는 좌표 그래프·표만 안전하게 활성화한다.

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualGenerationMode.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualReferenceKind.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualReferenceDescriptor.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualGenerationRequirement.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualGenerationPolicy.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualPolicyViolationException.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/config/ProblemVisualAuthoringProperties.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/config/ProblemDraftStorageProperties.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/config/ProblemVisualAuthoringConfig.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/VisualAuthoringConfigurationValidator.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationSpecification.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-local.yaml`
- Modify: `.env.example`
- Modify: `deploy/.env.prod.example`
- Modify: `src/main/java/com/cenedu/backend/global/common/ErrorCode.java`

**Interfaces:**
- Produces: `VisualGenerationMode { NONE, AUTO, PRESERVE_ORIGIN }`
- Produces: `VisualReferenceKind { NONE, UNKNOWN_FIGURE, NUMBER_LINE, COORDINATE_GRAPH, DATA_TABLE, PLANE_GEOMETRY, SOLID_GEOMETRY }`
- Produces: `VisualGenerationRequirement(VisualGenerationMode mode, VisualReferenceKind requiredKind)`
- Produces: `void VisualGenerationPolicy.validate(VisualGenerationRequirement, ProblemSemanticModelV1)`; 위반 시 `VisualPolicyViolationException`
- Consumes: `ProblemVisualAuthoringProperties.allowedKinds()` as `Set<DiagramKind>`
- Consumes: `ProblemVisualAuthoringProperties.allowedQuestionTypes()` as `Set<QuestionType>`
- Consumes: `ProblemDraftStorageProperties.root()` as normalized absolute `Path`
- Consumes: `ProblemDraftStorageProperties.previewMaxBytes()` as positive byte limit

- [x] **Step 1: 생성 mode와 visual kind를 정의한다**

  `NONE`은 text-only를 강제하고, `AUTO`는 모델이 허용 family 중 하나를 선택할 수 있게 하며,
  `PRESERVE_ORIGIN`은 `requiredKind`와 같은 family를 강제한다. `UNKNOWN_FIGURE`는 검색·진단 값으로만
  사용하고 생성 가능 kind로 변환하지 않는다.

- [x] **Step 2: origin descriptor와 generation requirement를 구현한다**

  다음 생성자 불변조건을 적용한다.

  ```java
  public record VisualGenerationRequirement(
          VisualGenerationMode mode,
          VisualReferenceKind requiredKind
  ) {
      public VisualGenerationRequirement {
          if (mode == null) throw new IllegalArgumentException("visual mode가 필요합니다.");
          if (mode == VisualGenerationMode.PRESERVE_ORIGIN
                  && (requiredKind == null
                      || requiredKind == VisualReferenceKind.NONE
                      || requiredKind == VisualReferenceKind.UNKNOWN_FIGURE)) {
              throw new IllegalArgumentException("보존할 origin visual kind가 필요합니다.");
          }
      }
  }
  ```

  `VisualReferenceDescriptor`는 `assetKey`, `kind`, `role`, `altText`, `DiagramSpecV1`을 가진다.
  정답 값은 포함하지 않는다.

- [x] **Step 3: `GenerationSpecification`에 visual requirement를 추가한다**

  기존 생성자 호출부가 즉시 깨지지 않도록 기존 4개·5개 인자 생성자는 `NONE` 또는 호출 목적에 맞는
  명시적 기본값을 사용한다. 서비스의 실제 생성 경로는 Task 6에서 `AUTO` 또는 `PRESERVE_ORIGIN`을
  직접 전달하도록 바꾼다.

- [x] **Step 4: 서버 시각 정책을 구현한다**

  `VisualGenerationPolicy.validate()`는 다음 오류를 한 번에 수집해
  `VisualPolicyViolationException(List<String> violations)`을 던진다.

  ```text
  NONE + visualRequired=true
  NONE + diagrams not empty
  AUTO + visualRequired=false + diagrams not empty
  AUTO + visualRequired=true + diagram count != 1
  PRESERVE_ORIGIN + diagram count != 1
  PRESERVE_ORIGIN + actual kind != required kind
  any visual mode + diagram kind not in allowedKinds
  visualRequired=true + question type not in allowedQuestionTypes
  diagram count > maxAssetsPerQuestion
  ```

- [x] **Step 5: 설정을 추가한다**

  ```yaml
  app:
    problem-authoring:
      visual:
        enabled: ${PROBLEM_VISUAL_AUTHORING_ENABLED:false}
        allowed-kinds: ${PROBLEM_VISUAL_ALLOWED_KINDS:COORDINATE_GRAPH,DATA_TABLE}
        allowed-question-types: ${PROBLEM_VISUAL_ALLOWED_QUESTION_TYPES:MULTIPLE_CHOICE,SHORT_INPUT}
        max-assets-per-question: 1
      draft:
        root: ${PROBLEM_DRAFT_ROOT:/tmp/cen-edu-problem-drafts}
        preview-max-bytes: ${PROBLEM_VISUAL_PREVIEW_MAX_BYTES:1048576}
  ```

  `.env.example`에는 운영 권장값과 local 전체 family 실험값을 주석으로 구분한다.
  `deploy/.env.prod.example`에는 좌표 그래프·표, 두 문항 유형,
  `PROBLEM_DRAFT_ROOT=/var/lib/cen-edu/problem-drafts`를 명시한다. 실제 비밀값을 추가하지 않는다.

- [x] **Step 6: configuration properties를 등록하고 토글 조합을 fail-fast 검증한다**

  `ProblemVisualAuthoringConfig`가 `ProblemVisualAuthoringProperties`와
  `ProblemDraftStorageProperties`를 함께 등록한다. draft root는 절대·정규화된 경로로 제공하고
  preview 크기는 양수여야 한다. `VisualAuthoringConfigurationValidator`는 visual enabled인데 semantic enabled가
  false인 경우 앱 기동을 실패시켜, 이미지가 활성화된 것처럼 보이지만 Legacy로
  조용히 우회하는 오설정을 막는다.

- [x] **Step 7: 오류 코드를 추가한다**

  `PROBLEM_VISUAL_POLICY_VIOLATION`, `PROBLEM_VISUAL_SOURCE_UNSUPPORTED`,
  `PROBLEM_DRAFT_ASSET_NOT_FOUND`, `PROBLEM_DRAFT_ASSET_NOT_READY`,
  `PROBLEM_DRAFT_ASSET_INTEGRITY_FAILED`를 기존 오류 응답 패턴에 맞춘다.

- [ ] **Step 8: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.

  Commit: `feat : 조건부 시각 문항 생성 정책 추가`

### Task 2: Semantic 평가값과 자산 계획 연결

**이 Task의 작업:** LLM이 제안한 도식을 문제의 semantic 평가값과 연결해 도면과 문제 데이터가 같은 수치를 사용하게 만든다.

**현재 문제:** draft 자산 생성 시 실제 semantic 평가값이 아닌 빈 `DiagramRenderContext`가 사용되어 그래프의 계수·범위나 표 내용이 기본값으로 그려질 수 있다.

**개선 방향:** materializer가 평가한 resolved values를 asset plan에 담고, renderer에 동일한 context를 전달한다. 수식·구조 검증은 렌더링 전에 실행한다.

**주요 구현체 담당:** `DefaultProblemSemanticMaterializer` 는 문제 snapshot과 asset plan을 연결하고, `SemanticAssetPlanFactory` 는 typed value를 보관하며, `LocalDraftAssetProductionAdapter` 는 동일한 값으로 SVG를 생성한다.

**기대 방향:** 문제 발문에 표시된 수치와 SVG 안의 수치, 정답의 산출 근거가 일치한다.

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/asset/AssetGenerationSpecification.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticAssetPlanFactory.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/DefaultProblemSemanticMaterializer.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticSnapshotFactory.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/SemanticAssertionValidator.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/validation/ProblemSemanticModelValidator.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecValidator.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingService.java`

**Interfaces:**
- Consumes: `SemanticEvaluation.values()` as `Map<String, SemanticResolvedValue>`
- Produces: `AssetGenerationSpecification.resolvedValues()`
- Produces: `SemanticAssetPlanFactory.create(List<DiagramSpecV1>, Map<String, SemanticResolvedValue>)`
- Produces: `DiagramRenderContext(resolvedValues)`
- Produces: `DraftAssetArtifact` with non-null checksum, widthPx, heightPx
- Produces: `MULTIPLE_CHOICE` 정답 `SnapshotAnswerUnit(answerRaw=choiceKey, compareMethod=CHOICE)`

- [x] **Step 1: 자산 명세에 typed resolved values를 추가한다**

  `Map<String, Object>`만으로 renderer 입력 타입을 추측하지 않는다. 다음 필드를 추가하고 null을 빈 Map으로
  정규화한다.

  ```java
  Map<String, SemanticResolvedValue> resolvedValues
  ```

  기존 5개 인자 보조 생성자는 빈 Map과 null diagram spec으로 직렬화 호환성만
  유지하되, `STRUCTURED_RENDER`의 실제 생성 진입점은 null diagram spec을 거절한다.

- [x] **Step 2: materializer가 동일한 평가 결과를 Snapshot과 asset plan에 전달하게 한다**

  `DefaultProblemSemanticMaterializer.materialize()` 안에서 evaluation을 한 번만 수행하고 다음 두 factory에
  같은 `evaluation.values()`를 전달한다.

  ```java
  QuestionSnapshotV1 snapshot = snapshotFactory.create(model, evaluation.values());
  List<GeneratedAssetPlan> plans = assetPlanFactory.create(model.diagrams(), evaluation.values());
  ```

- [x] **Step 3: 객관식 시각 문항의 정답 choiceKey를 유일하게 재료화한다**

  `SemanticAssertionValidator`는 모든 choice `valueKey`가 resolved value를 참조하는지 검증한다.
  `SemanticSnapshotFactory`는 `intent.targetKey`의 canonical value와 각 choice `valueKey`의 canonical
  value를 비교해 일치 choice가 정확히 1개일 때만 아래 정답을 만든다.

  ```java
  new SnapshotAnswerUnit(
      "MAIN", null, 0, matched.choiceKey(), matched.choiceKey(),
      CompareMethod.CHOICE, null, null)
  ```

  일치 choice가 0개나 2개 이상이면 `SemanticMaterializationException`을 던진다.
  `SHORT_INPUT`은 기존 target value 정답을 유지한다. visual feature가 켜진 동안
  `STEP_FILL`, `ESSAY`는 Task 6의 라우팅에서 Legacy를 사용한다.

- [x] **Step 4: visual content block과 presentation을 바로잡는다**

  모든 SVG 자산 block은 `SnapshotBlockKind.FIGURE`와 `assetRef=diagram.assetKey()`를 사용한다.
  `DATA_TABLE`이면 metadata presentation을 `WITH_TABLE`, 나머지 diagram이면 `WITH_FIGURE`, diagram이 없으면
  `TEXT_ONLY`로 설정한다. `markup=assetKey` 방식은 생성 SVG에서 사용하지 않는다.

- [x] **Step 5: 실제 resolved values로 SVG를 렌더링한다**

  `LocalDraftAssetProductionAdapter`는 diagram spec이 있을 때 다음처럼 처리한다.

  ```java
  RenderedDiagram rendered = renderer.render(
          specification.diagramSpec(),
          new DiagramRenderContext(specification.resolvedValues()));
  ```

  artifact의 checksum, widthPx, heightPx는 `RenderedDiagram` 값을 사용한다. 렌더링 실패를 설명 문자열만
  적힌 placeholder SVG로 바꾸지 않는다. `diagramSpec == null`이어도 기존 `renderSvg(plan)`
  fallback을 호출하지 않고 `DiagramRenderException`을 던진다.

- [x] **Step 6: renderer 기본값 fallback을 필수값 오류로 전환한다**

  좌표 범위, 함수 계수, 표 value key처럼 문제 의미에 필요한 값이 누락되면 default `-10`, `10`, `1`을
  쓰지 않고 `DiagramRenderException`을 발생시킨다. optional tick이나 optional label만 안전한 기본 표현을
  허용한다.

- [x] **Step 7: family별 구조 검증을 구현한다**

  - Number line: `min < max`, `tick > 0`, point·interval 범위 및 참조 key 존재
  - Coordinate graph: 축 최소·최대, tick 양수, point·segment·line 참조, coefficient key 존재
  - Data table: 1~12 행·열, 셀 중복·범위, value/text 둘 중 하나, label 길이
  - Plane geometry: point 참조, polygon 최소 3점, circle radius 양수, angle·arc·measurement target 존재
  - Solid geometry: kind별 필수 dimension, 양수 값, polygonSides 범위

  각 검증은 `Map<String, SemanticResolvedValue>`에서 실제 값을 읽는다.

- [x] **Step 8: 후보 처리 진입점에서 정책과 diagram spec을 함께 검증한다**

  semantic candidate를 다시 materialize한 뒤 Snapshot/plan 동등성, visual policy, diagram spec validation을
  순서대로 수행한다. 검사에 빈 Map을 전달하지 않는다.

- [x] **Step 9: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.

  Commit: `feat : 의미 평가값 기반 SVG 자산 생성 연결`

### Task 3: 자산 역할·최종 영속화·검증 계약 정합화

**이 Task의 작업:** semantic 자산의 key·role·presentation·content block을 맞추고, draft 와 최종 문제 자산이 같은 자료를 참조하게 한다.

**현재 문제:** 표도 현재 `FIGURE` 역할로 저장되거나, 문제 presentation이 `TEXT_ONLY`로 남는 등 프론트가 이미지를 정상적으로 표시하지 못할 수 있다.

**개선 방향:** `DATA_TABLE` 은 `AssetRole.TABLE`, 나머지 도식은 `AssetRole.FIGURE`로 일관되게 정의하고, `assetRef`와 최종 자산 엔티티를 서버가 검증한다.

**주요 구현체 담당:** `ProblemSnapshotEntityMapper` 는 role을 영속화하고, `ProblemCandidateProcessingService` 는 문제와 자산 참조를 검증하며, `DiagramSpecValidator` 는 family별 도식 스키마를 검증한다.

**기대 방향:** 자산 누락·중복·잘못된 role로 인해 교사 화면과 최종 문제 조회가 다르게 보이는 문제를 제거한다.

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/semantic/materialization/SemanticAssetPlanFactory.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/visual/VisualSnapshotConsistencyValidator.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSnapshotEntityMapper.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAuthoringFinalizationService.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/AssetChecks.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationPrompts.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationLlmClient.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationStructuredOutputSchemas.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapter.java`

**Interfaces:**
- Produces: `DATA_TABLE → AssetRole.TABLE`
- Produces: other diagram kinds → `AssetRole.FIGURE`
- Consumes: `Map<String, GeneratedAssetPlan>` in persistence mapping
- Produces: `void VisualSnapshotConsistencyValidator.validate(ProblemSemanticModelV1, QuestionSnapshotV1, List<GeneratedAssetPlan>)`
- Produces: `VerificationLlmClient.judgeAsset(QuestionSnapshotV1, DraftAssetManifest)`
- Produces: `AssetChecks.assetIntegrity(QuestionSnapshotV1, DraftAssetManifest)`

- [x] **Step 1: diagram kind에서 asset role을 결정한다**

  `SemanticAssetPlanFactory`는 `DATA_TABLE`만 `AssetRole.TABLE`, 나머지는 `AssetRole.FIGURE`로 만든다.
  SVG 표의 content block도 `FIGURE + assetRef`를 사용한다. 기존 `TABLE` block은 계약상
  `markup`용이므로 asset key를 markup에 넣지 않고, 표 의미는 `AssetRole.TABLE`과
  `WITH_TABLE` presentation으로 표현한다.

- [x] **Step 2: entity mapper가 plan role을 사용하게 한다**

  `ProblemSnapshotEntityMapper.map()`에 asset plan 또는 `Map<String, AssetRole>`을 전달하고 모든 자산을
  FIGURE로 고정하는 코드를 제거한다. Snapshot asset key와 plan key가 없으면 영속화를 거절한다.

- [x] **Step 3: 최종화에서 자산 plan map을 mapper로 전달한다**

  최종화 transaction 안에서 이미 만드는 `plans` map을 중복 생성하지 않고 mapper와 storage task 생성에
  함께 사용한다. Snapshot asset·plan·artifact key set이 정확히 같고 artifact가 `READY`,
  `image/svg+xml`, 양수 width/height, checksum을 가진 경우만 영속화한다. draft 파일은
  S3 업로드 성공 전까지 삭제하지 않는다.

- [x] **Step 4: materialize 후 Snapshot·발문·자산 정합성을 Java로 검증한다**

  `VisualSnapshotConsistencyValidator`는 visualRequired인 경우 kind별 구체 대상이 발문에
  표시되는지 확인한다. `COORDINATE_GRAPH→좌표 그래프/좌표평면`,
  `DATA_TABLE→표`, `NUMBER_LINE→수직선`, `PLANE_GEOMETRY→평면도형/구체 도형명`,
  `SOLID_GEOMETRY→입체도형/구체 입체명`을 구분한다. generic `다음 그림`만 있는
  경우는 거절한다. visualRequired=false인데 발문이 시각 대상을 필수로 참조해도
  거절한다. FIGURE block, `assetRef`, Snapshot asset, plan key·role·presentation의 정확한
  일치도 검증한다.

- [x] **Step 5: 자산 필요성 검증 입력에 visual kind와 구조 요약을 포함한다**

  자산 검증기는 SVG 전문을 LLM에 보내지 않는다. 발문, asset key, alt text, visual kind, render spec 구조
  요약을 사용해 `MISMATCH`, `LEAK`, `UNNECESSARY`, `TEXT_DUPLICATION`,
  `GENERIC_REFERENCE`를 검사한다. 이미지를 제거해도 유일 정답을 구할 수 있거나
  본문이 자산의 모든 정보를 중복하면 검증 실패로 처리한다. Java 검사는 manifest
  READY, key·role 일치, checksum·width·height 존재를 판정한다.

  `ProblemVerificationAdapter` → `AssetChecks.assetIntegrity(snapshot, manifest)` →
  `VerificationLlmClient.judgeAsset(snapshot, manifest)` 순으로 manifest plan을 전달한다.
  `VerificationPrompts.assetUserPrompt()`는 assetKey·role·kind·altText·참조 key 목록을
  요약한다. altText 정답 누출을 판정하기 위해 기존처럼 저작 정답은 별도
  대조 섹션에 포함하되, resolved value map, SVG 전문, local path는 포함하지 않는다.
  `VerificationStructuredOutputSchemas.ASSET`도 다섯 이슈 값과 빈 문자열을
  허용하도록 같이 변경한다.

- [x] **Step 6: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.

  Commit: `fix : 시각 자산 역할과 최종 저장 계약 정합화`

### Task 4: S3 이전 draft 미리보기 API 구현

**이 Task의 작업:** 교사가 문제를 확정하기 전에 local draft SVG를 보고 발문·정답과 일치하는지 판단할 수 있게 한다.

**현재 문제:** 현재 초안 이미지는 local 파일로는 저장되어도 교사에게 보여 주는 endpoint가 없고, 운영 container에 draft volume이 없어 재배포 시 preview와 S3 재시도 원본이 사라질 수 있다.

**개선 방향:** `ProblemDraftAssetPreviewService`가 소유권·PASSED·READY 상태·path containment·checksum·파일 크기를 검증한 뒤 `data:` URL로 반환하고, controller는 TEACHER principal과 `ApiResponse`만 담당한다. 생성·preview·정리·S3 worker는 동일한 typed root를 사용하고 운영 container에는 backend 전용 named volume을 mount한다.

**주요 구현체 담당:** `ProblemDraftAssetPreviewController` 는 요청 진입점, `ProblemDraftAssetPreviewService` 는 권한·무결성 검증, `ProblemDraftStorageProperties` 는 공통 root, draft producer·cleanup·storage worker는 동일 경로의 수명주기, Docker compose volume은 배포 간 보존을 담당한다.

**기대 방향:** S3가 다운되어 있거나 아직 저장되지 않은 상태에서도 교사가 신뢰할 수 있는 이미지를 보고 확정할 수 있고, container image 교체가 승인 중인 draft를 유실시키지 않는다.

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetPreviewService.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftPathResolver.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/dto/response/DraftAssetPreviewResponse.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/controller/ProblemDraftAssetPreviewController.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/LocalDraftAssetProductionAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemDraftAssetCleanupService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAssetStorageWorker.java`
- Modify: `Dockerfile`
- Modify: `deploy/docker-compose.prod.yml`
- Modify: `deploy/.env.prod.example`
- Modify: `docs/PROBLEM_API_SPEC.md`

**Interfaces:**
- Produces: `ProblemDraftAssetPreviewService.getPreview(long teacherId, long sessionId, long versionId, String assetKey)`
- Produces: `DraftAssetPreviewResponse(String assetKey, String contentType, String dataUrl, int widthPx, int heightPx, String checksum)`
- Produces: `GET /api/teacher/problems/authoring-sessions/{sessionId}/versions/{versionId}/assets/{assetKey}/preview`
- Produces: `ResponseEntity<ApiResponse<DraftAssetPreviewResponse>>` with `Cache-Control: no-store`
- Consumes: one `ProblemDraftPathResolver` backed by `ProblemDraftStorageProperties` in producer, preview, cleanup, and S3 worker

- [x] **Step 1: preview 응답 DTO를 구현한다**

  DTO에는 local path와 storage key를 넣지 않는다. `dataUrl`은 `data:image/svg+xml;base64,` 접두어를 가진다.

- [x] **Step 2: session·version·artifact 조회와 소유권 검증을 구현한다**

  서비스는 `ProblemAuthoringSessionRepository`, `ProblemAuthoringVersionRepository`,
  `ProblemAuthoringJsonCodec`, `ProblemDraftStorageProperties`를 주입받아 다음 순서로
  검증한다.

  ```text
  sessionId + ownerTeacherId 조회
  → versionId + sessionId 조회
  → version verificationStatus == PASSED
  → manifest에서 assetKey artifact 조회
  → artifact status == READY
  ```

  다른 교사의 session과 존재하지 않는 session은 동일한 외부 오류로 처리한다.

- [x] **Step 3: draft 파일 경로와 무결성을 검증한다**

  ```java
  Path source = draftPathResolver.resolveRegularFile(artifact.draftStorageKey());
  ```

  resolver 내부에서 정규화된 후보가 real root 아래인지, final·parent symbolic link가 없는지,
  `NOFOLLOW_LINKS` 기준 정규 파일인지, symlink를 해석한 실제 경로도 real root 아래인지 확인한다.
  resolver 예외는 서비스가 외부 경로를 노출하지 않는 draft 오류로 변환한다.

  파일 크기는 `previewMaxBytes` 이하, manifest content type은 `image/svg+xml`, sha256은
  artifact checksum과 같아야 한다. byte를 읽은 후 `<svg` root인지 확인하고, 저장 때
  생성한 checksum과 다르면 생성 후 변조로 보고 거절한다. domain service가
  `ai.problem.adapter.SafeSvgSanitizer`를 직접 참조하지 않는다.

- [x] **Step 4: base64 data URL을 생성한다**

  파일 byte를 한 번만 읽고 `Base64.getEncoder().encodeToString(bytes)`로 변환한다. SVG 원문과 data URL을
  로그에 남기지 않는다.

- [x] **Step 5: 교사 Controller를 구현한다**

  `@AuthenticationPrincipal AuthenticatedUser`에서 `memberId()`만 서비스로 전달하고 Controller에서 JWT를
  파싱하지 않는다. body는 `ApiResponse.success(preview)`로 감싸고 `Cache-Control: no-store`를
  헤더에 설정한다.

- [x] **Step 6: API 문서를 갱신한다**

  기존 `docs/PROBLEM_API_SPEC.md` 내용을 보존하면서 endpoint, 인증, READY 선행조건, 응답 예시, 프론트의
  `<img src=dataUrl>` 사용, S3 비의존성을 추가한다. 프론트 연동 계약은
  `Job READY → assets[].assetKey별 preview 호출 → 모든 이미지 load 성공 → 확정 활성`으로
  명시하고, 프론트 코드가 이 백엔드 저장소에 없다는 범위 경계를 기록한다. 프론트 CSP가
  `data:` 이미지를 막는 경우 preview 화면의 `img-src`만 허용하거나 base64를 `Blob` URL로
  변환해야 하며, `load` 실패 시 확정은 계속 비활성 상태여야 한다.

- [x] **Step 7: 운영 draft volume과 공통 root를 연결한다**

  `ProblemDraftPathResolver`는 root의 실제 경로를 기준으로 read/write/delete 상대 경로의
  경계와 symbolic link를 공통 검증한다. `LocalDraftAssetProductionAdapter`,
  `ProblemDraftAssetCleanupService`, `ProblemAssetStorageWorker`, preview service의 개별
  `@Value` 경로를 제거하고 모두 resolver를 주입받는다. `Dockerfile`은
  `/var/lib/cen-edu/problem-drafts`를 만들고 `cenedu` 사용자에게 소유권을 부여한다.
  `deploy/docker-compose.prod.yml`은 backend에만 다음 named volume을 mount한다.

  ```yaml
  services:
    backend:
      environment:
        PROBLEM_DRAFT_ROOT: /var/lib/cen-edu/problem-drafts
      volumes:
        - problem-drafts:/var/lib/cen-edu/problem-drafts
  volumes:
    problem-drafts:
  ```

  `deploy/.env.prod.example`에도 같은 경로를 문서화한다. 현재 단일 backend 배포만
  지원하며, local named volume을 둔 채 replicas를 늘리지 말아야 한다는 운영 제약을
  compose 주석과 API 설계 문서에 남긴다. S3 업로드 성공·TTL 만료·교사 취소 시 삭제되는
  기존 업무 수명주기는 바꾸지 않는다.

- [ ] **Step 8: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.
  `.env.example`의 기존 사용자 hunk와 untracked `docs/PROBLEM_API_SPEC.md`의 원래 내용을
  작업 변경과 구분해 검토하고, 위 Global Constraints의 선택적 staging 규칙을 따른다.

  Commit: `feat : 문제 초안 SVG 미리보기 API 추가`

### Task 5: 시각 정본 조회와 RAG visual kind 색인 구현

**이 Task의 작업:** 저장된 문제에서 원본 시각 유형과 렌더 정보를 읽어 RAG 색인과 조회에 사용한다.

**현재 문제:** 검색 문서에 시각 유형이 없어, 표 문제에 좌표 그래프 결과가 선택되거나 그 반대의 상황이 생길 수 있다.

**개선 방향:** `visual_kind` 칼럼과 문서 레이블을 추가하고, 요청에 필요한 시각 유형이 있으면 SQL에 하드 필터를 추가한다. `UNKNOWN_FIGURE`는 자동 생성 참고에서 제외한다.

**주요 구현체 담당:** `ProblemVisualReferenceQueryService` 는 시각 정본, `ProblemSearchDocumentFactory` 는 문서 레이블, `ProblemSearchIndexJdbcRepository` 는 upsert, `ProblemReferenceJdbcRepository` 는 유형 필터를 담당한다.

**기대 방향:** 시각 의미가 같은 유사·응용 문제만 참고되고, 이미지 없는 문제에 임의의 그림이 연결되지 않는다.

**Files:**
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemVisualReferenceQueryService.java`
- Create: `src/main/resources/db/migration/V20260821_1800__problem_add_visual_search_kind.sql`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/search/SearchIndexingCommand.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/ProblemReferenceQuery.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/retrieval/RetrievedProblemReference.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchIndexingService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/search/ProblemSearchDocumentFactory.java`
- Modify: `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexJdbcRepository.java`
- Modify: `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchIndexWorker.java`
- Modify: `src/main/java/com/cenedu/backend/infra/vector/ProblemReferenceJdbcRepository.java`
- Modify: `src/main/java/com/cenedu/backend/infra/vector/ProblemSearchCandidate.java`

**Interfaces:**
- Produces: `ProblemVisualReferenceQueryService.get(long questionId)` → `VisualReferenceDescriptor`
- Adds: `SearchIndexingCommand.indexSchemaVersion()`; 현재 값 `2`
- Adds: `SearchIndexingCommand.visualKind()`
- Adds: `ProblemReferenceQuery.requiredVisualKind()`
- Adds: `RetrievedProblemReference.visualKind()`
- Adds DB: `problem_search_index.visual_kind VARCHAR(30) NOT NULL DEFAULT 'NONE'`
- Adds DB: `problem_search_index.index_schema_version SMALLINT NOT NULL DEFAULT 1`
- Changes DB task uniqueness: `UNIQUE(question_id)` → `UNIQUE(question_id, index_schema_version)`

- [ ] **Step 1: 저장된 문항의 visual kind 정본 조회를 구현한다**

  같은 Problem 도메인의 `ProblemQuestionRepository`와 `ProblemAssetRepository`를 사용한다. 우선순위는
  semantic model diagram, asset render spec, presentation/role, `UNKNOWN_FIGURE`다. diagram spec JSON은 기존
  `ProblemSemanticDocumentCodec`을 사용하고 임의 `ObjectMapper` parsing을 중복하지 않는다.
  동일 문항에 보존 가능한 visual asset이 2개 이상이거나 semantic diagram과 render spec
  kind가 다르면 `UNKNOWN_FIGURE`로 판정해 자동 생성 근거로 사용하지 않는다.

- [ ] **Step 2: Flyway migration을 추가한다**

  적용되지 않은 새 파일로 다음을 추가한다.

  ```sql
  ALTER TABLE problem_search_index
      ADD COLUMN visual_kind VARCHAR(30) NOT NULL DEFAULT 'NONE',
      ADD COLUMN index_schema_version SMALLINT NOT NULL DEFAULT 1;

  ALTER TABLE problem_search_index_task
      ADD COLUMN index_schema_version SMALLINT NOT NULL DEFAULT 1;

  ALTER TABLE problem_search_index_task
      DROP CONSTRAINT IF EXISTS problem_search_index_task_question_id_key;

  ALTER TABLE problem_search_index_task
      ADD CONSTRAINT ck_problem_search_task_schema_version
          CHECK (index_schema_version >= 1),
      ADD CONSTRAINT uk_problem_search_task_question_schema
          UNIQUE (question_id, index_schema_version);

  UPDATE problem_search_index_task
  SET command = jsonb_set(
      jsonb_set(command, '{indexSchemaVersion}', '1'::jsonb, true),
      '{visualKind}', '"NONE"'::jsonb, true
  )
  WHERE NOT (command ? 'indexSchemaVersion') OR NOT (command ? 'visualKind');

  ALTER TABLE problem_search_index
      ADD CONSTRAINT ck_problem_search_visual_kind CHECK (visual_kind IN (
              'NONE','UNKNOWN_FIGURE','NUMBER_LINE','COORDINATE_GRAPH',
              'DATA_TABLE','PLANE_GEOMETRY','SOLID_GEOMETRY'
          )),
      ADD CONSTRAINT ck_problem_search_schema_version
          CHECK (index_schema_version >= 1);

  CREATE INDEX idx_problem_search_index_visual_kind
      ON problem_search_index (visual_kind, sub_unit_id, question_type, difficulty);
  ```

  실제 구현 시작 시 동일 timestamp 파일 존재 여부를 확인하고 충돌하면 더 늦은 현재 timestamp를 사용한다.

- [ ] **Step 3: index schema v2 command로 기존 문항 재색인 경로를 연다**

  `SearchIndexingCommand` 선두에 `int indexSchemaVersion`을 추가하고
  `ProblemSearchIndexingService.CURRENT_INDEX_SCHEMA_VERSION = 2`를 사용한다. idempotency key는
  아래 문자열을 UUID v3 이름 키로 변환한다.

  ```text
  problem-search:v2:{questionId}
  ```

  `ProblemSearchIndexJdbcRepository.insertPending()`는 `index_schema_version`을 insert한다.
  기존 questionId의 v1 READY task가 있어도 v2 task는 한 번 등록되어야 한다.
  migration이 기존 command JSON에 `indexSchemaVersion=1`, `visualKind=NONE`을 보강하므로
  배포 시점에 남아 있는 v1 PENDING/RETRY_WAIT task도 역직렬화 오류 없이 처리된다.

- [ ] **Step 4: 인덱싱 command와 검색 문서에 visual kind를 추가한다**

  `ProblemSearchIndexingService`가 questionId로 descriptor를 조회해 command에 kind를 넣는다. 검색 문서에는
  다음 레이블을 추가한다.

  ```text
  [시각유형] COORDINATE_GRAPH
  ```

- [ ] **Step 5: JDBC upsert와 row mapping을 확장한다**

  `problem_search_index` insert/update에 `visual_kind`, `index_schema_version`을 포함한다.
  `ON CONFLICT` update에는 두 값과 문서·embedding 갱신을 명시하되 다음 조건을 붙인다.

  ```sql
  WHERE problem_search_index.index_schema_version <= EXCLUDED.index_schema_version
  ```

  이 조건으로 v2가 저장된 뒤 늦게 완료된 v1 작업은 기존 행을 덮지 않는다. 후보 조회 결과가
  visual kind를 `ProblemSearchCandidate`와 `RetrievedProblemReference`로 반환하게 한다.

  `ReadySearchIndexMetadata`에는 `documentHash`와 `indexSchemaVersion`을 함께 반환한다.
  `ProblemSearchIndexWorker`는 READY version이 command version보다 높으면 document 생성과
  embedding 호출 전에 task를 `SKIPPED`로 종료한다. 같은 version에서는 기존처럼 document hash가
  같을 때 skip하고, 조건부 upsert는 두 worker가 동시에 진행된 경우의 downgrade를 막는다.

- [ ] **Step 6: 검색 hard filter를 구현한다**

  `requiredVisualKind`가 null 또는 `NONE`이면 기존 검색 범위를 유지한다. 생성 가능한 kind이면
  `visual_kind = :requiredVisualKind`를 추가한다. `UNKNOWN_FIGURE`는 생성용 후보 검색 조건으로 허용하지
  않는다.

- [ ] **Step 7: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.

  Commit: `feat : 문제 검색에 시각 유형 색인과 필터 추가`

### Task 6: 유사·응용 생성에서 origin 시각 의미 보존

**이 Task의 작업:** 학생의 오답 문제를 바탕으로 유사·응용 문제를 만들 때 원본의 그래프·표 유형을 유지한다.

**현재 문제:** 현재는 유사와 응용 생성에 원본 도식 정보가 충분히 전달되지 않아, 표 문제가 그래프로 바뀌거나 generic “다음 그림”을 모델이 임의로 해석할 수 있다.

**개선 방향:** origin 조회 결과를 `PRESERVE_ORIGIN` 요구로 변환하고, semantic model·render spec·alt text 순서로 의미를 복원한다. 의미를 복원할 수 없는 `UNKNOWN_FIGURE`는 생성을 중단한다.

**주요 구현체 담당:** `PersonalizedProblemGenerationPlanningService` 는 생성 요구를 조립하고, `ProblemSemanticReferenceEnricher` 는 원본 시각 정보를 복원하며, `ProblemSemanticGenerationPromptFactory` 는 종류·스펙을 프롬프트에 전달한다.

**기대 방향:** 학생이 틀린 원본과 같은 유형의 새 문제를 받으며, 정보가 부족한 그림은 신뢰할 수 있는 것처럼 포장하지 않는다.

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/generation/GenerationReference.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemAsyncGenerationService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/FewShotReferenceSerializer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPipeline.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/SpringAiProblemGenerationAdapter.java`

**Interfaces:**
- Consumes: ORIGIN `VisualReferenceDescriptor`, `ProblemSemanticModelV1`
- Produces: general/assessment `VisualGenerationMode.AUTO`
- Produces: SIMILAR/APPLICATION `VisualGenerationMode.PRESERVE_ORIGIN`
- Produces: prompt JSON field `originVisual`
- Produces: visual feature enabled 시 `NONE→Legacy`, `AUTO/PRESERVE_ORIGIN→semantic` 라우팅

- [ ] **Step 1: 일반·종합평가 생성 명령에 AUTO를 적용한다**

  기존 동기 은행 조회 API는 변경하지 않는다. 비동기 부족분 command 중 visual feature가
  켜져 있고 question type이 `MULTIPLE_CHOICE` 또는 `SHORT_INPUT`인 슬롯만
  `VisualGenerationMode.AUTO`를 사용한다. `STEP_FILL`, `ESSAY` 또는 visual feature off는
  `NONE`으로 정규화한다.

- [ ] **Step 2: semantic·Legacy pipeline 라우팅을 visual mode와 일치시킨다**

  `SpringAiProblemGenerationAdapter.generate()`는 아래 순서를 사용한다.

  ```text
  semantic.enabled=false → Legacy
  semantic.enabled=true AND visual.enabled=true AND mode=NONE → Legacy
  semantic.enabled=true AND mode=AUTO/PRESERVE_ORIGIN → semantic
  visual.enabled=false → 기존 semantic feature flag 동작 유지
  ```

  `PRESERVE_ORIGIN`인데 origin semantic/descriptor를 구할 수 없으면 Legacy로 떨어지지
  않고 `PROBLEM_VISUAL_SOURCE_UNSUPPORTED`로 실패한다. 이로써 visual 활성화가
  `STEP_FILL`, `ESSAY`의 기존 생성 흐름을 변경하지 않는다.

- [ ] **Step 3: 맞춤 origin의 visual descriptor를 계획 단계에 연결한다**

  `PersonalizedProblemGenerationPlanningService`가 origin snapshot과 questionId를 확보한 뒤
  `ProblemVisualReferenceQueryService`로 descriptor를 읽는다. origin이 text-only이면 NONE, 생성 가능한
  visual이면 PRESERVE_ORIGIN, UNKNOWN이면 `PROBLEM_VISUAL_SOURCE_UNSUPPORTED`로 AI visual 슬롯을 만들지 않는다.

- [ ] **Step 4: RAG query에 origin visual kind를 전달한다**

  SIMILAR과 APPLICATION query는 origin kind를 `requiredVisualKind`로 전달한다. text-only origin은 visual
  hard filter를 사용하지 않되 생성 requirement는 NONE으로 유지한다.

- [ ] **Step 5: origin semantic enrichment 결과를 GenerationReference에 보존한다**

  저장된 semantic model이 있으면 재호출하지 않는다. 없으면 기존 extraction을 한 번 수행한다. visual
  origin extraction 실패를 `originUnavailable → legacy`로 보내지 말고 visual source unsupported로 종료한다.

- [ ] **Step 6: Few-shot과 semantic prompt에 실제 origin visual 구조와 생성 조건을 포함한다**

  Prompt 입력 예시는 다음 필드를 가진다.

  ```json
  {
    "role": "ORIGIN",
    "visualKind": "COORDINATE_GRAPH",
    "visualAssetKey": "F1",
    "semanticModel": {
      "intent": {"visualRequired": true},
      "diagrams": [{"kind": "COORDINATE_GRAPH"}]
    },
    "directCopyForbidden": true
  }
  ```

  실제 전체 semantic model은 structured JSON으로 전달하되, 원본 정답을 별도 평문 필드로 복제하지 않는다.
  모델에게 SIMILAR은 kind/구조 보존과 값 변경, APPLICATION은 kind 보존과 추론 복잡도 증가를 지시한다.
  AUTO에는 `구체 시각 대상 발문`, `자산 없이 정답 불가/모호`, `본문에 자산 정보
  전부 중복 금지`, `diagram 정확히 1개`를 명시한다. 이 조건을 만족하지
  않으면 `visualRequired=false`, `diagrams=[]`를 출력하게 한다.

- [ ] **Step 7: semantic pipeline에서 생성 후 visual policy를 검증한다**

  parser 결과를 server-owned curriculum으로 교체한 다음 계산·materialize 전에 visual policy를 검사하고,
  materialize 후에도 Snapshot/asset plan 연결을 재검사한다.

- [ ] **Step 8: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.

  Commit: `feat : 유사 응용 문제의 원본 시각 의미 보존`

### Task 7: Experimental 전체 family 웹 생성 준비

**이 Task의 작업:** 운영에서 좌표 그래프·표만 사용하되, local에서는 수직선·평면도형·입체도형까지 같은 흐름으로 테스트할 수 있게 한다.

**현재 문제:** renderer 자체는 여러 `DiagramKind`을 지원하지만, 생성 정책과 실제 웹 흐름에서 어떤 family를 허용할지 일관된 설정이 없다.

**개선 방향:** 설정의 allowlist만 바꿔 local experimental에서 전체 5개 family를 흐름에 태워, 스키마 검증·SVG sanitizer·preview를 동일하게 적용한다. 사진·일러스트를 생성하지 않는다.

**주요 구현체 담당:** `DiagramKind` renderer들은 family별 SVG, `DiagramSpecValidator` 는 스키마, `ProblemVisualAuthoringProperties` 는 환경 allowlist, preview flow는 교사 확인 경로를 담당한다.

**기대 방향:** 운영 안전범위를 해치지 않으면서 나중에 도형 family를 확장할 수 있고, 각 family의 렌더 결과를 실제 브라우저에서 검증할 수 있다.

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/authoring/diagram/DiagramSpecValidator.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/render/NumberLineSvgRenderer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/render/CoordinateGraphSvgRenderer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/render/DataTableSvgRenderer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/render/PlaneGeometrySvgRenderer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/render/SolidGeometrySvgRenderer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/render/ProblemDiagramRenderer.java`
- Modify: `src/main/resources/ai/problem/problem-semantic-model-v1.schema.json`

**Interfaces:**
- Consumes: explicit `allowedKinds`
- Produces: deterministic SVG for all five `DiagramKind`
- Produces: `DiagramRenderException` for missing required values and unsupported spec

- [x] **Step 1: Schema의 diagram family 계약을 allowlist 정책과 맞춘다**

  JSON Schema는 5개 family 구조를 계속 허용하되 서버 allowlist가 운영 범위를 결정한다. 모델이 free-form
  SVG, image URL, base64를 출력할 필드는 추가하지 않는다.

- [x] **Step 2: 모든 renderer에서 조용한 fallback과 빈 SVG를 제거한다**

  `ProblemDiagramRenderer`가 알 수 없는 subtype을 받으면 흰 배경만 반환하지 않고 예외를 발생시킨다.
  각 family는 필수 값 누락, NaN/Infinity, 0으로 나누기, 잘못된 viewport 변환을 예외로 처리한다.

- [x] **Step 3: 좌표 그래프와 표를 운영 수준으로 정리한다**

  축 범위, tick, function coefficient, table cell value를 실제 resolved value에서 읽는다. label escape와
  sanitizer 경계를 유지한다. 표 셀 텍스트가 길이 상한을 넘으면 자르지 말고 spec validation에서 거절한다.

- [x] **Step 4: 수직선·평면·입체도형을 experimental 수준으로 정리한다**

  구조 검증을 통과한 spec만 렌더링하고, 운영 정확도를 보장한다는 주석이나 설정을 추가하지 않는다.
  experimental allowlist에서 실제 웹 preview가 가능하도록 artifact 생성 계약만 동일하게 맞춘다.

- [x] **Step 5: sanitizer 불변조건을 재확인한다**

  output에는 script, `on*` attribute, 외부 URL, javascript scheme, foreignObject가 없어야 한다. 이 검증은
  Task 9 테스트에 포함한다.

- [x] **Step 6: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.

  Commit: `feat : 전체 SVG 도식 family 실험 생성 허용`

### Task 8: 검색 백필 주기와 반복 부하 완화

**이 Task의 작업:** 문제 검색 백필을 신규 자료 인덱싱과 분리하고, 유일한 누락 보정 용도로 저빈도 실행한다.

**현재 문제:** scheduler가 60초마다 백필을 실행하고, 끝에서 cursor를 0으로 되돌려 같은 문제 ID를 반복 조회하며 Hibernate bind TRACE가 반복된다.

**개선 방향:** initial delay 10분, fixed delay 1시간, batch 50을 사용하고, exhausted 상태에서도 마지막 cursor를 유지한다. 신규 확정 문제는 기존처럼 즉시 인덱싱 큐에 들어간다.

**주요 구현체 담당:** `ProblemRagProperties` 는 백필 설정, `ProblemSearchBackfillScheduler` 는 스케줄과 cursor 정책, `ProblemSearchBackfillService` 는 batch 조회·큐 등록, 요약 로그는 식별자와 건수만 담당한다.

**기대 방향:** 매분 반복 조회로 인한 DB·Hibernate 부하를 낮추고, 누락 데이터 보정은 지속한다.

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/config/ProblemRagProperties.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillScheduler.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSearchBackfillService.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`

**Interfaces:**
- Adds: `ProblemRagProperties.Indexing.backfillInitialDelay()`
- Adds: `ProblemRagProperties.Indexing.backfillDelay()` default `1h`
- Adds: `ProblemRagProperties.Indexing.backfillBatchSize()` default `50`
- Preserves: `ProblemSearchBackfillService.enqueueBatch(long afterQuestionId, int batchSize)`

- [x] **Step 1: backfill 전용 설정을 분리한다**

  indexing worker의 `batchSize=20`, `workerDelay=5s`는 PENDING task 처리 설정으로 유지한다. backfill에는
  `initialDelay=10m`, `delay=1h`, `batchSize=50`을 별도로 추가한다.

- [x] **Step 2: 스케줄 annotation을 저빈도 설정으로 교체한다**

  ```java
  @Scheduled(
      initialDelayString = "${app.problem.rag.indexing.backfill-initial-delay:10m}",
      fixedDelayString = "${app.problem.rag.indexing.backfill-delay:1h}"
  )
  ```

- [x] **Step 3: cursor를 0으로 되돌리지 않는다**

  결과가 exhausted여도 `cursor = result.nextQuestionId()`를 유지한다. 빈 batch는 기존 cursor를 반환해야 한다.
  다음 실행은 더 큰 신규 question ID만 조회한다.
  backfill이 등록하는 command는 Task 5의 `indexSchemaVersion=2`를 사용하므로 같은
  questionId의 v1 READY task가 있어도 재색인 큐에 한 번 들어가야 한다.

- [x] **Step 4: 백필 요약 로그를 추가한다**

  다음 필드만 INFO로 기록한다.

  ```text
  event=problem_search_backfill cursorBefore cursorAfter scanned enqueued rejected exhausted elapsedMs
  ```

  문항 ID 목록, 본문, 정답, command JSON은 로그에 남기지 않는다.

- [x] **Step 5: `.env.example`에 부하 정책을 설명한다**

  개발자가 60초로 낮추면 DB 조회가 반복된다는 경고와 운영 권장 기본값을 적는다. 사용자 기존 수정과
  병합해 해당 구간만 추가한다.

- [x] **Step 6: 구현 파일만 검토하고 커밋한다**

  Gradle 명령은 실행하지 않는다.

  Commit: `perf : 문제 검색 백필 주기와 반복 조회 완화`

### Task 9: 모든 구현 완료 후 테스트 작성·보강 및 관련 테스트 실행

**이 Task의 작업:** Task 1~8에서 전체 구현이 끝난 후, 정책·렌더·preview·RAG·백필을 한 번에 검증하는 테스트를 작성하고 관련 테스트만 실행한다.

**현재 문제:** 이미지 생성 흐름의 주요 부분에 대한 회귀 테스트가 없어, 설정이나 파일 무결성이 변해도 브라우저에서 발견하기 어렵다.

**개선 방향:** 정책 조건 행렬, family별 validator, semantic·SVG 값 일치, 소유권·checksum, visual kind SQL 필터, 백필 cursor를 각각 독립적으로 검증한 후 생성 Job→preview 흐름을 통합 검증한다. 이 Task에서부터 처음 Gradle 테스트를 실행한다.

**주요 구현체 담당:** 정책·서비스·컨트롤러·JDBC repository 테스트는 각 계층의 계약을 검증하고, `ProblemVisualAuthoringFlowIntegrationTest` 는 전체 시나리오를 담당한다.

**기대 방향:** 모든 생성 코드가 완료된 상태에서 검증하므로 테스트가 중간 설계를 반영하고, 전체 빌드 전에 실패 원인을 제거할 수 있다.

**Files:**
- Create/Modify all files listed in section 2.3

**Interfaces:**
- Consumes: Task 1~8의 최종 public signatures
- Produces: 정책, 렌더링, preview, RAG, backfill 회귀 테스트

- [x] **Step 1: 시각 생성 정책 테스트를 작성한다**

  최소 테스트 행렬은 다음과 같다.

  ```text
  NONE + no diagram → pass
  NONE + diagram → reject
  AUTO + visualRequired=false + no diagram → pass
  AUTO + visualRequired=true + one allowed graph → pass
  AUTO + visualRequired=true + disallowed plane geometry → reject in production allowlist
  AUTO + two diagrams → reject
  PRESERVE_ORIGIN(DATA_TABLE) + DATA_TABLE → pass
  PRESERVE_ORIGIN(DATA_TABLE) + COORDINATE_GRAPH → reject
  AUTO + visualRequired=true + STEP_FILL → reject
  PRESERVE_ORIGIN(UNKNOWN_FIGURE) → reject
  ```

  properties binding이 `MULTIPLE_CHOICE,SHORT_INPUT`을 읽고, visual enabled + semantic disabled 조합이
  기동 검증에서 실패하는 케이스도 포함한다. draft properties가 root를 절대·정규화하고
  preview max bytes의 0 이하 값을 거절하는지도 검증한다.

- [x] **Step 2: family별 validator 테스트를 작성한다**

  각 family에 정상 1건과 다음 실패를 포함한다.

  ```text
  number line: min >= max, tick <= 0, out-of-range point
  coordinate graph: xMin >= xMax, missing coefficient, invalid segment point
  data table: duplicate cell, out-of-range cell, missing value/text
  plane geometry: missing point reference, invalid polygon, non-positive radius
  solid geometry: missing required dimension, non-positive dimension, invalid polygonSides
  ```

- [x] **Step 3: materializer·객관식 정답·renderer 값 일치 테스트를 작성한다**

  계수 3인 정비례 그래프 SVG가 계수 1 SVG와 다른 checksum을 가져야 한다. 표의 semantic cell 값 7이
  SVG text에 포함되어야 한다. Snapshot presentation, content block assetRef, plan role/key를 함께 단언한다.
  객관식에서 target value와 일치하는 choice가 하나면 그 choiceKey가 `CHOICE` 정답이고,
  0개·2개면 materialization이 실패해야 한다. `STRUCTURED_RENDER + null diagramSpec`이
  placeholder SVG를 만들지 않고 실패하는지도 검증한다.

- [x] **Step 4: draft adapter 무결성 테스트를 보강한다**

  `@TempDir`에서 SVG 생성, checksum 재계산, width/height, path containment, atomic replace를 검증한다.

- [x] **Step 5: preview service와 Controller 테스트를 작성한다**

  ```text
  owner teacher + PASSED + READY + valid checksum → data URL
  other teacher → not found/denied
  pending version → not ready
  failed artifact → not ready
  ../ path traversal → integrity error
  symbolic link 또는 parent symbolic link → integrity error
  file too large → integrity error
  checksum mismatch → integrity error
  SVG root가 아닌 파일 → integrity error
  no JWT → 401
  STUDENT JWT → 403
  TEACHER JWT → ApiResponse success
  success response → Cache-Control: no-store
  ```

  `ProblemDraftPathResolverTest`는 정상 상대 경로와 함께 `..`, final symlink,
  parent-directory symlink, root 밖 실제 경로를 producer·preview·cleanup·S3 worker가
  공통으로 거절하는 기반 계약을 검증한다.

- [x] **Step 6: 유사·응용 의미 보존 테스트를 작성한다**

  표 origin은 SIMILAR과 APPLICATION command 모두 `PRESERVE_ORIGIN(DATA_TABLE)`을 가져야 한다. 좌표 그래프
  origin은 같은 kind를 유지해야 한다. generic figure extraction 실패는 Legacy fallback을 호출하지 않고
  visual source unsupported로 종료해야 한다. semantic prompt에 origin diagram kind/spec이 있고 정답 평문
  복제 필드는 없어야 한다.
  visual feature enabled인 경우 `NONE`인 STEP_FILL/ESSAY는 Legacy로, AUTO/PRESERVE는
  semantic으로 이동하는 adapter 라우팅도 검증한다.

  `AssetChecks` 테스트에는 `UNNECESSARY`, `TEXT_DUPLICATION`, `GENERIC_REFERENCE`가
  `ASSET_INCONSISTENT`로 승격 차단되고, SVG 전문은 LLM 입력에 들어가지 않는 것을 포함한다.

- [x] **Step 7: RAG visual kind 테스트를 작성한다**

  검색 문서의 `[시각유형]`, indexing upsert parameter, required visual kind SQL 조건, row mapping을 검증한다.
  DATA_TABLE query가 COORDINATE_GRAPH 후보를 반환하지 않는 repository 통합 테스트를 포함한다.
  이미 v1 READY task가 있는 questionId에 v2 PENDING task가 1개 등록되고, 동일 v2
  재등록은 멱등으로 막히며, worker upsert가 `visual_kind`와 index schema version을
  갱신하는지 검증한다. v2 upsert 뒤 동일 questionId의 v1 upsert를 실행해도 v2 문서·embedding·
  `visual_kind`가 유지되는 순서 역전 케이스를 포함한다. Worker 단위 테스트에서는 READY v2가
  있을 때 v1 task가 `SKIPPED`가 되고 embedding client가 호출되지 않는지도 확인한다.

- [x] **Step 8: 백필 부하 테스트를 작성한다**

  scheduler가 indexing flag가 꺼졌을 때 service를 호출하지 않고, 켜졌을 때 batch size 50을 전달하며,
  exhausted 결과에서도 cursor를 0으로 바꾸지 않는지 검증한다. application property binding이 10m/1h/50을
  읽는지도 검증한다.

- [x] **Step 9: 생성 Job에서 preview까지 통합 테스트를 작성한다**

  Fake semantic generation port 또는 고정 semantic model fixture로 좌표 그래프와 표 후보를 만들고 다음을
  확인한다.

  ```text
  async AI_GENERATION item
  → candidate registration
  → SVG draft READY
  → verification PASSED
  → job slot READY
  → preview endpoint data URL
  → finalize 전 problem_asset/S3 task 없음
  → finalize 후 role이 일치하는 problem_asset PENDING + storage task 생성
  ```

  그래프는 `MULTIPLE_CHOICE`, 표는 `SHORT_INPUT` fixture를 사용해 두 운영 문항 유형을
  모두 검증한다. 외부 OpenAI와 S3는 fake port로 대체해 자격 증명 없이 재현한다.

- [x] **Step 10: 관련 테스트만 실행한다**

  이 단계가 이번 작업의 첫 Gradle 실행이다.

  ```bash
  bash gradlew test \
    --tests 'com.cenedu.backend.domain.problem.authoring.visual.*' \
    --tests 'com.cenedu.backend.domain.problem.authoring.diagram.*' \
    --tests 'com.cenedu.backend.domain.problem.authoring.semantic.materialization.*' \
    --tests 'com.cenedu.backend.ai.problem.adapter.*' \
    --tests 'com.cenedu.backend.ai.problem.adapter.semantic.VisualAuthoringConfigurationValidatorTest' \
    --tests 'com.cenedu.backend.ai.problem.render.*' \
    --tests 'com.cenedu.backend.ai.verification.adapter.AssetChecksVisualNecessityTest' \
    --tests 'com.cenedu.backend.ai.verification.adapter.ProblemVerificationAdapterTest' \
    --tests 'com.cenedu.backend.domain.problem.config.ProblemVisualAuthoringPropertiesTest' \
    --tests 'com.cenedu.backend.domain.problem.config.ProblemDraftStoragePropertiesTest' \
    --tests 'com.cenedu.backend.domain.problem.service.ProblemDraftPathResolverTest' \
    --tests 'com.cenedu.backend.domain.problem.service.ProblemDraftAssetPreviewServiceTest' \
    --tests 'com.cenedu.backend.domain.problem.service.ProblemSnapshotEntityMapperTest' \
    --tests 'com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationServiceTest' \
    --tests 'com.cenedu.backend.domain.problem.controller.ProblemDraftAssetPreviewControllerTest' \
    --tests 'com.cenedu.backend.domain.problem.service.PersonalizedProblemGenerationPlanningServiceTest' \
    --tests 'com.cenedu.backend.domain.problem.service.ProblemVisualReferenceQueryServiceTest' \
    --tests 'com.cenedu.backend.domain.problem.service.ProblemSearchIndexingServiceTest' \
    --tests 'com.cenedu.backend.domain.problem.service.ProblemSearchBackfillSchedulerTest' \
    --tests 'com.cenedu.backend.infra.vector.*' \
    --tests 'com.cenedu.backend.domain.problem.controller.ProblemVisualAuthoringFlowIntegrationTest'
  ```

  Expected: all selected tests PASS. 실패하면 해당 원인만 수정하고 같은 관련 테스트 명령을 다시 실행한다.
  전체 `build`는 아직 실행하지 않는다.

- [x] **Step 11: 테스트 변경을 커밋한다**

  Commit: `test : 조건부 시각 문항 생성과 미리보기 검증 추가`

### Task 10: 웹 시나리오 확인과 전체 빌드 1회

**이 Task의 작업:** 교사 생성·preview·확정·S3 저장과 백필 주기를 실제 웹서비스에서 확인한 뒤, 전체 빌드 명령을 단 한 번 실행한다.

**현재 문제:** 여러 컴포넌트가 연결되는 시점에서는 단위 테스트만으로 교사가 이미지를 보고 확정하는 실제 흐름과 백필 시간을 확인할 수 없다.

**개선 방향:** 운영 allowlist와 local experimental allowlist를 나누어 그래프·표 미리보기, 전체 family 실험, 확정 후 S3 URL, 백필 초기 지연을 순차대로 확인한 후 `bash gradlew build` 단 한 번을 실행한다.

**주요 구현체 담당:** 웹 API와 브라우저는 교사 미리보기 결과, S3 storage worker는 확정 후 영속 URL, scheduler는 백필 지연과 요약 로그, Gradle은 최종 전체 빌드 결과를 담당한다.

**기대 방향:** 교사가 S3 저장 전에 실제 이미지를 확인하고, 확정 후에만 영구 URL이 발급되며, 전체 빌드는 이 전제를 검증한 후 단 한 번 실행된다.

**Files:**
- Verify only: no new implementation file unless Task 9에서 확인된 결함 수정이 필요함

**Interfaces:**
- Consumes: 모든 Task의 최종 구현과 테스트
- Produces: 웹서비스 검증 기록과 최종 build 결과

- [ ] **Step 1: 실행 환경 값을 확인한다**

  비밀값을 출력하지 않고 다음 키의 설정 여부만 확인한다.

  ```text
  PROBLEM_SEMANTIC_AUTHORING_ENABLED=true
  PROBLEM_VISUAL_AUTHORING_ENABLED=true
  PROBLEM_VISUAL_ALLOWED_KINDS=COORDINATE_GRAPH,DATA_TABLE
  PROBLEM_VISUAL_ALLOWED_QUESTION_TYPES=MULTIPLE_CHOICE,SHORT_INPUT
  PROBLEM_DRAFT_ROOT=/var/lib/cen-edu/problem-drafts (production container)
  OPENAI_API_KEY is configured for live generation
  S3_ENABLED는 draft preview 검증에 필수가 아님
  ```

  설정 유무만 확인하고 값을 로그에 출력하지 않는다. live OpenAI 자격 증명이
  없으면 Step 2·3을 실행한 것처럼 보고하지 않고 외부 환경 blocker로 명시한다.

  운영 compose를 사용하는 경우 `docker compose config`로 backend에만
  `problem-drafts:/var/lib/cen-edu/problem-drafts`가 mount되는지 확인한다. image build는
  이 단계에서 다시 수행하지 않는다.

- [ ] **Step 2: 운영 allowlist 웹 시나리오를 확인한다**

  Swagger 또는 브라우저 HTTP 호출로 일반·종합평가 비동기 AI 부족분을 생성하고 Job READY 후
  graph/table asset key로 preview API를 호출한다.
  data URL을 브라우저 `<img>`에서 열어 발문 수치와 SVG 수치가 일치하는지 확인한다. 교사 확정 전 S3 자산이
  없어도 이미지가 보여야 한다.
  운영 검증 환경에서는 draft를 만든 뒤 backend container를 재생성하고 같은 preview가
  TTL 이내에 다시 조회되는지도 확인한다. 이는 image rebuild 없이 기존 tag로 수행한다.

- [ ] **Step 3: local experimental 전체 family 시나리오를 확인한다**

  local 설정에서 allowlist를 5개 family로 확장하고 각 family 고정 fixture 또는 생성 요청을 한 건씩 preview한다.
  이 확인은 운영 allowlist를 변경하지 않는다.

- [ ] **Step 4: 확정 후 기존 S3 흐름을 확인한다**

  graph/table session을 학습지 생성으로 최종화하고 storage task가 PENDING→READY가 되는지, 최종 문제 조회의
  `assets[].url`이 발급되는지 확인한다. 이 Step에서만 `S3_ENABLED=true`와 AWS 자격 증명이
  필요하다. 자격 증명이 없으면 Task 9의 fake storage 통합 테스트 결과와 수동 미실행
  blocker를 분리해 보고한다. S3 업로드 성공 후 draft 파일 삭제를 확인한다.

- [ ] **Step 5: 백필 주기 설정과 로그를 확인한다**

  기동 직후 백필이 실행되지 않고 initial delay 이후 한 번 실행되는지, 요약 로그가 cursor와 count만 담는지
  확인한다. 테스트를 위해 운영 기본값을 60초로 변경하지 않는다.

- [ ] **Step 6: 프론트엔드 연동 경계를 작업 보고에 남긴다**

  현재 저장소에 프론트엔드 코드가 없으므로 API만으로 “교사 화면 연동 완료”를
  주장하지 않는다. `docs/PROBLEM_API_SPEC.md`의 호출 순서와 예외 처리를 프론트
  담당자에게 전달할 후속 항목으로 기록한다.

- [ ] **Step 7: 전체 빌드를 정확히 한 번 실행한다**

  ```bash
  bash gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`.

  전체 빌드는 자동 재실행하지 않는다. 한 번에 통과할 수 있도록 실행 전에 Task 9 관련 테스트와 정적
  검토를 모두 끝낸다. 실패하면 두 번째 전체 빌드를 즉시 수행하지 않고, 실행 1회와 실패 원인을 작업 보고에
  기록한다.

- [ ] **Step 8: 최종 변경을 커밋한다**

  Commit: `feat : 조건부 시각 문항 생성과 초안 미리보기 완성`

## 4. 최종 수용 체크리스트

- [ ] 운영 allowlist가 좌표 그래프·데이터 표로 제한된다.
- [ ] local 설정으로 5개 family 전체 생성과 preview가 가능하다.
- [ ] text-only 문항은 asset을 만들지 않는다.
- [ ] visualRequired와 diagrams 불일치가 서버에서 거절된다.
- [ ] 구체 시각 대상이 없는 generic 발문과 이미지 정보 중복 문항이 승격되지 않는다.
- [ ] visual 객관식의 semantic target이 유일한 choiceKey 정답으로 재료화된다.
- [ ] visual feature가 켜져도 STEP_FILL·ESSAY는 Legacy 경로를 유지한다.
- [ ] 문제와 SVG가 같은 semantic resolved values를 사용한다.
- [ ] graph/table의 content block, asset key, role, presentation이 일치한다.
- [ ] 교사는 S3 없이 PASSED draft SVG를 볼 수 있다.
- [ ] 다른 교사와 학생은 draft SVG를 볼 수 없다.
- [ ] preview는 symbolic link·root 이탈을 거절하고 `Cache-Control: no-store`를 반환한다.
- [ ] 운영 draft named volume이 container 재생성 사이에 미확정 draft를 보존한다.
- [ ] producer·preview·cleanup·S3 worker가 하나의 typed draft root 설정을 사용한다.
- [ ] 현재 single-backend 제약과 scale-out 선행조건이 배포 문서에 기록된다.
- [ ] 유사·응용 문제는 알려진 origin visual kind를 보존한다.
- [ ] generic figure는 임의 diagram으로 변환되지 않는다.
- [ ] RAG가 visual kind를 색인하고 같은 kind를 필터링한다.
- [ ] 기존 v1 READY task가 있는 문항도 v2 task로 한 번 재색인된다.
- [ ] 늦게 완료된 v1 task가 v2 검색 문서·embedding·visual kind를 덮어쓰지 않는다.
- [ ] 백필은 10분 initial delay, 1시간 주기, batch 50을 사용한다.
- [ ] 백필 cursor는 exhausted 이후 0으로 돌아가지 않는다.
- [ ] 모든 구현 뒤 테스트가 작성·실행된다.
- [ ] 전체 `bash gradlew build`는 마지막에 한 번만 실행된다.
- [ ] 프론트 코드가 없는 저장소 범위와 후속 UI 연동 항목이 작업 보고에 명시된다.
