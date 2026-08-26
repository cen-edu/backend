# 문제 수정 경로 복구 설계

## 목표

교사의 자연어 문제 수정 요청이 출력 가드, semantic 편집, 문제은행 교체, AI 생성 폴백까지 일관되게 실행되도록 복구한다. 특히 다음 요청을 실제 데이터로 처리할 수 있어야 한다.

- 난이도를 한 단계 낮추거나 높이는 요청
- 이미지가 있거나 없는 문항으로 교체하는 요청
- 객관식·주관식·서술형·빈칸형 사이의 유형 교체 요청
- 문제 속 독립 입력값을 바꾸는 파라미터 편집
- 객관식 보기 순서 재배치
- 문제은행에 정확히 맞는 문항이 없을 때 유사 문항을 예시로 사용한 새 문항 생성

## 현재 실패 원인

1. `RequestedProblemSpecification.isEmpty()`가 Jackson 계산 프로퍼티 `empty`로 직렬화되어 `ProblemEditOutputGuard`의 재역직렬화가 실패한다.
2. `modificationDeltaFor()`가 모든 필드를 무제약 union schema로 선언하여 OpenAI strict JSON Schema 검증에서 400을 반환한다.
3. semantic 추출 JSON Schema가 후단 validator의 대문자 논리 키 규칙을 표현하지 않아 대부분의 추출 결과가 폐기된다.
4. 현재 유일한 READY semantic model의 parameter가 전부 `editable=false`여서 파라미터 편집 가능 데이터가 없다.
5. 문제은행 이미지 조건이 무작위 8건 선택 이후 적용되어, 적합한 이미지 문항이 있어도 놓칠 수 있다.
6. 문제은행 교체 실패 후 수정 생성 경로에는 `ProblemReferenceRetrievalPort`가 연결되어 있지 않다.

## 설계

### 1. 출력 가드와 요청 스펙 직렬화

`RequestedProblemSpecification.isEmpty()`를 JavaBean getter로 인식되지 않는 의도형 메서드 이름으로 바꾼다. `ProblemEditAgent`는 새 메서드로 빈 스펙을 `null`로 정규화한다.

`ProblemEditOutputGuard`는 Agent가 반환한 값이 이미 `ProblemEditConversationResult`이면 직접 사용하고, Map 등 외부 표현일 때만 `ObjectMapper.convertValue()`를 사용한다. 예외를 사용자 입력이나 정답 없이 예외 종류와 안전한 메시지만 로그에 남겨 다시 같은 블라인드 실패가 생기지 않게 한다.

### 2. 수정 delta strict schema

`modificationDeltaFor()`는 `repairDeltaFor()`와 동일하게 검증된 `CANDIDATE.properties` 하위 schema를 복사한다. 수정 대상에 해당하는 필드만 `properties`와 `required`에 넣는다. `WHOLE_QUESTION`과 `QUESTION_TYPE`은 기존처럼 전체 `CANDIDATE` schema를 사용한다.

테스트는 생성된 schema의 모든 object 노드에 `additionalProperties:false`가 있는지 재귀 검증하고, CHOICE·CONTENT·ANSWER 등 대상별 필드와 `required`가 정확한지 확인한다.

### 3. semantic 추출 계약 정렬

semantic model 출력 schema의 parameter key에 `^[A-Z][A-Z0-9_]{0,63}$` 패턴을 추가해 provider 출력 단계에서 후단 validator 규칙을 강제한다. 프롬프트에는 bounds가 숫자 parameter에만 허용되고 현재 값을 포함해야 하며, 표현용 축·눈금 값과 오답 전용 값만 `editable=false`라는 규칙을 명시한다.

추출 결과가 domain validation에서 실패하면 원문을 다시 생성하지 않고, 검증 위반 목록을 안전하게 제한해 한 번만 교정 요청한다. 두 번째 결과도 실패하면 기존 `FAILED` 처리로 종료한다. 정답 및 원문 전체는 로그에 남기지 않는다.

semantic 성공률과 editable 비율은 DB 진단 쿼리로 별도 측정하며, 기능 테스트에는 editable parameter가 포함된 모델을 고정 fixture로 사용한다.

### 4. 문제은행 조건 필터

`ProblemQuestionSelector`에 자산 보유 조건을 받는 조회 경계를 추가한다. `requiresAsset=true/false`이면 전체 후보에서 해당 조건을 먼저 적용한 후 shuffle과 limit을 수행한다. 조건이 없으면 기존 동작을 유지한다.

