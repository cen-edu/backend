# 문제 생성·수정 신뢰성 및 성능 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 문제 생성·수정 후보의 검증 오탐과 불필요한 LLM 호출을 줄이고, 요청부터 최종 승격까지 문항 단위로 추적 가능한 생성 파이프라인을 만든다.

**Architecture:** 기존 비동기 Job/Item 구조와 `ProblemCandidateProcessingService`의 후보 등록·검증·승격 경계는 유지한다. 먼저 Job·Item·후보·LLM 호출을 연결하는 관측성을 추가한 뒤, 정규화 오탐과 검증 응답 오류를 제거하고, 비활성 의미 추출 및 무제한에 가까운 중첩 재시도를 호출 예산으로 제어한다. 수정 경로는 확인 턴의 불필요한 LLM 호출을 없애고 전체 Snapshot 출력 대신 제한된 Delta를 적용한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Gradle, JUnit 5, AssertJ, Mockito, Spring AI/OpenAI, PostgreSQL 17

**Spec:** 이 문서의 1~7절이 문제 정의, 목표 설계, 범위 및 성공 기준을 함께 정의한다.

## Global Constraints

- 사용자 프롬프트를 처리하는 문제 수정은 반드시 `AgentDispatcher`를 통과한다.
- `domain.problem..`은 `ai.client..`, `com.openai..`, `org.springframework.ai..`를 직접 참조하지 않는다.
- 다른 도메인의 Repository나 Entity를 직접 참조하지 않고 Service 공개 메서드와 ID를 사용한다.
- 문제 본문, 정답, 사용자 입력 원문, Solver 응답 전문은 로그에 남기지 않는다.
- API 응답은 `ApiResponse<T>`를 유지하고 기존 URL 접두어를 변경하지 않는다.
- 새 설정값은 `application.yaml`과 `.env.example`에 함께 추가하며 비밀값을 저장소에 기록하지 않는다.
- DB 변경이 필요하면 `V{yyyyMMdd_HHmm}__problem_{description}.sql` 형식의 새 Flyway 파일만 추가한다.
- 모든 기능 변경은 실패 테스트 작성, 실패 확인, 최소 구현, 통과 확인 순서로 진행한다.
- 각 단계 완료 시 관련 테스트와 `bash gradlew build`를 통과시킨다.

---

## 1. 작업 배경

현재 문제 생성은 다음 세 경로에서 AI 호출이 발생한다.

1. 일반학습·종합평가 비동기 요청에서 문제은행 재고가 부족한 경우
2. 맞춤 SIMILAR 요청에서 재사용할 유사 문제가 부족한 경우
3. 맞춤 ADVANCED 요청에서 응용 문제를 생성하는 경우

공통 실행 흐름은 다음과 같다.

```text
사용자 요청
→ 요청·교육과정 조건 검증
→ 문제은행 조회 및 재사용 후보 선택
→ 부족분 또는 응용 슬롯을 AI_GENERATION으로 계획
→ Job/Item 저장
→ Item별 비동기 실행
→ 참고 문제 의미 보강
→ AI 후보 생성
→ 후보 Version 등록
→ 내용·자산 검증
→ PASSED 후보만 current로 승격
→ FAILED 후보는 새 후보 생성부터 재시도
```

기존 동기 API인 `/api/teacher/problems/generate`와 `/api/teacher/assessments/generate`는 재고 부족 시 AI를 호출하지 않고 `QUESTION_INVENTORY_INSUFFICIENT`를 반환한다. 부족분 생성은 `/generate/async` 경로에서만 수행되므로 프론트엔드 연동 시 두 API를 혼동하지 않아야 한다.

## 2. 현재 문제점

### 2.1 검증 통과율이 낮다

로컬 DB 누적 기록은 다음과 같다. 이 값은 여러 코드 버전의 실행이 섞인 방향성 지표이며, 개선 전후 평가는 동일 버전의 통제된 표본으로 다시 수행한다.

| 작업 | PASSED | FAILED | ERROR | 누적 통과율 |
|---|---:|---:|---:|---:|
| AI_GENERATE | 60 | 86 | 1 | 40.8% |
| AI_MODIFY | 8 | 32 | 1 | 19.5% |

AI 생성 후보의 주요 실패 Finding은 다음과 같다.

| 검증 항목 | 코드 | 건수 | 해석 |
|---|---|---:|---|
| 정답 정확성 | `ANSWER_INCORRECT` | 51 | 실제 오답과 정규화 오탐이 혼재 |
| 답·해설 일관성 | `ANSWER_INCONSISTENT` | 33 | 생성 내용 결함과 LLM 판정 편차가 혼재 |
| 풀이 불가 | `UNVERIFIABLE` | 14 | 실제 조건 오류 또는 Solver 실패 |
| 루브릭 | `RUBRIC_INVALID` | 4 | 서술형 채점 기준 결함 |
| 교육과정 | `CURRICULUM_MISMATCH` | 2 | 요청 범위 이탈 |

확인된 검증 오탐은 다음과 같다.

- `2³×3×5×7`과 `2^3 \times 3 \times 5 \times 7`이 다른 답으로 판정된다.
- `∠D`, `\angle D`, `D`처럼 의미가 같은 각 표기가 문자열 비교에서 어긋난다.
- 현재 `AnswerNormalizer`는 LaTeX 명령과 braced exponent는 처리하지만 `×`, `²`, `³`, `∠`을 처리하지 않는다.

