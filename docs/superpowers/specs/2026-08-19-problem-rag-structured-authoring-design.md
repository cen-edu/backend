# 중학교 1학년 수학 RAG·구조 기반 문제 저작 설계

## 1. 문서 목적

이 문서는 현재 `feat/problem-ai-contracts` 브랜치의 문제 생성·수정·검증·자산 처리 구조를
기반으로 다음 세 단계의 목표 아키텍처를 고정한다.

- A안: 메타데이터 필터와 Dense Vector 검색을 이용한 기본 RAG 문제 생성
- B안: 구조화 의미 모델을 정본으로 사용하는 문제 생성·자연어 수정·이미지 동기화
- C안: 충분한 운영 데이터가 축적된 이후 적용하는 Hybrid 검색과 데이터 기반 랭킹

세 단계는 독립 프로젝트가 아니라 순차적으로 누적되는 구조다. A안은 C안에 필요한 검색 로그와
교사 의사결정 이벤트를 처음부터 기록하고, B안은 생성·수정·이미지가 공유하는 의미 정본을
제공한다. C안은 A안과 B안의 계약을 바꾸지 않고 검색 후보 산출과 순위 결정 구현만 교체한다.

본 설계의 기준일은 2026-08-19이며, 대상 교육과정은 대한민국 2022 개정 교육과정의
중학교 1학년 수학이다.

---

## 2. 핵심 결정

### 2.1 신규 문제 우선, 기존 문제 지연 구조화

다음 규칙을 고정한다.

1. 신규 AI 생성 문제는 승인 가능한 후보가 되기 전에 `ProblemSemanticModelV1`을 반드시 가진다.
2. 신규 AI 수정 문제는 의미 모델을 기준으로 수정한다.
3. 기존 문제은행 문제는 일괄 전처리하지 않는다.
4. 기존 문제은행 문제는 다음 중 하나가 발생할 때만 의미 모델을 지연 생성한다.
   - 교사가 문제 수정을 시작한다.
   - 유사·응용 문제의 `ORIGIN`으로 선택된다.
   - Few-shot 예제로 사용하기 위해 풀이 구조 정보가 필요하다.
5. 기존 문제의 지연 구조화가 실패해도 원본 문제의 일반 조회·학습지 재사용은 영향을 받지 않는다.
6. 지연 구조화된 의미 모델은 자동으로 원본 문제를 덮어쓰지 않고 별도 구조화 상태로 보관한다.

이 결정으로 5,594개 기존 문제를 선행 변환하는 비용을 제거하면서 신규 생성 데이터부터
고품질 구조를 축적한다.

### 2.2 의미 모델이 정본이고 화면 스냅샷과 이미지는 파생물

신규 AI 생성·수정 문제의 정본 관계는 다음과 같다.

```text
ProblemSemanticModelV1
├── QuestionSnapshotV1       학생·교사 화면에 표시할 문제 구조
├── GeneratedAssetPlan       렌더링할 자산 계획
├── DiagramSpecV1            결정적 SVG 렌더링 입력
├── 정답·보기·풀이·해설       계산 그래프와 템플릿에서 파생
└── SemanticAssertions       검증해야 할 불변조건
```

`QuestionSnapshotV1`, SVG 파일, 대체 텍스트를 서로 독립적으로 생성하거나 수정하지 않는다.
숫자·단위·좌표·도형 조건에 영향을 주는 변경은 의미 모델을 먼저 변경하고 모든 파생물을 다시 만든다.

### 2.3 자연어 수정은 전체 JSON 재생성이 아니라 제한된 패치

교사의 자연어 요청은 `AgentDispatcher`의 `PROBLEM_EDIT` 경로를 유지한다. 에이전트는 수정된
전체 문제를 반환하지 않고 다음 중 하나를 반환한다.

- `PRESENTATIONAL_PATCH`: 의미를 바꾸지 않는 문구·표현 수정
- `PARAMETRIC_PATCH`: 반지름, 높이, 좌표, 표의 값처럼 허용된 파라미터 수정
- `STRUCTURAL_REGENERATION`: 도형 종류, 문항 유형, 풀이 전략처럼 구조를 바꾸는 재생성
- `RESTORE`: 이전 통과 버전 복원
- `REJECTED`: 교육과정 위반, 모순된 요청 또는 지원하지 않는 변경

파라미터 수정은 서버가 패치를 적용하고 계산·본문·정답·해설·이미지를 결정적으로 다시 만든다.
구조 변경은 기존 문제 전체 교체와 동일한 수준의 생성·검증·교사 확인을 거친다.

### 2.4 정답에 영향을 주는 시각 자료는 생성형 이미지 모델을 사용하지 않음

좌표 그래프, 수직선, 평면도형, 입체도형, 표는 타입이 명확한 `DiagramSpecV1`을 사용해 SVG로
렌더링한다. 이미지 생성 모델은 장식 목적의 비필수 삽화에만 사용할 수 있으며 A·B안의 필수
범위에는 포함하지 않는다.

### 2.5 C안은 데이터 준비 조건을 만족한 뒤 순위 결정에만 사용

C안은 A안 검색 계약의 구현을 교체하지만 생성·수정 API, `GenerationReference`,
`ProblemSemanticModelV1` 계약을 변경하지 않는다. 준비 조건을 충족하기 전에는 shadow mode로만
실행하고 사용자 응답에는 A안의 순위를 사용한다.

---

## 3. 현재 코드 기준선

### 3.1 재사용할 기존 구조

- `domain.problem.authoring.generation.GenerationReference`
  - `ORIGIN`, `EXAMPLE` 역할과 문제 스냅샷 전달 계약이 이미 존재한다.
- `domain.problem.authoring.generation.ProblemGenerationCommand`
  - 생성 목적, 교육과정, 참고 문제, 개념 근거를 전달한다.
- `domain.problem.service.ProblemGenerationPlanningService`
  - 문제은행 재사용과 AI 부족분 생성 슬롯을 계획한다.
- `domain.problem.service.ProblemGenerationWorker`
  - 생성, 검증, 재시도, 후보 승격을 조율한다.