교체 실행은 같은 소단원·목표 난이도·목표 유형·자산 조건·제외 문항을 모두 만족하는 후보만 최대 8건 검증한다. 재사용 가능한 후보가 없을 때만 AI 생성으로 폴백한다.

### 5. 수정 생성용 유사 예시 검색

`ProblemReferenceQuery`에 선택적 `queryHint`를 추가한다. 기존 생성 호출자는 빈 힌트를 사용하므로 동작이 변하지 않는다. 수정 교체 폴백에서는 정규화된 교사 지시와 현재 Snapshot을 함께 검색 문서로 구성한다.

`ProblemModificationExecutionCoordinator`는 다음 조건에서만 검색한다.

- action이 `REPLACE`
- 문제은행 재사용이 실패했거나 정책이 `GENERATE_ONLY`
- RAG가 활성화되고 retrieval Port가 존재함
- 현재 문항의 ORIGIN ID와 curriculum scope를 구성할 수 있음

검색 결과는 최대 4개의 `GenerationReferenceRole.EXAMPLE`로 변환한다. 현재 문항은 ORIGIN으로 검색 query에만 사용하고 예시 목록에는 중복 포함하지 않는다. 검색 실패·timeout·후보 없음은 기존 수정 생성으로 조용히 폴백한다.

`ProblemModificationCommand`는 curriculum과 reference 목록을 전달한다. `ModificationPromptStrategy`는 기존 `FewShotReferenceSerializer`로 정답을 제거한 `FEW_SHOT_JSON`만 프롬프트에 추가하며 직접 복사를 금지한다.

## 데이터 흐름

1. 사용자 입력을 `ProblemEditAgent`가 구조화한다.
2. 출력 가드가 action, requested specification, semantic patch binding을 검증한다.
3. 확인 요청이면 pending command에 저장하고, 사용자가 확인하면 실행 계획을 확정한다.
4. 교체 요청이면 조건을 DB 조회에 먼저 적용해 문제은행 후보를 찾는다.
5. 적합한 문항이 있으면 AI 호출 없이 `BANK_REUSE` Version으로 승격한다.
6. 적합한 문항이 없으면 현재 문항과 교사 지시로 pgvector 예시를 검색한다.
7. 검색된 예시를 정답 없는 few-shot으로 수정 LLM에 전달한다.
8. 후보는 기존 구조·정규화·검증 파이프라인을 통과한 뒤에만 Version으로 승격한다.

## 오류 처리

- 출력 가드의 형식 오류는 원인 종류를 로그에 남기되 사용자 입력과 정답은 남기지 않는다.
- OpenAI schema는 호출 전에 단위 테스트로 strict subset을 검증한다.
- semantic 교정은 최대 1회만 허용해 호출 폭증을 막는다.
- 검색 장애는 수정 실패로 승격하지 않고 예시 없는 기존 생성으로 폴백한다.
- 수정 실행 실패 시 Session은 기존 `abortActiveExecution()` 경로로 재시도 가능한 상태를 유지한다.

## 테스트 기준

- requested specification이 있는 정상 Agent 응답이 출력 가드를 통과한다.
- 빈 requested specification이 JSON에 `empty`를 만들지 않는다.
- CHOICE와 CONTENT 수정 schema가 OpenAI strict object 규칙을 만족한다.
- 잘못된 semantic key가 provider schema에서 제한되고, validation 실패 시 교정은 정확히 한 번 실행된다.
- editable parameter 변경이 semantic materialize와 Snapshot diff까지 성공한다.
- 이미지 후보가 전체 풀에 한 건만 있어도 무작위 8건 제한 전에 필터되어 선택 가능하다.
- 문제은행 hit에서는 retrieval과 LLM을 호출하지 않는다.
- 문제은행 miss에서는 교사 지시가 검색 query에 포함되고, EXAMPLE이 수정 프롬프트에 전달된다.
- retrieval 실패 시 예시 없는 수정 생성으로 폴백한다.
- 관련 단위·통합 테스트와 `./gradlew build`가 통과한다.

## 비범위

- 새로운 DB 테이블 또는 Flyway migration 추가
- 에이전트/Dispatcher 경계 변경
- 검색 인덱스 embedding 차원 또는 모델 변경
- 문제 생성 전체를 수정 경로로 통합하는 대규모 리팩터링
- 운영 데이터 전체 semantic backfill 자동 실행