반대로 최대공약수·최소공배수 조건이 서로 양립할 수 없는 문제처럼 검증기가 올바르게 거절한 실제 생성 결함도 존재한다. 따라서 검증 자체를 약화하지 않고 오탐과 실제 생성 오류를 분리해야 한다.

### 2.2 후보 한 건의 실패가 전체 생성 반복으로 확대된다

일반적인 비서술형 후보 한 건은 다음 LLM 호출을 사용한다.

```text
문제 생성 1회
+ Blind Solver 1회
+ 원본·해설 정합성 검사 1회
= 최소 3회의 논리적 LLM 호출
```

검증이 `FAILED`이면 현재 Worker는 생성부터 다시 시작하며 최대 세 후보를 만든다. 따라서 자산 생성과 의미 추출을 제외해도 문항당 최대 9회의 논리적 LLM 호출이 발생한다. 각 논리 호출 내부에는 SDK `max-retries=2`, 호출 제한시간 60초가 적용되어 숨은 재시도와 지연이 더해질 수 있다.

### 2.3 비활성 기능을 위한 LLM 호출이 발생한다

`PROBLEM_SEMANTIC_AUTHORING_ENABLED=false`여도 Worker는 `ProblemSemanticReferenceEnricher`를 호출한다. 맞춤 문제의 ORIGIN 및 일부 EXAMPLE에 의미 모델이 없으면 LLM 추출을 수행하지만, 이후 `SpringAiProblemGenerationAdapter`는 의미 저작이 비활성이므로 결과를 사용하지 않고 Legacy 파이프라인으로 이동한다.

### 2.4 검증 응답 형식 오류가 후보 전체 실패가 된다

검증 LLM은 일반 텍스트를 받은 뒤 JSON을 직접 추출한다. `answers` 누락이나 잘린 JSON이 반환되면 `ERROR`가 되고, Worker는 검증만 재시도하지 않은 채 Item을 실패 처리한다. 이미 생성된 후보를 버리므로 비용과 지연이 모두 낭비된다.

### 2.5 문제 수정은 전체 후보를 반복 생성한다

- 수정 의도 해석 후 확인 턴에서도 다시 LLM을 호출한다.
- 수정 모델이 전체 Snapshot을 반환해 요청하지 않은 필드가 함께 바뀔 수 있다.
- 수정 후보 검증 실패 시 최대 세 번 전체 수정·검증을 반복한다.
- 누적 실패에서 `ANSWER_INCONSISTENT` 32건, `EDIT_REQUIREMENT_MISSING` 18건이 확인됐다.

### 2.6 로그만으로 한 요청을 끝까지 연결할 수 없다

현재 LLM 로그에는 모델, 시간, 토큰 수가 있지만 `jobId`, `itemId`, `sessionId`, 후보 시도 번호가 없다. `AgentDispatcher`의 `traceId`도 비동기 생성 스레드에는 자동 전파되지 않는다. 생성 성공 경로에는 단계별 시간 로그가 부족해 생성·검증·대기 중 어느 구간이 느린지 분리하기 어렵다.

## 3. 작업 목표

1. 요청부터 LLM 호출, 검증, 승격까지 문항 단위로 추적한다.
2. 유니코드 수학 표기로 발생하는 확인된 검증 오탐을 제거한다.
3. 검증 JSON 형식 오류를 구조화 출력과 제한된 검증 재시도로 흡수한다.
4. 의미 저작 비활성 시 의미 추출 LLM 호출을 완전히 생략한다.
5. 후보 재생성, 공급자 재시도, 중첩 호출을 하나의 문항별 호출 예산 안에서 관리한다.
6. 수정 확인 턴과 전체 Snapshot 재생성을 제거해 수정 안정성과 속도를 높인다.
7. 동일한 표본과 지표로 개선 전후를 비교하고 모델 변경은 실험 결과로만 결정한다.

## 4. 작업 범위에서 제외하는 것

- 검증 규칙을 일괄 비활성화하거나 실패를 무조건 통과시키지 않는다.
- 문제 생성·수정 API URL과 응답 계약을 변경하지 않는다.
- 이번 작업에서 RAG 검색 알고리즘이나 임베딩 모델을 교체하지 않는다.
- 문제 원문과 정답을 운영 로그에 기록하지 않는다.
- 현재 브랜치와 갈라진 `feat/backend-problem-total-v1`을 통째로 병합하지 않는다. 필요한 설계와 테스트만 현재 코드에 맞게 선택적으로 이식한다.

## 5. 목표 실행 흐름

```text
요청 접수
→ traceId/jobId/itemId 생성 및 로그 컨텍스트 설정
→ 문제은행 재사용/AI 부족분 수량 기록
→ [semantic 활성일 때만] 참고 문제 의미 보강
→ 후보 생성 (호출 예산 차감)
→ 구조 검증 및 Version 등록
→ Blind Solver 구조화 검증
   ├─ 실제 오답/풀이 불가: 후속 원본 LLM 검사 생략, 후보 재생성 판단
   └─ 응답 형식/일시 오류: 검증 단계만 최대 1회 재시도
→ Java 정규화·구조 검사
→ 원본·해설 구조화 검사
→ PASSED 후보만 승격
→ 모든 단계의 시간·토큰·판정 코드를 문항 ID와 함께 기록
```