- `domain.problem.authoring.model.QuestionSnapshotV1`
  - 화면 표시와 저장에 사용하는 현재 문제 스냅샷이다.
- `domain.problem.authoring.asset.GeneratedAssetPlan`
  - 자산 역할, 제작 방식, 출력 형식, 렌더링 요구를 전달한다.
- `domain.problem.service.ProblemEditPolicy`
  - 요청 대상, 의존 대상, 보호 대상을 계산한다.
- `domain.problem.service.ProblemModificationWorker`
  - 확정된 수정 계획의 실행, 검증, 재시도를 담당한다.
- `ai.problem.agent.ProblemEditAgent`
  - 교사 자연어를 받는 유일한 문제 수정 에이전트 진입점이다.
- `domain.problem.service.ProblemCandidateProcessingService`
  - 스냅샷·자산 생성과 검증 결과를 Authoring Version으로 관리한다.

### 3.2 보완해야 할 현재 한계

1. `ProblemGenerationPromptFactory`가 참고 문제 ID만 프롬프트에 넣고 실제 예제를 넣지 않는다.
2. `ai.embedding`, `infra.vector`에 문제 검색 구현이 없다.
3. `problem_question`에 검색 문서는 있으나 임베딩 저장 구조가 없다.
4. `CurriculumContext`에 교육과정 개정과 성취기준 ID가 없다.
5. `QuestionSnapshotV1.assets`에는 `assetKey`, `altText`만 남아 렌더링 의미 원본이 보존되지 않는다.
6. `LocalDraftAssetProductionAdapter`는 실제 도형이 아니라 설명 문자열만 SVG로 출력한다.
7. `ProblemModificationAdapter`는 수정 가능한 영역을 LLM이 다시 작성하고 병합하므로 하나의 수치
   변경이 본문·정답·해설·이미지에 서로 다르게 반영될 수 있다.
8. 검색 결과의 후보·점수·선택과 교사 승인·수정·거절을 연결하는 이벤트가 없다.

---

## 4. 범위와 비범위

### 4.1 포함 범위

- 2022 개정 교육과정 중학교 1학년 수학 문제
- `MULTIPLE_CHOICE`, `SHORT_INPUT`, `STEP_FILL`, `ESSAY`
- 일반학습, 종합평가, 맞춤 유사문제, 맞춤 응용문제
- 승인된 문제와 검증 가능한 기존 문제를 사용하는 RAG
- 신규 AI 문제의 의미 모델 생성
- 기존 문제의 지연 구조화
- 숫자·단위·좌표·도형 속성의 자연어 수정
- 문제 본문·정답·해설·루브릭·이미지 동기화
- 수직선, 좌표 그래프, 평면도형, 기본 입체도형, 표의 SVG 렌더링
- 검색·생성·수정·승인 과정의 품질 로그
- 데이터가 쌓인 이후 Hybrid 검색과 reranking

### 4.2 명시적 비범위

- 기존 문제 5,594개의 일괄 의미 모델 변환
- 학생 답안 이미지 생성·수정
- 손글씨 렌더링
- 생성형 이미지 모델로 정답 필수 그래프·도형 제작
- C안에서 자체 CrossEncoder 모델 학습
- 자유 형식의 임의 SVG나 JavaScript를 LLM 출력으로 실행
- 중학교 2·3학년 또는 고등학교 개념 사용
- 문제 생성·수정 Agent끼리 직접 호출하는 다중 에이전트 대화
- 교사 확인 없이 구조 변경 후보를 자동 승인

---

## 5. 공통 도메인 규칙

### 5.1 교육과정 범위

모든 검색·생성·구조화·수정 명령은 다음 메타데이터를 가진다.