## 6. 기대 효과

| 개선 항목 | 기대 효과 | 확인 방법 |
|---|---|---|
| 구조화 로그 | 느린 단계와 실패 문항을 한 번의 조회로 추적 | `traceId` 또는 `itemId`로 전체 로그 검색 |
| 수학 표기 정규화 | 맞는 답이 표기 차이로 탈락하는 오탐 제거 | 정규화 회귀 테스트 및 과거 실패 사례 재검증 |
| 검증 구조화 출력 | 잘린 JSON·필드 누락으로 인한 `ERROR` 감소 | 최근 100건의 검증 공급자 오류율 |
| 검증 전용 재시도 | 생성된 정상 후보를 형식 오류 때문에 버리는 비용 제거 | 후보 재생성 없이 검증 재시도 성공 건수 |
| 의미 추출 조건부 실행 | 맞춤 SIMILAR·ADVANCED의 사용되지 않는 LLM 호출 제거 | semantic 비활성 테스트에서 extraction 호출 0회 |
| 문항별 호출 예산 | 숨은 재시도 폭증과 장시간 대기 방지 | item별 `usedBudget`, `budgetLimit` 로그 |
| Solver 회로 차단 | 선행 검증 오류 후 같은 공급자 호출을 막아 지연 감소 | `CORRECTNESS=ERROR` 뒤 원본 검사 호출 0회 |
| 수정 Delta | 요청하지 않은 필드 변경과 수정 검증 실패 감소 | protected target 불변 테스트 |

초기 운영 목표는 동일 코드 버전에서 충분한 표본이 쌓인 뒤 평가한다.

- 최근 AI 생성 후보 100건 기준 `PASSED` 비율 60% 이상
- 검증 응답 형식 및 공급자 오류율 1% 미만
- 비자산 문항의 논리적 LLM 호출 중앙값 3회 이하, p95 6회 이하
- 일반학습·종합평가 부분 실패 Job의 p95 완료 시간 90초 이하
- 확인된 유니코드 수학 표기 회귀 사례 통과율 100%
- 수정 후보 최근 50건 기준 `PASSED` 비율 50% 이상

## 7. 변경 파일 지도

| 책임 | 파일 |
|---|---|
| 비동기 MDC 전파 | `src/main/java/com/cenedu/backend/domain/problem/config/ProblemGenerationAsyncConfig.java` |
| 문항 실행 단계 로그·재시도 | `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java` |
| 후보 등록·검증·승격 시간 | `src/main/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingService.java` |
| LLM 호출 시간·토큰·예산 로그 | `src/main/java/com/cenedu/backend/ai/client/OpenAiLlmClient.java` |
| 호출 예산 컨텍스트 | `src/main/java/com/cenedu/backend/ai/client/LlmCallBudgetManager.java` |
| 수학 답 정규화 | `src/main/java/com/cenedu/backend/domain/grading/service/AnswerNormalizer.java` |
| Solver·원본 검사 구조화 출력 | `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationLlmClient.java` |
| 검증 JSON Schema | `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationStructuredOutputSchemas.java` |
| 의미 추출 조건부 실행 | `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java` |
| 수정 확인 턴 | `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditApplicationService.java` |
| 수정 Delta 적용 | `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationAdapter.java` |
| 설정 | `src/main/resources/application.yaml`, `.env.example` |

---

### Task 1: 문제 생성 관측성 추가

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/config/ProblemGenerationAsyncConfig.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationPlanningService.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/PersonalizedProblemGenerationPlanningService.java`
- Modify: `src/main/java/com/cenedu/backend/ai/client/OpenAiLlmClient.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/config/ProblemGenerationAsyncConfigTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorkerTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingServiceTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/client/OpenAiLlmClientTest.java`

**Interfaces:**
- Consumes: `ProblemGenerationWorkItem.itemId()`, `jobId()`, `sessionId()`, `command().requestId()`, `command().purpose()`
- Produces: MDC 키 `traceId`, `jobId`, `itemId`, `sessionId`, `operationId`, `operation`, `stage`, `candidateAttempt`

- [x] **Step 1: 비동기 실행 시 MDC 복사·복원 테스트를 작성한다**

  `ProblemGenerationAsyncConfig`가 제출 스레드의 MDC를 작업 스레드로 복사하고 작업 후 이전 값을 복원하는지 검증한다. 작업 간 MDC가 누출되지 않는 두 번째 케이스도 포함한다.

- [x] **Step 2: RED 확인은 사용자 지시에 따라 생략한다**

  RED→GREEN 중간 실행은 생략하고 구현 완료 후 관련 테스트를 실행한다.

- [x] **Step 3: `ThreadPoolTaskExecutor`에 MDC TaskDecorator를 설정한다**

  `MDC.getCopyOfContextMap()`, `MDC.setContextMap()`, `MDC.clear()`를 `try/finally`로 사용한다. 원문 데이터는 MDC에 넣지 않는다.

- [x] **Step 4: Worker 단계 로그 테스트를 작성한다**

  생성 성공, 검증 실패 후 재시도, 검증 ERROR 종료 케이스에서 다음 필드가 존재하는지 확인한다.

  ```text
  event=problem_authoring_stage
  jobId itemId sessionId operationId purpose stage candidateAttempt outcome elapsedMs
  ```

- [x] **Step 5: Worker와 후보 처리 서비스에 단계별 시간 로그를 구현한다**

  단계 값은 `PLANNING`, `ENRICHMENT`, `GENERATION`, `REGISTRATION`, `ASSET`, `VERIFICATION`, `PROMOTION`으로 고정한다. 실패 로그에는 예외 메시지 원문 대신 오류 코드와 예외 타입만 남긴다.

- [x] **Step 6: LLM 로그에 문항 컨텍스트를 추가한다**

  기존 모델·토큰·시간 로그에 MDC 식별자와 `apiAttempt`, `outcome`을 추가한다. prompt/response는 길이만 기록한다.

- [x] **Step 7: 관련 테스트를 통과시키고 커밋한다**

  Run: `bash gradlew test --tests '*ProblemGenerationAsyncConfigTest' --tests '*ProblemGenerationWorkerTest' --tests '*ProblemCandidateProcessingServiceTest' --tests '*OpenAiLlmClientTest'`

  Commit: `feat : 문제 생성 단계별 추적 로그 추가`

### Task 2: 수학 표기 정규화 오탐 제거

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/grading/service/AnswerNormalizer.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemGenerationPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/semantic/ProblemSemanticGenerationPromptFactory.java`
- Test: `src/test/java/com/cenedu/backend/domain/grading/service/RuleGraderTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/grading/service/AnswerNormalizerTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapterTest.java`

**Interfaces:**
- Consumes: `AnswerNormalizer.normalize(String raw, String displayUnit)`
- Produces: 유니코드 수학 표기를 기존 ASCII/LaTeX 정규형으로 변환한 문자열

- [x] **Step 1: 실제 오탐 사례를 회귀 테스트로 추가한다**

  최소 케이스는 다음과 같다.

  ```java
  assertThat(normalizer.normalize("2³×3×5×7", null))
          .isEqualTo("2^3*3*5*7");
  assertThat(normalizer.normalize("∠D", null))
          .isEqualTo("D");
  ```

  `⁰`~`⁹`, `⁻`, `×`, `·`, `÷`, `∠`의 대표값과 정규화 멱등성도 포함한다.

- [x] **Step 2: RED 단계는 사용자 지시에 따라 생략하고 구현 후 회귀 테스트로 검증한다**

  Run: `bash gradlew test --tests '*RuleGraderTest'`

  Expected: 유니코드 곱셈·위첨자·각 기호 케이스 FAIL.

- [x] **Step 3: 유니코드 수학 표기를 LaTeX 처리 전에 변환한다**

  위첨자는 연속 문자를 하나의 지수로 합치고 `⁻`는 음수 지수로 변환한다. `×`, `·`은 `*`, `÷`는 `/`, `∠`은 제거한다. 한글 답과 일반 Unicode 문자는 변경하지 않는다. 표시용 텍스트는 프롬프트 계약으로 `$...$`를 사용하며 서버가 임의로 문장 전체를 수식으로 감싸지 않는다.

- [x] **Step 4: RuleGrader와 검증 Adapter 회귀 테스트를 통과시킨다**

  Run: `bash gradlew test --tests '*RuleGraderTest' --tests '*ProblemVerificationAdapterTest'`

- [x] **Step 5: 표시용 KaTeX 계약과 비교용 정답 계약을 프롬프트에 명시하고 커밋한다**

  Commit: `feat : 수식 표시 및 수학 답안 정규화 보강`

### Task 3: 검증 구조화 출력과 검증 전용 재시도 도입

> 보강 범위: 검증 오류가 발견되어도 후보 전체를 재생성하지 않고, 오류가 발생한 구성요소만
> 수정할 수 있도록 부분 수정(Repair Delta) 흐름을 함께 도입한다. 호출 형식 오류·일시적
> 공급자 오류는 검증 호출만 최대 1회 재시도하고, 해설·정답·보기·풀이·루브릭의 내용 오류는
> 대상 필드와 의존 필드만 부분 수정한다. Task 3 내부에서는 후보 생성 Port를 다시 호출하지 않는다.
> 본문 자체가 불완전하거나 부분 수정으로 일관성을 회복할 수 없으면 후보를 종료하고, 전체 재생성
> 여부는 Task 5의 문항별 호출 예산과 Worker 재시도 정책에서 한 번만 결정한다.

**검증 책임 경계:** Java는 자연어 문제를 읽어 독립적으로 정답을 계산하지 않는다.
`BlindQuestionFactory`가 정답·해설을 제거한 문제를 만들고 검증 Solver LLM이 독립 답을 계산한다.
Java의 `AnswerNormalizer`·`RuleGrader`·`ExpressionEvaluator`는 저작측 정답과 Solver 답의 표기 및
수학적 동치만 비교한다. 따라서 두 답이 다르다는 사실만으로 저작측 정답 오류를 단정하지 않는다.