```java
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

고정값은 다음과 같다.

- `curriculumRevision = "2022_REVISED"`
- `schoolLevel = "MIDDLE"`
- `grade = 1`

`achievementStandardId`는 교육과정 데이터에 존재하면 필수다. 기존 seed에 성취기준 ID가 없는
소단원은 `subUnitId`를 임시 정본으로 사용하되, 검색과 생성 로그에 성취기준 누락을 기록한다.

### 5.2 유사문제와 응용문제 정의

`SIMILAR`은 다음 조건을 만족해야 한다.

- 같은 교육과정 범위 또는 같은 성취기준
- 같은 핵심 풀이 전략
- 같은 수준의 추론 단계 수
- 숫자, 문장 표현, 상황 또는 배치를 변경
- 원본과 문장·수치가 사실상 동일한 복제는 금지

`APPLICATION`은 다음 조건을 만족해야 한다.

- 같은 성취기준
- 핵심 개념과 최종 학습목표 유지
- 표현 방식, 생활 맥락 또는 시각 자료 형식을 변경
- 중1 범위 안에서 최대 한 단계의 추가 추론 허용
- 중2 이상 선행 개념 추가 금지

### 5.3 승인 문제만 기본 검색 corpus에 포함

기본 검색 대상은 다음과 같다.

- 검증을 통과한 문제은행 문제
- 교사가 승인·최종화한 AI 생성 문제
- 교사가 승인·최종화한 AI 수정 문제

다음은 검색 대상에서 제외한다.

- 생성 실패 후보
- 검증 실패 후보
- 교사 확인 전 후보
- 삭제된 문제
- 자산 생성 또는 업로드가 최종 실패한 이미지 필수 문제

### 5.4 패키지 경계

- Problem 도메인은 검색·인덱싱 Port와 데이터 계약을 소유한다.
- `infra.vector`는 pgvector와 PostgreSQL FTS 구현을 소유한다.
- `ai.embedding`은 임베딩 Provider 호출 구현을 소유한다.
- 사용자 자연어 수정은 반드시 `AgentDispatcher`를 통과한다.
- 시스템 문제 생성, 구조화, 검증은 Dispatcher를 통과하지 않고 Port를 사용한다.
- Problem 도메인은 `infra.vector` Repository를 직접 호출하지 않는다.
- `infra.vector` 구현은 Problem 도메인이 공개한 Port를 구현한다.

---

## 6. A안: 기본 RAG 문제 생성

### 6.1 목표

일반학습·종합평가·맞춤 문제 생성에서 교육과정에 맞는 문제를 검색하고, 중복되지 않는 소수의
참고 문제를 실제 Few-shot 예제로 제공한다. 운영 데이터가 없어도 동작해야 한다.

### 6.2 처리 흐름

```text
HTTP 생성 요청
→ ProblemGenerationRequirement 작성
→ ProblemGenerationPlanningService
→ 문제은행 정확 조건 재사용 조회
→ 부족 슬롯에 ProblemReferenceRetrievalPort 호출
→ 메타데이터 선필터
→ Dense cosine HNSW 검색
→ 동일·중복 문제 제거
→ MMR로 ORIGIN/EXAMPLE 선택
→ ProblemGenerationCommand.references에 스냅샷 주입
→ ProblemGenerationPromptFactory가 실제 Few-shot 직렬화
→ ProblemGenerationPort.generate
→ 구조·정답·교육과정·자산 검증
→ 교사 HITL
→ 최종화
→ SearchIndexingPort에 PENDING 인덱싱 요청
```

### 6.3 검색 입력 계약

```java
public record ProblemReferenceQuery(
        UUID requestId,
        GenerationPurpose purpose,
        CurriculumScope curriculum,
        QuestionType questionType,
        String difficulty,
        Long originQuestionId,
        QuestionSnapshotV1 originSnapshot,
        int candidateLimit,
        int selectionLimit,
        Set<Long> excludedQuestionIds
) {}
```

규칙은 다음과 같다.

- 일반·종합평가 부족분은 `originQuestionId = null`이다.
- 유사·응용 문제는 `ORIGIN` 하나를 반드시 가진다.
- `candidateLimit` 기본값은 40이다.
- `selectionLimit` 기본값은 일반·종합 3, 유사·응용 4다.
- 한 생성 Job 안에서 이미 사용한 문제 ID는 `excludedQuestionIds`에 포함한다.
- API 요청값으로 limit를 직접 받지 않고 서버 정책으로 설정한다.

### 6.4 검색 출력 계약

```java
public record RetrievedProblemReference(
        Long questionId,
        QuestionSnapshotV1 snapshot,
        double denseScore,
        int denseRank,
        String documentHash,
        String duplicateClusterKey,
        Set<String> matchedConceptKeys
) {}
```

도메인 서비스는 검색 결과를 기존 `GenerationReference`로 변환한다. 유사·응용 문제의 기준
문제는 `ORIGIN`, 검색으로 선택한 참고 문제는 `EXAMPLE`로 지정한다.

### 6.5 검색 문서

임베딩 입력은 화면 발문만 사용하지 않고 다음 순서의 안정된 문자열로 만든다.

```text
[교육과정] 중학교 1학년 > 대단원 > 중단원 > 소단원
[성취기준] achievementStandardId
[유형] questionType
[난이도] difficulty
[발문] prompt_text
[풀이전략] solutionStrategy
[풀이요약] 정답을 직접 노출하지 않는 짧은 풀이 구조 요약
[표현] text-only | figure | table
```

정답 원문을 임베딩 문서에 직접 포함하지 않는다. 풀이 전략과 계산 구조는 포함하되 학생에게
노출되는 검색 로그에는 남기지 않는다.

### 6.6 임베딩과 인덱스

- 임베딩 모델 기본값: `text-embedding-3-small`
- 차원: 1024
- 거리: cosine distance
- 인덱스: HNSW
- 검색 대상 기본 필터:
  - `curriculum_revision = 2022_REVISED`
  - `school_level = MIDDLE`
  - `grade = 1`
  - 동일 `achievement_standard_id` 또는 동일 `sub_unit_id`
  - 동일 `question_type` 우선
  - 난이도 차이 최대 1, 요청 난이도 정확 일치 우선
  - `index_status = READY`
  - `deleted = false`

일반·종합평가는 동일 문제 유형을 강제한다. 응용문제는 정책이 명시적으로 허용할 때만 문제
유형을 넓힌다.

### 6.7 MMR 선택

운영 데이터가 없으므로 학습된 threshold를 사용하지 않는다. 후보 순위는 다음 기본식으로
선택한다.

```text
MMR(candidate) = λ × relevance(candidate, query)
               - (1 - λ) × maxSimilarity(candidate, alreadySelected)