```text
저작측 정답 A ─┐
                ├─ Java 동치 비교 → 일치/불일치
Blind Solver B ─┘

불일치 → 기존 원본 검사에서 원인 판정
       → 두 신호가 저작 오류에 동의할 때만 Repair
```

**자동 수정 합의 조건:** Solver 불일치와 원본 검사를 별도 추가 호출로 만들지 않는다. 기존
원본 검사 응답에 `AUTHORING_ANSWER_WRONG`, `SOLVER_UNCERTAIN`, `QUESTION_AMBIGUOUS`,
`EXPLANATION_INCONSISTENT` 원인을 포함한다. Solver 불일치와 원본 검사가 모두 저작 오류를
지목할 때만 부분 수정한다. 두 신호가 충돌하면 `UNVERIFIABLE`로 종료하며 자동 수정하지 않는다.

**문항별 호출 예산(비자산):** 정상 후보는 생성 1회 + 최초 검증 최대 2회에서 종료한다. 실패
후보도 묶음 Repair 1회와 선택적 재검증 최대 2회만 허용해 논리적 LLM 호출을 최대 6회로 제한한다.
Repair 대상을 고르기 위한 LLM은 호출하지 않고 Java 규칙으로 Finding을 매핑한다. 부분 수정은
문항당 최대 1회이며, 수정 후 실패하면 반복 Repair 없이 종료한다. SDK 내부 재시도는 별도 API
시도이므로 Task 5 호출 예산에서 함께 계측한다.

**Files:**
- Create: `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationStructuredOutputSchemas.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/repair/RepairTarget.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/repair/ProblemRepairPlan.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/repair/ProblemRepairCommand.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/repair/ProblemRepairDelta.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemRepairPort.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemRepairPlanner.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemRepairDeltaMerger.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemRepairAdapter.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemRepairPromptFactory.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/VerificationLlmClient.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/ContentIntegrityChecker.java`
- Modify: `src/main/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingService.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemRepairPlannerTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemRepairDeltaMergerTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/ProblemRepairAdapterTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/verification/adapter/ProblemVerificationAdapterTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemCandidateProcessingServiceTest.java`

**Interfaces:**
- Consumes: `LlmClient.completeStructured(systemPrompt, messages, seed, LlmUseCase.VERIFICATION, outputSchema)`
- Produces: Solver, 원본 검사, 루브릭, 자산 판정별 `additionalProperties=false` JSON Schema
- Produces: `ProblemRepairPlanner.plan(ProblemVerificationBundle)` → 수정 불가 또는 단일 묶음 `ProblemRepairPlan`
- Produces: `ProblemRepairPort.repair(ProblemRepairCommand)` → 허용된 대상 필드만 포함한 `ProblemRepairDelta`
- Produces: `ProblemRepairDeltaMerger.merge(QuestionSnapshotV1, ProblemRepairPlan, ProblemRepairDelta)` → 구조 검증 전 Snapshot

- [x] **Step 1: Solver 구조화 출력 사용 테스트를 작성한다**

  Fake LLM Client가 전달받은 Schema를 기록하도록 하고 `solved`, `answers`, `reason` 필수 필드와 `answers[].unitKey`, `answers[].answer` 필수 여부를 검증한다.

- [x] **Step 2: RED 단계는 사용자 지시에 따라 생략하고 구현 후 회귀 테스트로 검증한다**

  Run: `bash gradlew test --tests '*ProblemVerificationAdapterTest'`

- [x] **Step 3: 네 종류의 검증 Schema를 추가하고 `completeStructured()`로 전환한다**

  Schema는 `SOLVER`, `ORIGINAL`, `RUBRIC`, `ASSET` 네 상수로 분리한다. 기존 파싱 검증은 공급자 계약 위반을 방어하기 위해 유지한다.

- [ ] **Step 4: 검증 일시 오류만 한 번 재시도하는 테스트를 작성한다**

  첫 호출이 `SolverResponseParseException` 또는 재시도 가능한 공급자 오류이고 두 번째 호출이 성공할 때 후보 생성 Port가 다시 호출되지 않고 기존 Version의 검증만 완료되는지 검증한다. 정답 불일치 `FAILED`는 검증 재시도 대상이 아니다.

- [ ] **Step 5: 검증 전용 재시도를 최대 1회 구현한다**

  `ProblemCandidateProcessingService.callVerification()` 경계에서 오류 종류를 분류한다. 형식 오류와 일시 공급자 오류만 같은 `verificationRequestId` 문맥에서 한 번 재시도하고, 인증·쿼터·잘못된 요청 오류는 즉시 종료한다.

- [ ] **Step 6: Solver ERROR 회로 차단을 검증한다**

  `CORRECTNESS=ERROR`이면 Java 검사는 계속 수행하되 원본·해설 LLM 검사는 호출하지 않고 관련 Finding을 `ERROR`로 남기는 기존 동작을 회귀 테스트로 고정한다.

- [ ] **Step 7: 테스트를 통과시키고 커밋한다**

  Run: `bash gradlew test --tests '*ProblemVerificationAdapterTest' --tests '*ProblemCandidateProcessingServiceTest'`

  Commit: `fix : 문제 검증 구조화 출력과 제한 재시도 적용`

#### Task 3 하위 범위: 오류 항목 부분 수정

- [ ] **Step 3-A: Solver 불일치의 원인을 기존 원본 검사에서 함께 판정한다**
  - 원본 검사 Schema에 `answerMismatchCause`를 추가하고 값은 `NONE`, `AUTHORING_ANSWER_WRONG`,
    `SOLVER_UNCERTAIN`, `QUESTION_AMBIGUOUS`, `EXPLANATION_INCONSISTENT`로 제한한다.
  - 별도 판정 LLM 호출은 추가하지 않는다.
  - Solver와 원본 검사가 저작 오류에 동의하지 않으면 `UNVERIFIABLE`로 종료한다.
- [ ] **Step 3-B: 검증 Finding에 수정 대상과 수정 이유를 Java 규칙으로 구조화한다**
  - `CONTENT`, `CHOICES`, `ANSWERS`, `STEPS`, `EXPLANATION`, `RUBRIC` 대상과 의존 필드를 정의한다.
  - 각 Finding에 왜 틀렸는지와 어떤 값을 재생성해야 하는지 기록한다.
- [ ] **Step 3-C: 모든 오류 항목을 한 번에 수정하는 묶음 Repair Delta를 추가한다**
  - 필드마다 LLM을 따로 호출하지 않고 모든 RepairTarget을 한 요청으로 전달한다.
  - 전체 Snapshot이 아니라 수정 대상 필드만 반환하는 `ProblemRepairPort`를 추가한다.
  - 수정 대상 외 필드가 응답에 포함되면 계약 위반으로 거부한다.
- [ ] **Step 3-D: Snapshot Delta Merger와 수정 후 구조 검증을 연결한다**
  - 기존 Snapshot의 문제 유형·난이도·교육과정·수정 대상 외 필드는 보존한다.
  - `CHOICES→ANSWERS`, `STEPS→ANSWERS/EXPLANATION`, `CONTENT→연관 전체` 의존성을 반영한다.
- [ ] **Step 3-E: 수정 범위별 선택적 재검증과 호출 예산을 적용한다**
  - `EXPLANATION`·`RUBRIC`은 관련 원본 검사 1회, `CHOICES`·`ANSWERS`·`STEPS`는 Solver와 원본 검사 최대 2회만 수행한다.
  - 부분 수정은 문항당 최대 1회, 비자산 논리적 LLM 호출은 생성부터 최대 6회로 제한한다.
  - 수정 후 관련 검사와 비용 없는 Java 구조·정규화 검사를 수행한다.
  - 본문 정보 부족·교육과정 이탈·신호 충돌은 부분 수정하지 않고 종료한다.
- [ ] **Step 3-F: 해설·정답·보기·풀이·루브릭 부분 수정 회귀 테스트를 통과시키고 커밋한다**
  - 검증 오류와 내용 오류를 구분하고, 후보 생성 Port가 재호출되지 않는지 검증한다.
  - 여러 오류가 있어도 Repair Port가 정확히 1회만 호출되는지 검증한다.
  - Solver와 원본 검사 신호가 충돌하면 Repair Port가 0회인지 검증한다.
  - 정상 후보 3회, 해설 수정 5회, 정답·보기 수정 최대 6회 호출 예산을 검증한다.
  - Commit: `feat : 검증 오류 항목 부분 수정 흐름 추가`

### Task 4: 의미 저작 비활성 시 의미 추출 호출 제거

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricher.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemSemanticReferenceEnricherTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorkerTest.java`

**Interfaces:**
- Consumes: `app.problem-authoring.semantic.enabled`
- Produces: 비활성 시 원본 `ProblemGenerationCommand`를 그대로 반환하고 extraction 호출을 0회로 보장

- [ ] **Step 1: semantic 비활성 회귀 테스트를 작성한다**

  ORIGIN과 EXAMPLE이 모두 있어도 `ProblemSemanticExtractionService.ensureQuestionSemantic()`이 호출되지 않고 command 참조가 보존되는지 검증한다.

- [ ] **Step 2: 현재 무조건 추출 동작 때문에 테스트가 실패하는지 확인한다**

  Run: `bash gradlew test --tests '*ProblemSemanticReferenceEnricherTest' --tests '*ProblemGenerationWorkerTest'`

- [ ] **Step 3: Enricher에 활성 조건을 추가한다**

  비활성 시 즉시 `new SemanticReferenceEnrichmentResult(command, false)`를 반환한다. 활성 시 ORIGIN과 응용 문제 EXAMPLE 최대 2개를 보강하는 기존 규칙을 유지한다.

- [ ] **Step 4: 활성·비활성 테스트를 모두 통과시키고 커밋한다**

  Run: `bash gradlew test --tests '*ProblemSemanticReferenceEnricherTest' --tests '*SpringAiProblemGenerationAdapterTest' --tests '*ProblemGenerationWorkerTest'`

  Commit: `fix : 의미 저작 비활성 시 참고 문제 추출 생략`

### Task 5: 문항별 LLM 호출 예산과 재시도 상한 적용

**Files:**
- Create: `src/main/java/com/cenedu/backend/ai/client/LlmCallBudgetManager.java`
- Create: `src/main/java/com/cenedu/backend/ai/client/OpenAiFailureClassifier.java`
- Create: `src/main/java/com/cenedu/backend/domain/problem/authoring/port/ProblemAiExecutionBudgetPort.java`
- Create: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemAiExecutionBudgetAdapter.java`
- Modify: `src/main/java/com/cenedu/backend/ai/client/OpenAiLlmClient.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorker.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemModificationWorker.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Test: `src/test/java/com/cenedu/backend/ai/client/LlmCallBudgetManagerTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/client/OpenAiFailureClassifierTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/client/OpenAiLlmClientTest.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemGenerationWorkerTest.java`
- Create: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemModificationWorkerTest.java`