```

- 일반·종합·유사 기본 `λ = 0.70`
- 응용 기본 `λ = 0.55`
- 동일 `duplicateClusterKey`에서는 하나만 선택
- 같은 원천 `source_ref` 계열에서는 하나만 선택
- threshold 미달이라는 이유로 생성을 중단하지 않는다.
- 필터 후 후보가 없으면 references 없이 기존 생성 경로를 사용하고 fallback 로그를 남긴다.

이 값들은 구성값으로 노출하되 API에서 변경하지 않는다.

### 6.8 Few-shot 직렬화

프롬프트에는 ID 목록이 아니라 다음 데이터를 넣는다.

- 역할: ORIGIN 또는 EXAMPLE
- 교육과정 범위
- 문제 유형과 난이도
- 실제 발문
- 보기 또는 풀이 단계 구조
- 정답을 직접 복사하지 않도록 축약한 풀이 전략
- 시각 자료가 있으면 `DiagramSpec`의 의미 요약
- 금지 지시: 숫자·문장·보기의 직접 복제 금지

Few-shot은 JSON으로 직렬화하고 프롬프트의 정적 지시문 뒤, 현재 요청 앞에 둔다. 정적 prefix와
Structured Output Schema는 변동시키지 않아 prompt caching을 방해하지 않는다.

### 6.9 인덱싱 생명주기

최종화 transaction에서 외부 임베딩 호출을 하지 않는다. 최종화된 문제에 대해 인덱스 행을
`PENDING`으로 생성하고 별도 Worker가 다음을 수행한다.

```text
PENDING
→ 검색 문서 작성
→ documentHash 계산
→ 기존 READY hash와 같으면 SKIPPED
→ 임베딩 호출
→ 차원 검증
→ READY
```

기술 실패는 `RETRY_WAIT`, 영구 데이터 실패는 `FAILED`로 기록한다. 인덱싱 실패는 문제 최종화와
학습지 사용을 막지 않는다.

### 6.10 C안을 위한 A단계 데이터 수집

A안부터 다음을 기록한다.

- 검색 요청 ID와 생성 Job/Item ID
- 검색 정책 버전
- 후보 questionId
- dense score와 rank
- MMR 선택 여부와 최종 순서
- fallback 여부
- 생성된 Authoring Version ID
- 교사의 최종 행동: 승인, 수정 시작, 복원, 교체, 폐기
- 수정이 있었다면 변경 성격과 대상 유형

교사의 자연어 원문과 문제 정답 원문은 검색 로그에 저장하지 않는다.

---

## 7. B안: 구조 기반 생성·수정·이미지 동기화

### 7.1 목표

문제 본문, 정답, 풀이, 해설, 루브릭, 도형·그래프가 하나의 의미 모델을 공유하게 하여 숫자나
도형 조건이 수정될 때 모든 결과를 일관되게 재생성한다. 운영 학습 데이터 없이 규칙·타입·검증으로
품질을 개선한다.

### 7.2 의미 모델 최상위 계약

```java
public record ProblemSemanticModelV1(
        int schemaVersion,
        CurriculumScope curriculum,
        SemanticProblemIntent intent,
        List<SemanticParameter> parameters,
        List<SemanticComputation> computations,
        List<SemanticConstraint> constraints,
        SemanticPresentationPlan presentation,
        List<DiagramSpecV1> diagrams,
        List<SemanticAssertion> assertions
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
```

모든 목록은 `null` 대신 빈 목록을 사용한다. 논리 키는 영문 대문자·숫자·밑줄로 제한하고 서버가
중복을 검증한다.

### 7.3 문제 의도

```java
public record SemanticProblemIntent(
        QuestionType questionType,
        String difficulty,
        EvaluationArea evaluationArea,
        String solutionStrategy,
        String targetKey,
        int expectedReasoningSteps,
        boolean visualRequired
) {}
```

- `solutionStrategy`는 서버가 관리하는 제한된 코드다.
- `targetKey`는 최종 정답을 만드는 parameter 또는 computation key다.
- `expectedReasoningSteps`는 중1 난이도 검증 보조값이다.
- `visualRequired=true`이면 최소 하나의 Diagram이 존재해야 한다.

### 7.4 파라미터와 계산

```java
public record SemanticParameter(
        String key,
        SemanticValueType valueType,
        String value,
        String unit,
        boolean editable,
        SemanticNumericBounds bounds
) {}

public enum SemanticValueType {
    INTEGER, DECIMAL, RATIONAL, TEXT, POINT, BOOLEAN
}

public record SemanticComputation(
        String key,
        SemanticOperation operation,
        List<String> operands,
        String literal,
        String unit,
        String result
) {}
```

초기 `SemanticOperation`은 다음으로 제한한다.

- `IDENTITY`
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`
- `NEGATE`
- `ABS`
- `POWER_INTEGER`
- `SUM`
- `PRODUCT`
- `LINEAR_EVALUATE`
- `DIRECT_PROPORTION`
- `INVERSE_PROPORTION`

지원하지 않는 계산은 자유 수식 문자열로 실행하지 않는다. 해당 문제는 기존 스냅샷 생성 경로를
사용하거나 구조 변경 후보로 분류한다. 계산 그래프는 순환을 허용하지 않는다.

### 7.5 프레젠테이션 계획

```java
public record SemanticPresentationPlan(
        String questionTemplate,
        List<SemanticChoiceTemplate> choices,
        List<SemanticStepTemplate> steps,
        String explanationTemplate,
        SemanticLearningGuideTemplate learningGuide,
        List<SemanticRubricTemplate> rubrics
) {}
```

템플릿은 `${KEY}` placeholder로 parameter·computation을 참조한다. 예:

```text
반지름이 ${RADIUS}${RADIUS_UNIT}인 원의 지름을 구하여라.
원의 지름은 반지름의 2배이므로 ${RADIUS} × 2 = ${DIAMETER}${DIAMETER_UNIT}이다.
```

서버 Materializer가 placeholder를 치환한다. 존재하지 않는 키, 사용되지 않은 필수 파라미터,
서로 다른 단위의 잘못된 연산, 치환 후 남은 placeholder는 후보 실패다.

파라미터 수정에서는 템플릿을 LLM으로 다시 쓰지 않는다. 표현 수정에서만 템플릿 변경을 허용하며,
변경 전후 placeholder 집합이 동일해야 한다.

### 7.6 DiagramSpecV1

`DiagramSpecV1`은 `assetKey`, `kind`, viewport, style과 종류별 payload를 가진 tagged union이다.
지원 종류는 다음과 같다.

1. `NUMBER_LINE`
   - 최소·최대, 눈금 간격, 표시 점, 구간, 화살표
2. `COORDINATE_GRAPH`
   - x/y 범위, 눈금, 점, 선분, 직선, 비례·반비례 그래프, 라벨
3. `PLANE_GEOMETRY`
   - 점, 선분, 직선, 반직선, 각, 다각형, 원, 호, 길이·각도 표시
4. `SOLID_GEOMETRY`
   - 직육면체, 각기둥, 각뿔, 원기둥, 원뿔, 구의 파라미터형 도식
5. `DATA_TABLE`
   - 행·열 헤더, 셀 값, 강조 셀

임의 SVG 문자열, HTML, CSS, JavaScript, 외부 URL은 DiagramSpec에 포함할 수 없다. 라벨 텍스트는
길이와 허용 문자를 검증하고 SVG sanitizer를 마지막 방어선으로 유지한다.

### 7.7 결정적 렌더링

```java
public interface ProblemDiagramRendererPort {
    RenderedDiagram render(DiagramSpecV1 spec, DiagramRenderContext context);
}
```

렌더러 규칙은 다음과 같다.

- 같은 정규화된 DiagramSpec과 rendererVersion은 같은 SVG hash를 생성한다.
- SVG `viewBox`를 필수로 사용한다.
- 문제 계산에 쓰는 값과 라벨에 표시하는 값은 같은 semantic key를 참조한다.
- 좌표는 서버가 계산하고 LLM이 픽셀 좌표를 임의로 만들지 않는다.
- 겹침 방지 기본 규칙을 적용하고 해결할 수 없는 겹침은 렌더 실패로 반환한다.
- 렌더러는 네트워크를 호출하지 않는다.
- SVG에는 script, foreignObject, event handler, 외부 참조를 허용하지 않는다.

### 7.8 Materializer

```java
public interface ProblemSemanticMaterializer {
    MaterializedProblem materialize(ProblemSemanticModelV1 model);
}

public record MaterializedProblem(
        QuestionSnapshotV1 snapshot,
        List<GeneratedAssetPlan> assetPlans,
        SemanticMaterializationReport report
) {}
```

처리 순서는 고정한다.

1. 의미 모델 구조 검증
2. 교육과정·문항 유형 규칙 검증
3. 계산 그래프 위상 정렬
4. 계산 결과 재산출
5. 저장된 `result`와 재산출 값 비교
6. assertion 평가
7. 템플릿 치환
8. QuestionSnapshot 생성
9. DiagramSpec에서 GeneratedAssetPlan 생성
10. Snapshot 구조·정규화 검증

LLM이 제공한 계산 `result`는 신뢰하지 않고 서버 재계산 값으로 덮어쓴다.

### 7.9 구조화 생성

신규 AI 생성은 두 단계 Structured Output을 사용한다.

```text
1단계: ProblemSemanticModelV1 생성
2단계: 서버 Materializer로 Snapshot과 Diagram 생성
```

1단계 출력이 의미 검증에 실패하면 검증 오류를 축약해 최대 두 번 재생성한다. Snapshot 전체를
다시 생성하는 현재 경로는 의미 모델을 지원할 수 없는 기존 문제 fallback에만 사용한다.

### 7.10 지연 구조화

기존 문제를 수정하거나 `ORIGIN`으로 사용할 때 `ProblemSemanticExtractionPort`가 기존 Snapshot을
의미 모델로 변환한다.

```java
public interface ProblemSemanticExtractionPort {
    SemanticExtractionResult extract(SemanticExtractionCommand command);
}
```

추출 결과는 다음 상태 중 하나다.

- `EXTRACTED`: 의미 모델 검증 통과
- `UNSUPPORTED`: 현재 계산·Diagram 종류로 표현할 수 없음
- `INVALID_SOURCE`: 기존 문제 자체의 구조·정답 불일치
- `TECHNICAL_ERROR`: Provider 또는 파싱 실패

`UNSUPPORTED`이면 수정 UI에서 지원되지 않는 구조 변경임을 알리고 기존 전체 교체 흐름을 제안한다.
`INVALID_SOURCE`이면 원본을 변경하지 않고 교사에게 문제 검토가 필요함을 알린다.

### 7.11 자연어 수정 패치

에이전트가 반환할 의미 패치 계약은 다음과 같다.

```java
public record ProblemSemanticPatch(
        int schemaVersion,
        UUID requestId,
        Long baseVersionId,
        SemanticEditMode mode,
        List<SemanticPatchOperation> operations,
        String assistantMessage
) {}
```

허용 operation은 다음과 같다.

- `SET_PARAMETER_VALUE`
- `SET_PARAMETER_UNIT`
- `SET_TEMPLATE_TEXT`
- `SET_DIAGRAM_STYLE`
- `SET_LABEL_TEXT`

각 operation은 `path`, `expectedOldValue`, `newValue`를 가진다. 서버는 `expectedOldValue`가 현재
모델과 다르면 stale patch로 거절한다.

다음 변경은 patch operation으로 처리하지 않는다.

- 문항 유형 변경
- 풀이 전략 변경
- 계산 그래프 operation 변경
- 도형 종류 변경
- parameter·computation 추가 또는 제거
- 성취기준 변경

이 변경은 `STRUCTURAL_REGENERATION`으로 분류한다.

### 7.12 수정 의존성

수정 모드별 재생성 범위는 다음과 같다.

| 수정 모드 | 의미 모델 | 본문 | 정답/보기 | 해설 | 이미지 | 검증 |
|---|---:|---:|---:|---:|---:|---:|
| 표현 수정 | 템플릿만 | 재생성 | 유지 | 필요 시 재생성 | 유지 | placeholder·의미 불변 |
| 파라미터 수정 | 값 변경 | 재물질화 | 재계산 | 재물질화 | 재렌더 | 전체 |
| 그림 스타일 | style만 | 유지 | 유지 | 유지 | 재렌더 | 자산 |
| 구조 변경 | 새 모델 | 재생성 | 재생성 | 재생성 | 재생성 | 전체 + 교사 확인 |
| 복원 | 이전 모델 | 이전 값 | 이전 값 | 이전 값 | 이전 자산 | 저장된 PASSED 사용 |

현재 `ProblemEditPolicy`의 대상 계산은 유지하되 의미 모델 patch mode를 먼저 계산하고,
`ProblemModificationSnapshotMerger`는 의미 모델이 없는 fallback 문제에만 사용한다.

### 7.13 수정 확인 응답

교사 확인 화면에 정답 원문을 노출하지 않으면서 다음 diff를 제공한다.

- 변경된 파라미터 이름과 이전/새 값
- 변경된 단위
- 영향을 받는 본문·보기·해설·이미지 영역
- 구조 변경 여부
- 재검증 필요 여부
- 미리보기 Version ID

정답·풀이의 내부 값은 교사가 문제 편집 화면에서 볼 권한이 있을 때 기존 Snapshot API를 통해
확인하고, Agent의 `assistantMessage`에는 직접 포함하지 않는다.

### 7.14 저장 구조

Authoring Version에는 다음을 추가한다.

- `semantic_model_schema_version smallint null`
- `semantic_model jsonb null`
- `semantic_model_hash varchar(64) null`

최종 `problem_question`에는 다음을 추가한다.

- `semantic_model_schema_version smallint null`
- `semantic_model jsonb null`
- `semantic_model_hash varchar(64) null`
- `semantic_model_status varchar(20) not null default 'ABSENT'`

기존 문제는 `ABSENT`, 검증된 신규 문제는 `READY`, 지연 변환 실패는 `UNSUPPORTED` 또는
`FAILED`다.

최종 `problem_asset`에는 재현 가능한 자산을 위해 다음을 추가한다.

- `render_spec_schema_version smallint null`
- `render_spec jsonb null`
- `render_spec_hash varchar(64) null`
- `renderer_version varchar(30) null`

적용된 Flyway 파일은 수정하지 않고 새로운 타임스탬프 마이그레이션을 추가한다.

### 7.15 검증

B안의 검증은 다음 순서다.

1. Java 구조 Validator
2. 계산 그래프 Validator
3. 단위·범위 Validator
4. 교육과정 Validator
5. Template placeholder Validator
6. Diagram semantic Validator
7. 결정적 Materializer
8. SVG sanitizer와 render readiness 검사
9. 기존 독립 LLM Verification Adapter
10. 교사 HITL

결정적 검증 실패를 LLM 검증으로 덮어쓸 수 없다. Java Validator 실패 후보는 즉시 실패 또는
재생성 대상이다.

---

## 8. C안: Hybrid 검색과 데이터 기반 랭킹

### 8.1 목표

A안의 Dense 검색이 놓치는 수학 용어·수식·풀이 구조를 lexical 검색과 reranker로 보완하고,
교사 승인·수정·폐기 데이터로 threshold와 정책을 보정한다.

### 8.2 활성화 준비 조건

C안의 사용자 노출 순위 활성화는 다음을 모두 만족한 뒤 진행한다.

1. 최소 500건의 검색 trace가 존재한다.
2. 최소 200건의 생성 후보에 승인·수정·폐기 결과가 연결되어 있다.
3. 네 교육과정 대영역 각각 최소 30건의 결과가 있다.
4. 최소 200쌍의 문제 관련성 라벨이 있다.
5. 관련성 라벨은 `DUPLICATE`, `SIMILAR`, `APPLICATION_RELEVANT`, `RELATED`, `IRRELEVANT`,
   `OUT_OF_CURRICULUM` 중 하나다.
6. A안 대조군의 Recall@k, nDCG@10, 교사 무수정 승인율, p95 latency가 재현 가능하게 측정된다.

준비 조건 전에도 C 코드는 shadow mode로 실행할 수 있지만 사용자 결과와 생성 Few-shot에는
A안 결과만 사용한다.

### 8.3 검색 채널

C안은 세 채널을 병렬 실행한다.

1. Dense channel
   - A안의 1024차원 cosine HNSW
2. Lexical channel
   - PostgreSQL `tsvector`, GIN, `ts_rank_cd`
   - 단원명, 개념명, 수학 용어, 정규화된 발문
3. Structure channel
   - `solutionStrategy`
   - computation operation sequence
   - Diagram kind
   - 정규화된 수식 fingerprint

메타데이터 hard filter는 세 채널 전에 공통 적용한다.

### 8.4 RRF 병합

각 채널의 원점수는 직접 합산하지 않는다. 순위를 Reciprocal Rank Fusion으로 합친다.

```text
RRF(question) = Σ 1 / (k + rank_channel(question))
```

- 기본 `k = 60`
- 채널별 최대 후보 50
- 병합 후보 최대 60
- 채널에 없는 문제는 해당 채널 기여도 0
- RRF score와 채널별 rank를 trace에 모두 저장

### 8.5 CrossEncoder reranking

RRF 상위 후보만 reranker에 전달한다.

- rerank 입력 최대 20개
- 최종 MMR 입력 최대 10개
- 최종 Few-shot 3~4개
- 입력에는 교육과정, 발문, 풀이 전략, 시각 유형을 포함
- 정답 원문은 제외
- reranker timeout 시 RRF 순위로 fallback
- reranker 실패는 생성 요청 전체 실패로 처리하지 않음

초기에는 사전학습된 다국어 CrossEncoder를 사용하고 자체 fine-tuning은 범위에서 제외한다.
후보 모델은 별도 offline bake-off로 한국어 중1 수학 관련성 라벨에서 선택한다.

### 8.6 데이터 기반 threshold

threshold는 목적별로 분리한다.

- duplicate 차단 threshold
- similar 후보 포함 threshold
- application 관련 후보 포함 threshold
- out-of-curriculum 거절 threshold

각 threshold는 고정 cosine 값이 아니라 라벨 데이터에서 precision/recall trade-off를 측정해
선택한다. 운영 적용 시 threshold 정책 버전을 저장하고 이전 버전으로 즉시 rollback할 수 있어야
한다.

### 8.7 교사 이벤트 활용

교사 행동은 직접 정답 라벨로 간주하지 않고 약한 신호로 사용한다.

- 무수정 승인: 선택 예제와 생성 결과의 긍정 신호
- 경미한 표현 수정 후 승인: 의미 적합, 표현 품질 보통
- 파라미터 수정 후 승인: 개념 적합, 난이도·수치 품질 보통
- 전체 교체: 강한 부정 신호
- 폐기: 부정 신호
- 복원: 직전 수정 결과의 부정 신호

동일 교사의 반복 행동이 데이터 전체를 지배하지 않도록 교사·Job 단위 중복을 제한한다.

### 8.8 offline 평가

최소 평가 지표는 다음과 같다.

- Retrieval: Recall@5, Recall@10, nDCG@10, MRR@10
- Duplicate: precision, recall, false block rate
- Curriculum: achievement standard hit rate, out-of-curriculum rate
- Generation: 정답 정확성, 교사 무수정 승인율, 평균 수정 횟수
- Diversity: 선택 Few-shot 간 평균 유사도, duplicate cluster 수
- Performance: 검색 p50/p95, rerank p50/p95, 전체 생성 p95
- Cost: 검색·rerank·LLM 문항당 비용

평가는 일반·종합·유사·응용 목적, 네 대영역, 문제 유형, 난이도로 층화한다.

### 8.9 shadow와 rollout

1. A 결과만 사용자에게 제공하면서 C 결과를 shadow 저장
2. A와 C의 후보 차이와 offline 지표 비교
3. 교사 내부 테스트에서 C 순위 미리보기
4. 설정 기반 소규모 canary
5. 지표 악화가 없으면 기본값 C로 전환
6. 장애·품질 악화 시 즉시 A로 rollback

기능 플래그는 검색 구현 선택에만 사용하고 API 응답 계약은 바꾸지 않는다.

---

## 9. 공통 데이터 흐름

### 9.1 신규 생성

```text
요청 검증
→ CurriculumScope 확정
→ A/C Retriever로 참고 문제 선택
→ Semantic Model 생성
→ Java 계산·교육과정 검증
→ Materializer
→ Snapshot + DiagramSpec
→ SVG 렌더링
→ 내용·자산 독립 검증
→ 교사 미리보기
→ 승인
→ 문제은행·자산 최종화
→ 검색 인덱싱 PENDING
→ 교사 결과 이벤트 기록
```

### 9.2 기존 문제 수정

```text
교사 수정 시작
→ semantic_model READY 여부 확인
├─ READY: 그대로 사용
└─ ABSENT: 지연 구조화
→ 자연어 수정 Agent
→ patch mode 분류
→ 교사 확인
→ patch 적용 또는 구조 재생성
→ Materializer
→ Snapshot + SVG 재생성
→ 검증
→ 새 Authoring Version
→ 교사 승인/복원
```

### 9.3 표현 수정

```text
문구 수정 요청
→ PRESENTATIONAL_PATCH
→ placeholder 집합 불변 확인
→ 템플릿 교체
→ Materializer
→ 의미값·정답·Diagram hash 불변 확인
→ 검증·미리보기
```

### 9.4 파라미터 수정

```text
"반지름을 3cm에서 5cm로"
→ SET_PARAMETER_VALUE(RADIUS, expected=3, new=5)
→ bounds·단위 검증
→ 계산 그래프 재계산
→ 본문·보기·정답·해설 재물질화
→ DiagramSpec binding 재해결
→ SVG 재렌더
→ 전체 검증·미리보기
```

---

## 10. 오류 처리와 fallback

### 10.1 검색 오류

- Vector 검색 timeout: references 없는 생성으로 fallback
- 일부 후보 snapshot 복원 실패: 해당 후보 제외
- 모든 후보 제외: 검색 fallback 로그 후 생성 계속
- 임베딩 Provider 실패: 인덱싱 `RETRY_WAIT`, 문제 사용은 계속

### 10.2 의미 모델 오류

- 순환 계산 그래프: 후보 실패
- 존재하지 않는 operand: 후보 실패
- 지원하지 않는 operation: `UNSUPPORTED`
- 범위 밖 파라미터: patch 거절
- stale expected value: 최신 Version 기준 재확인 요청
- placeholder 누락·잔존: 후보 실패
- 정답과 target computation 불일치: 후보 실패

### 10.3 자산 오류

- Diagram semantic validation 실패: 자산 생성 전 후보 실패
- SVG render 실패: 후보의 asset 검증 ERROR
- sanitizer 제거로 의미 요소 누락: asset 검증 FAILED
- 필수 그림 없는 Snapshot: 구조 검증 실패
- 자산 실패 문제는 교사 최종화 차단

### 10.4 수정 fallback

- 의미 모델 없는 기존 문제: 지연 구조화 시도
- 지연 구조화 UNSUPPORTED: 기존 Snapshot 전체 교체 제안
- 표현 수정인데 placeholder 불변 실패: 수정 실패 후 재시도
- 구조 변경: parametric patch로 강행하지 않고 전체 후보 재생성

---

## 11. 보안과 안전

- 교사 자연어 수정은 `AgentDispatcher`와 Input/Output Guard를 통과한다.
- 시스템 문제 생성·구조화는 도메인 Port를 사용한다.
- 사용자 원문, 문제 정답, 시스템 프롬프트를 로그에 기록하지 않는다.
- DiagramSpec은 자유 SVG·script·외부 URL을 허용하지 않는다.
- 렌더 SVG는 `SafeSvgSanitizer`를 통과한다.
- teacherId는 소유권 검증에만 사용하고 검색 점수에 직접 반영하지 않는다.
- 삭제된 문제와 검증 실패 문제는 검색에서 제외한다.
- LLM이 반환한 DB ID, storage key, schema version을 신뢰하지 않고 서버가 부여한다.

---

## 12. 관측성과 정책 버전

각 생성·검색·수정 로그는 다음 식별자를 연결한다.

- HTTP traceId
- clientRequestId
- generation jobId/itemId
- authoring sessionId/versionId
- retrievalRequestId
- semantic requestId
- verificationRequestId

다음 버전을 결과 provenance에 기록한다.

- promptVersion
- structuredSchemaVersion
- semanticModelSchemaVersion
- diagramSpecSchemaVersion
- rendererVersion
- retrievalPolicyVersion
- embeddingModel과 dimensions
- rerankerModel과 version(C안)
- thresholdPolicyVersion(C안)

로그에는 원문 대신 길이, ID, hash, 상태, latency, token usage를 기록한다.

---

## 13. 테스트 전략

### 13.1 단위 테스트

- 교육과정 hard filter
- 검색 문서 정규화와 hash 멱등성
- MMR 선택과 duplicate 제거
- Few-shot 직렬화에서 정답 원문 제외
- Semantic Model 구조 Validator
- 계산 그래프 위상 정렬과 순환 탐지
- 각 SemanticOperation 계산
- 단위·bounds 검증
- placeholder 치환과 집합 불변
- 각 DiagramSpec Validator
- SVG renderer 결정성
- patch optimistic concurrency
- 수정 mode 분류
- Materializer의 Snapshot 생성
- C안 RRF와 fallback

### 13.2 통합 테스트

- PostgreSQL pgvector HNSW 검색
- curriculum metadata prefilter
- 인덱싱 Worker 상태 전이
- 신규 생성 → 검증 → 승인 → 인덱싱
- 기존 문제 → 지연 구조화 → 수정 → 승인
- parameter patch → 정답·해설·SVG 동시 변경
- 표현 patch → 의미 hash와 Diagram hash 불변
- 자산 실패 → 최종화 차단
- reranker timeout → RRF fallback

### 13.3 실제 API 시나리오

최소 다음 중1 시나리오를 Swagger 또는 실제 HTTP 호출로 검증한다.

1. 음수·양수 수직선 문제 생성 후 표시 점 이동
2. 좌표평면 점의 좌표 변경 후 본문·정답·그래프 동기화
3. 정비례 그래프의 비례상수 변경
4. 반비례 그래프의 상수 변경
5. 삼각형 변 길이 변경 후 라벨과 풀이 동기화
6. 원의 반지름 변경 후 지름·둘레 관련 값 동기화
7. 직육면체 가로·세로·높이 변경
8. 표의 데이터 한 값 변경 후 정답·설명 동기화
9. 서술형 문제 기준 변경 후 rubric 합계 100 유지
10. 문항 유형 변경 요청을 parametric patch가 아닌 구조 재생성으로 처리

### 13.4 회귀 테스트

- 기존 일반학습·종합평가 생성 API 응답 계약 유지
- 기존 TEXT_ONLY 생성·수정 유지
- 기존 문제은행 재사용 경로 유지
- Worksheet 연결 유지
- 문제 Asset URL과 S3 저장 흐름 유지
- `AiClientAccessTest`와 패키지 경계 통과

---

## 14. 단계별 완료 조건

### 14.1 A안 완료

- 메타데이터 필터 + Dense HNSW 검색이 실제 PostgreSQL에서 동작한다.
- 생성 명령에 실제 `GenerationReference` 스냅샷이 0~4개 주입된다.
- 프롬프트가 ID가 아닌 실제 Few-shot 내용을 포함한다.
- 검색 실패 시 기존 생성 경로로 안전하게 fallback한다.
- 승인된 신규 문제가 비동기로 인덱싱된다.
- 검색 후보·선택과 교사 결과 이벤트가 연결된다.
- 일반·종합·유사·응용 생성 회귀 테스트가 통과한다.

### 14.2 B안 완료

- 신규 AI 생성 문제에 검증된 `ProblemSemanticModelV1`이 저장된다.
- 기존 문제는 수정 요청 시에만 지연 구조화된다.
- 숫자 수정이 본문·정답·해설·SVG에 동일하게 반영된다.
- 표현 수정은 의미 hash와 Diagram hash를 바꾸지 않는다.
- 구조 변경은 전체 재생성과 교사 확인을 요구한다.
- 수직선·좌표 그래프·평면도형·기본 입체도형·표를 SVG로 렌더링한다.
- 같은 DiagramSpec과 rendererVersion이 같은 hash를 만든다.
- 자산과 내용 검증을 모두 통과해야 현재 Version으로 승격된다.

### 14.3 C안 완료

- Dense·lexical·structure 채널이 병렬로 검색된다.
- RRF와 CrossEncoder reranking이 timeout fallback과 함께 동작한다.
- A안과 C안 결과를 shadow mode로 동시에 기록한다.
- 목적별 threshold 정책이 버전 관리된다.
- 준비 조건과 offline 지표를 충족한 경우에만 canary 활성화된다.
- 기능 플래그로 A안에 즉시 rollback할 수 있다.

---

## 15. 구현 순서와 의존성

```text
A1 교육과정·검색 계약
→ A2 검색 인덱스·임베딩
→ A3 Retriever·MMR·Few-shot
→ A4 비동기 인덱싱·trace·교사 결과 이벤트
→ A5 실제 API 검증

B1 의미 모델 계약·Validator
→ B2 계산 엔진·Materializer
→ B3 DiagramSpec·SVG Renderer
→ B4 신규 구조화 생성
→ B5 기존 문제 지연 구조화
→ B6 자연어 patch 수정
→ B7 저장·최종화·실제 API 검증

C1 준비 데이터 감사
→ C2 FTS·structure channel
→ C3 RRF
→ C4 CrossEncoder adapter
→ C5 shadow·offline 평가
→ C6 threshold 정책·canary
```

A와 B는 일부 병렬 진행이 가능하지만 다음 인터페이스를 먼저 고정한다.

- `CurriculumScope`
- `ProblemReferenceRetrievalPort`
- `ProblemSemanticModelV1`
- `DiagramSpecV1`
- `ProblemSemanticMaterializer`
- `ProblemSemanticPatch`

C는 A의 trace와 결과 이벤트가 배포된 뒤 구현할 수 있으며 사용자 노출은 준비 조건 이후다.

---

## 16. 배포와 호환성

- DB 컬럼은 nullable 또는 안전한 기본값으로 추가한다.
- 기존 Snapshot Schema V1 API 응답은 유지한다.
- semantic model은 내부 저작 계약이며 프론트가 직접 조작하지 않는다.
- 수정 미리보기 API에는 영향 범위와 Version ID만 추가하고 기존 필드를 제거하지 않는다.
- 기능 플래그 기본값은 A 검색 off, B semantic authoring off, C ranking off로 시작한다.
- 각 기능은 local → 통합 테스트 → 내부 교사 테스트 순으로 활성화한다.
- A/B 기능 비활성화 시 현재 생성·수정 경로가 그대로 동작해야 한다.
- C 비활성화 시 A의 Dense + MMR 순위를 사용한다.

---

## 17. 최종 설계 요약

이 설계는 문제 텍스트와 이미지를 함께 다시 생성하는 방식에 의존하지 않는다. 신규 문제는 먼저
교육적 의미, 파라미터, 계산 관계, 템플릿, DiagramSpec을 구조화하고, 화면 문제와 SVG를 같은
정본에서 만든다. 기존 문제는 실제 수정·참고 요청이 있을 때만 구조화한다.

A안은 즉시 사용할 수 있는 기본 RAG와 데이터 수집을 제공한다. B안은 운영 데이터 없이 타입,
계산, patch, 결정적 렌더링으로 생성·수정 정확도를 높인다. C안은 A에서 축적된 검색·교사 결과를
사용해 hybrid retrieval과 reranking을 안전하게 추가한다. 세 단계 모두 현재 Problem 도메인의
Port, Authoring Version, 검증, HITL 경계를 유지한다.