**Interfaces:**
- Consumes: 작업별 `operationId`, `itemId`, `sessionId`, `operation`, `stage`, `candidateAttempt`
- Produces: `ProblemAiExecutionBudgetPort.Scope`, `stage(Stage stage, int candidateAttempt)`, `close()`

- [ ] **Step 1: 호출 예산 단위 테스트를 작성한다**

  같은 Scope에서 호출이 설정 한도를 넘으면 `AI_CLIENT_CALL_BUDGET_EXHAUSTED`가 발생하고, 중첩 Scope가 서로 카운터를 공유하지 않는지 검증한다.

- [ ] **Step 2: 공급자 오류 분류 테스트를 작성한다**

  인증, 쿼터, 잘못된 요청, 일시 오류, 알 수 없는 오류를 분리하고 일시 오류만 재시도 가능하도록 고정한다.

- [ ] **Step 3: 문제 저작 경로의 SDK 숨은 재시도를 제거한다**

  `PROBLEM_GENERATION`, `PROBLEM_MODIFICATION`, `VERIFICATION`은 SDK `maxRetries(0)`를 사용하고 애플리케이션에서 실제 API 시도 수를 직접 센다. 일시 오류만 추가 1회 허용한다.

- [ ] **Step 4: 후보 최대 횟수를 3회에서 2회로 줄이는 테스트를 작성한다**

  생성 실패, 검증 FAILED 각각에서 생성 Port가 최대 2회만 호출되고 마지막 실패 코드가 보존되는지 검증한다.

- [ ] **Step 5: Worker에 예산 Scope와 최대 2후보 규칙을 구현한다**

  초기값은 생성 8회, 수정 8회로 두되, Task 7의 측정 후 조정한다. 쿼터·인증·잘못된 요청·예산 소진은 후보 재생성을 하지 않는다.

- [ ] **Step 6: 설정과 환경변수 예시를 추가한다**

  ```yaml
  app.ai.problem.call-budget.generation: ${AI_PROBLEM_GENERATION_CALL_BUDGET:8}
  app.ai.problem.call-budget.modification: ${AI_PROBLEM_MODIFICATION_CALL_BUDGET:8}
  ```

- [ ] **Step 7: 테스트를 통과시키고 커밋한다**

  Run: `bash gradlew test --tests '*LlmCallBudgetManagerTest' --tests '*OpenAiFailureClassifierTest' --tests '*OpenAiLlmClientTest' --tests '*ProblemGenerationWorkerTest' --tests '*ProblemModificationWorkerTest'`

  Commit: `feat : 문제 저작 LLM 호출 예산과 재시도 상한 적용`

### Task 6: 문제 수정 확인 턴과 Delta 출력 안정화

**Files:**
- Modify: `src/main/java/com/cenedu/backend/domain/problem/dto/request/ProblemEditTurnRequest.java`
- Modify: `src/main/java/com/cenedu/backend/domain/problem/service/ProblemEditApplicationService.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/ProblemStructuredOutputSchemas.java`
- Modify: `src/main/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationAdapter.java`
- Test: `src/test/java/com/cenedu/backend/domain/problem/service/ProblemEditApplicationServiceTest.java`
- Test: `src/test/java/com/cenedu/backend/ai/problem/adapter/ProblemModificationAdapterTest.java`

**Interfaces:**
- Consumes: 명시적 확인 여부, `PendingProblemEditCommand`, 기준 `QuestionSnapshotV1`
- Produces: 허용 필드만 포함하는 수정 Delta와 서버에서 재구성한 완전한 후보 Snapshot

- [ ] **Step 1: 명시적 확인 턴이 LLM을 호출하지 않는 테스트를 작성한다**

  pending 명령이 있는 세션에서 확인 요청을 받으면 `ProblemEditAgentGateway.handle()` 호출이 0회이고 저장된 명령을 즉시 실행하는지 검증한다. pending이 없거나 baseVersion이 달라졌으면 `PROBLEM_EDIT_COMMAND_STALE`을 유지한다.

- [ ] **Step 2: 현재 확인 턴 LLM 호출 때문에 테스트가 실패하는지 확인한다**

  Run: `bash gradlew test --tests '*ProblemEditApplicationServiceTest'`

- [ ] **Step 3: 요청 계약과 Application Service에 확인 분기를 구현한다**

  사용자 자유문을 `예/아니오`로 다시 해석하지 않고 프론트가 명시적으로 전달한 확인 상태만 사용한다. 최초 수정 요청은 기존대로 `AgentDispatcher`를 통과한다.

- [ ] **Step 4: 수정 Delta Schema 테스트를 작성한다**

  요청 대상이 `EXPLANATION`이면 모델 출력에서 문제 본문, 정답, 메타데이터를 반환할 수 없고, 서버가 기준 Snapshot의 보호 필드를 그대로 보존하는지 검증한다.

- [ ] **Step 5: 제한된 Delta 출력과 서버 병합을 구현한다**

  모델은 허용된 필드의 변경값만 반환한다. `ProblemModificationAdapter`가 기준 Snapshot에 Delta를 적용한 뒤 기존 구조 Validator와 검증 파이프라인에 전달한다.

- [ ] **Step 6: 수정 회귀 테스트를 통과시키고 커밋한다**

  Run: `bash gradlew test --tests '*ProblemEditApplicationServiceTest' --tests '*ProblemModificationAdapterTest' --tests '*ProblemModificationWorkerTest'`

  Commit: `fix : 문제 수정 확인 호출 제거와 Delta 출력 적용`

### Task 7: 통제된 전후 성능·품질 측정과 운영 기준 확정

**Files:**
- Create: `docs/problem-authoring-reliability-performance-result.md`
- Create: `src/test/java/com/cenedu/backend/ai/problem/adapter/ProblemAuthoringModelComparisonLiveTest.java`
- Modify: `src/main/resources/application.yaml` only when measurement supports a configuration change
- Modify: `.env.example` only when a new optional measurement variable is introduced

**Interfaces:**
- Consumes: 고정된 일반학습, 종합평가, 맞춤 SIMILAR, 맞춤 ADVANCED, 수정 표본과 Task 1 로그
- Produces: 경로별 통과율, Finding 분포, 호출 수, 토큰 수, 단계별 latency, 비용 비교 보고서

- [ ] **Step 1: 비교 표본과 실행 조건을 고정한다**

  각 생성 경로별 최소 20문항, 수정 20건을 사용한다. 요청 JSON, 모델, reasoning effort, temperature/seed, RAG·semantic 토글, 실행 시각을 결과 문서에 기록한다. 문제·정답 원문은 저장소 문서에 넣지 않고 비식별 fixture ID만 기록한다.

- [ ] **Step 2: 개선 전 기준값을 로그와 DB에서 추출한다**

  경로별 `PASSED/FAILED/ERROR`, 실패 코드, item당 logical/API call 수, prompt/completion/reasoning token, 전체·단계별 p50/p95를 기록한다.

- [ ] **Step 3: Task 1~6 적용 후 같은 표본을 재실행한다**

  실행 중 호출 예산을 변경하지 않는다. 공급자 장애가 있으면 해당 실행을 별도 표기하고 정상 실행과 섞어 평균내지 않는다.

- [ ] **Step 4: 생성 모델과 검증 모델 분리 실험을 수행한다**

  현재 기본값은 생성과 검증이 모두 `gpt-5.6-luna`다. 동일 모델과 독립 검증 모델 조합을 같은 표본에서 비교하고, 오탐·미탐·비용·지연을 모두 개선하는 조합만 설정 후보로 선택한다.

- [ ] **Step 5: 성공 기준을 평가하고 설정값을 확정한다**

  6절의 목표를 모두 표로 판정한다. 미달 지표는 Finding 코드와 단계별 latency를 근거로 후속 작업을 분리하고, 검증을 끄거나 호출 예산을 임의로 늘려 통과시키지 않는다.

- [ ] **Step 6: 전체 회귀 검증을 실행한다**

  Run: `bash gradlew build`

  Expected: 모든 단위·통합·ArchUnit 테스트 PASS.

- [ ] **Step 7: 측정 결과와 최종 설정을 커밋한다**

  Commit: `docs : 문제 생성 신뢰성 및 성능 개선 결과 기록`

## 8. 단계별 배포 및 롤백

1. Task 1 관측성은 동작 변경 없이 먼저 배포한다.
2. Task 2~4는 독립 커밋으로 배포해 실패율과 지연 변화를 각각 확인한다.
3. Task 5 호출 예산은 환경변수로 조정 가능하게 배포한다. 예산 소진이 정상 후보를 과도하게 막으면 코드 롤백 대신 설정값을 한 단계 올리고 원인을 분석한다.
4. Task 6 수정 Delta는 수정 경로 회귀 테스트와 사용자 확인 플로우 점검 후 배포한다.
5. 각 단계에서 `ERROR` 비율 또는 p95 지연이 직전 100건 대비 20% 이상 악화되면 해당 단계만 되돌린다.

## 9. 완료 정의

- Task 1~7의 체크박스가 모두 완료됐다.
- `bash gradlew build`와 ArchUnit 테스트가 통과한다.
- 세 생성 경로와 수정 경로를 `itemId` 또는 `sessionId`로 끝까지 추적할 수 있다.
- 확인된 유니코드 수학 표기 오탐 회귀 테스트가 모두 통과한다.
- 검증 구조화 출력과 검증 전용 재시도가 생성 재호출 없이 동작한다.
- semantic 비활성 실행에서 의미 추출 호출이 0회다.
- 문항별 호출 예산과 실제 사용량이 로그에 남는다.
- 6절의 초기 운영 목표에 대한 측정 결과가 결과 문서에 기록됐다.
- 프론트엔드가 일반·종합평가 부족분 생성에 `/generate/async`를 사용하는지 연동 점검이 완료됐다.
