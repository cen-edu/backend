# 취약점 기반 맞춤 문제 생성 설계

## 1. 배경

교사는 `/problems/custom`에서 채점이 끝난 원본 학습지와 학생을 선택하고,
취약점 분석 결과를 바탕으로 복습·유사·응용 문항 수를 조정한 뒤 문제 생성을
요청한다.

현재 프론트는 `src/mocks/customCreation.js`의 고정 템플릿으로 문항을 즉시 만들고
있다. 백엔드에는 다음 기반이 이미 존재하지만 이를 연결하는 맞춤 생성 진입점이
없다.

- 분석 도메인의 `ReissueProposalService`
- 문제 도메인의 비동기 생성 Job, 문항별 검증·재시도, 문제은행 재사용
- 개인화 생성 목적 `PERSONALIZED_SIMILAR_SHORTAGE`,
  `PERSONALIZED_APPLICATION`
- 맞춤 학습지 계보와 학생 단위 배포를 수용하는 DB 스키마

Notion의 분석 보고서 설계 원칙에 따라 점수·정답률·학생 답안 같은 분석 원본을
새 테이블에 중복 저장하지 않는다. 맞춤 생성은 AI 보고서 문장이 아니라 기존
채점 데이터에서 매번 계산한 구조화된 재출제 제안을 입력으로 사용한다. 따라서
AI 분석 보고서가 `FAILED`여도 맞춤 문제 생성은 동작해야 한다.

## 2. 목표와 비목표

### 목표

- 서버가 계산한 재출제 제안을 맞춤 문제 생성의 단일 기준으로 사용한다.
- 복습은 오답 원문항 재사용, 유사는 문제은행 우선·부족분 AI 생성, 응용은 조건부
  AI 생성으로 분리한다.
- 같은 학생이 이미 받은 문항을 다시 뽑지 않는다.
- 생성 요청을 멱등 비동기 Job으로 처리하고 문항별 진행·실패·재시도를 제공한다.
- 생성 결과가 복습·유사·응용 중 어느 단계인지 Job 재조회 후에도 보존한다.
- 생성 결과를 기존 문제 미리보기·AI 편집·학습지 저장 흐름에 연결할 수 있게 한다.

### 비목표

- AI 분석 보고서의 자연어 문장을 생성 프롬프트에 넣지 않는다.
- 학생의 원문 답안이나 필기 인식 문자열을 문제 생성 LLM에 전달하지 않는다.
- 첫 버전에서 취약점 분석 범위 밖의 소단원을 수동 추가하지 않는다.
- 맞춤 문제 생성 버튼에서 학습지 저장과 학생 배포까지 자동 수행하지 않는다.
- 기존 일반 학습·종합평가 생성 API 계약은 바꾸지 않는다.

## 3. 핵심 결정

### 3.1 서버 제안이 정본이다

프론트는 화면 표시를 위해 재출제 제안을 조회하지만, 생성 POST에서 후보 문항 ID,
난이도, 취약 영역, 제외 문항 ID를 다시 보내지 않는다. 서버는 POST 처리 시
`ReissueProposalService`를 다시 호출하고 다음을 검증한다.

- 교사에게 원본 배정 접근 권한이 있는가
- 학생이 해당 원본 배정을 받았고 채점이 완료됐는가
- 요청 소단원이 현재 제안에 존재하는가
- 단계별 수량이 현재 `maxCount` 이하인가
- 응용 수량이 1 이상이면 `advanced.triggered`가 참인가
- 전체 요청 문항 수가 20 이하인가

새 채점 결과 때문에 GET 이후 제안이 달라진 경우 POST 시점의 최신 제안을 적용한다.
상한을 넘긴 요청은 조용히 줄이지 않고 도메인 오류로 거절한다.

### 3.2 화면과 생성의 기준 축은 소단원이다

`ReissueProposalResponse`는 소단원 단위 계약이다. 프론트 mock의 `conceptId` 문자열을
서버 소단원으로 다시 추측하지 않는다. 화면의 한 행은 `subUnitId/subUnitName`이며,
평가 영역·풀이 단계는 우측 제안 근거로 표시한다.

기본 선택 대상은 다음 중 하나를 만족하는 소단원이다.

- `historicalIncorrectItemCount > 0`
- `advanced.triggered == true`

기본 수량은 서버의 `proposedCount`를 사용한다. 누적 문항 수가 20을 넘으면
`incorrectSessionCount`, `historicalIncorrectItemCount`, 교육과정 순서 순으로 우선순위를
정해 선택한다. 선택이 끝난 뒤 화면과 최종 문항 구성은 다시 교육과정 순서로 정렬한다.
한도에 걸린 마지막 소단원은 유사 문항 수만 남은 한도까지 줄인다. 교사는 20문항 범위
안에서 수량을 다시 조정할 수 있다.

### 3.3 단계 순서

최종 문항 순서는 단계 우선으로 고정한다.

1. 모든 소단원의 `REVIEW`
2. 모든 소단원의 `SIMILAR`
3. 모든 소단원의 `ADVANCED`

같은 단계 안에서는 재출제 제안의 교육과정 순서를 유지한다. 이 순서는 학생이 먼저
틀린 문제를 되짚고, 다음에 유사 문제로 확인하고, 마지막에 응용하는 학습 흐름과 같다.

### 3.4 단계별 공급 정책

#### REVIEW

- `review.candidateQuestionIds` 앞에서부터 요청 수만큼 사용한다.
- 해당 문항의 검증된 Snapshot을 `BANK_REUSE` 슬롯으로 만든다.
- LLM을 호출하지 않는다.
- 화면에는 원본 문항 ID를 `sourceQuestionId`로 제공한다.

#### SIMILAR

- `similar.referenceQuestions`의 첫 항목을 주 ORIGIN으로 사용한다. 목록은 반복 오답과
  최근 오답을 반영한 우선순위 순서다.
- 나머지 오답 문항은 EXAMPLE 참고로 전달할 수 있다.
- `similar.excludedQuestionIds`와 같은 Job에서 이미 선택된 문항을 모두 제외한다.
- 같은 소단원, `similar.difficulty`, `STEP_FILL` 조건으로 벡터 검색해 최대 4문항을
  문제은행에서 재사용한다.
- 요청 수보다 부족한 슬롯만 `PERSONALIZED_SIMILAR_SHORTAGE`로 AI 생성한다.
- 오답 ORIGIN이 없으면 유사 문항을 개인화할 근거가 없으므로 제안의 유사
  `proposedCount/maxCount`를 0으로 반환하고 생성 요청도 거절한다.

#### ADVANCED

- `advanced.triggered == true`일 때만 요청할 수 있다.
- 기본 제안은 기존 정책대로 0문항이다.
- 전 슬롯을 `PERSONALIZED_APPLICATION`으로 AI 생성한다.
- `primaryEvaluationArea`, `primaryTargetStage`, 분포 배열을 구조화된 생성 명령으로
  전달한다.
- 문제 검색은 예시 참고용으로만 사용하고, 결과를 그대로 응용 문항으로 재사용하지
  않는다.
- 난이도는 `high`, 문항 유형은 `STEP_FILL`, 풀이 구조는 필수로 지정한다.

## 4. API 계약

### 4.1 재출제 제안 조회

기존 API를 유지한다.

```http
GET /api/teacher/analysis/assignments/{sourceAssignmentId}/students/{studentId}/reissue-proposal
```

유사 ORIGIN이 없는 소단원은 `similar.proposedCount=0`, `similar.maxCount=0`으로
보정한다. 프론트는 상한을 하드코딩하지 않고 응답을 사용한다.

### 4.2 맞춤 생성 시작

```http
POST /api/teacher/custom-problems/generate/async
```

```json
{
  "clientRequestId": "c9e13f16-773d-4f6b-9219-03ed86105494",
  "sourceAssignmentId": 120,
  "studentId": 35,
  "items": [
    {
      "subUnitId": 14,
      "reviewCount": 1,
      "similarCount": 5,
      "advancedCount": 0
    }
  ]
}
```

요청의 `items`는 중복 `subUnitId`를 허용하지 않는다. 각 수량은 0 이상이며 전체 합은
1 이상 20 이하다. `clientRequestId`는 교사 범위의 멱등 키다.

응답은 기존 `ProblemGenerationStartResponse`를 재사용한다.

```json
{
  "jobId": 901,
  "status": "QUEUED",
  "totalCount": 6
}
```

### 4.3 생성 상태 조회

기존 공통 Job 조회 API를 재사용한다.

```http
GET /api/teacher/problems/generation-jobs/{jobId}
```

`ProblemGenerationSlotResponse`에 다음 필드를 추가한다.

```json
{
  "slotIndex": 1,
  "customStage": "review",
  "sourceQuestionId": 501,
  "originQuestionId": null,
  "status": "READY",
  "preview": {}
}
```

- 일반·종합 Job의 `customStage`는 `null`이다.
- 맞춤 Job은 모든 슬롯에 `review|similar|advanced` 중 하나가 존재한다.
- `sourceQuestionId`는 문제은행 재사용 슬롯의 실제 재사용 문항 ID다. AI 생성 슬롯은
  `null`이다.
- `originQuestionId`는 유사·응용 생성의 주 ORIGIN 문항 ID다. REVIEW는
  `sourceQuestionId` 자체가 원문항이므로 `null`이다.

## 5. 백엔드 구성

### 5.1 컨트롤러와 오케스트레이션

`domain/problem` 소유 경계에 다음을 추가한다.

- `CustomProblemGenerationController`
- `CustomProblemGenerationService`
- 요청 DTO와 단계별 수량 DTO

컨트롤러는 `/api/teacher/custom-problems`를 소유하고
`@AuthenticationPrincipal AuthenticatedUser`에서 교사 ID만 꺼내 서비스에 전달한다.

`CustomProblemGenerationService`는 다른 도메인의 Repository를 직접 호출하지 않는다.
분석 근거는 `ReissueProposalService`의 public 메서드로 받고, 교육과정 경로와 문제
Snapshot은 각 도메인의 public Service를 통해 조회한다.

### 5.2 맞춤 생성 계획기

일반 생성의 무작위 문제은행 선정을 그대로 사용하면 오답과 의미적으로 유사한 문제를
고를 수 없다. `PersonalizedProblemGenerationPlanningService`를 별도 컴포넌트로 둔다.

책임은 다음으로 제한한다.

- 최신 제안과 요청 수량 검증
- REVIEW/SIMILAR/ADVANCED 슬롯 조립
- 제외 문항 집합 전파
- ORIGIN/EXAMPLE Snapshot 조립
- 단계 메타데이터가 포함된 `ProblemGenerationPlan` 생성

Job 저장, Worker 실행, 검증, 재시도는 기존 서비스를 재사용한다.

### 5.3 공통 맞춤 단계 enum

현재 맞춤 단계는 worksheet, analysis, problem 세 경계에서 함께 사용한다.
`CustomStage`를 `global/common/enums`로 이동해 값 `REVIEW/SIMILAR/ADVANCED`를 단일
선언으로 사용한다. DB 저장값은 바뀌지 않는다.

외부 API 변환은 기존 화면 계약을 유지한다.

| 공통 enum | 재출제·생성 API | worksheet API·프론트 |
|---|---|---|
| `REVIEW` | `review` | `retrace` |
| `SIMILAR` | `similar` | `basic` |
| `ADVANCED` | `advanced` | `independent` |

문자열 변환은 각 API 응답/요청 Formatter 한 곳에서만 수행한다.

### 5.4 Job 단계 메타데이터

`problem_generation_item`에 다음 컬럼을 새 Flyway 마이그레이션으로 추가한다.

- `custom_stage VARCHAR(20) NULL`
- `origin_question_id BIGINT NULL`

맞춤 Job의 모든 Item은 `custom_stage`가 필수이고, 일반·종합 Job은 `NULL`이어야 한다.
`origin_question_id`는 AI 유사·응용 슬롯의 주 ORIGIN을 보존한다. 기존
`source_question_id`는 실제 문제은행 재사용 문항을 계속 뜻한다. 한 컬럼에 두 의미를
섞지 않는다.

적용된 기존 마이그레이션은 수정하지 않고 타임스탬프 버전의 새 파일을 추가한다.

## 6. 프론트 구성

맞춤 페이지의 mock 의존성을 다음 계층으로 교체한다.

- `src/api/analysis/analysisApi.js`: 재출제 제안 조회 추가
- `src/api/problems/customProblemGenerationApi.js`: 생성 시작 API
- `CustomProblemPage/customProblemGenerationAdapter.js`: API enum과 화면 모델 변환
- `CustomProblemPage/customProblemGenerationHooks.js`: 조회·mutation·폴링
- `CustomProblemPage.jsx`: 선택과 화면 전환만 조율

기존 `problemGenerationPolling.js`, 문제 미리보기, AI 편집 패널을 재사용한다.

화면 동작은 다음과 같다.

- 학생 선택 시 해당 학생의 재출제 제안을 독립 조회한다.
- 조회 중에는 문항 구성 skeleton과 비활성 생성 버튼을 표시한다.
- 자료 부족·채점 전·제안 없음은 오류와 구분한다.
- 생성 클릭 후 버튼을 비활성화하고 `완료 수/전체 수`를 표시한다.
- READY 문항은 완료 즉시 미리보기 가능하게 하되 출력 가드레일과 검증이 끝난
  Snapshot만 사용한다.
- 일부 문항 실패 시 성공 문항을 유지하고 실패 슬롯별 오류와 재시도 가능 여부를
  표시한다.
- Job 전체가 끝나면 기존 생성 결과 집중 레이아웃으로 전환한다.

## 7. 학습지 저장과 학생 배포

생성 버튼의 책임과 분리하지만, 생성 결과가 다음 단계로 이어질 수 있도록 계약을
맞춘다.

- 학습지 저장은 `POST /api/teacher/worksheets`를 재사용한다.
- `origin=custom`, `sourceAssignmentId`, `parentWorksheetId`, 문항별 `customStage`를
  전달한다.
- `parentWorksheetId`는 클라이언트가 추측하지 않고 맞춤 저장용 백엔드 서비스가 원본
  학습지 또는 가장 최근 맞춤 학습지를 결정하도록 후속 단계에서 개선한다.
- 학생 단위 배포는 DB가 이미 지원하지만 현재 공개 API가 반 단위이므로 별도 학생 배포
  서비스/API가 필요하다.

첫 구현 단위는 생성 결과 미리보기까지다. 저장과 학생 배포 연결은 생성 API가 안정된
뒤 같은 설계의 두 번째 구현 단위로 진행한다.

## 8. 오류 처리

새 오류는 `global/common/ErrorCode`와 공통 예외 처리기를 사용한다.

- `CUSTOM_PROBLEM_EMPTY_SELECTION`: 전체 요청 수가 0
- `CUSTOM_PROBLEM_TOTAL_LIMIT_EXCEEDED`: 전체 20문항 초과
- `CUSTOM_PROBLEM_SUB_UNIT_NOT_PROPOSED`: 최신 제안에 없는 소단원
- `CUSTOM_PROBLEM_COUNT_EXCEEDS_PROPOSAL`: 단계별 최신 상한 초과
- `CUSTOM_PROBLEM_SIMILAR_REFERENCE_MISSING`: 유사 ORIGIN 없음
- `CUSTOM_PROBLEM_ADVANCED_NOT_ALLOWED`: 응용 발동 조건 불충족

채점 전, 학생 미배정, 교사 접근 권한 오류는 기존 분석 도메인 오류를 그대로 올린다.
LLM 또는 검증 실패는 Job 전체 HTTP 요청 실패로 바꾸지 않고 기존 문항별 실패 상태로
표현한다.

## 9. 안전과 로깅

맞춤 생성은 사용자의 자유 입력 프롬프트가 없는 시스템 트리거 호출이다. 따라서
`AgentDispatcher`를 거치지 않고 problem 도메인 Port와 `ai/problem/adapter` 경로를
사용한다. 생성 결과를 교사가 자연어로 수정하는 후속 기능만 `PROBLEM_EDIT`
AgentDispatcher를 사용한다.

학생의 답안 원문은 생성 명령과 로그에 포함하지 않는다. 다음 구조화 정보만 사용한다.

- 소단원 ID·이름
- 현재 난이도
- 반복 오답 횟수
- 평가 영역별 집계
- 풀이 단계별 집계
- ORIGIN 문제 Snapshot

로그에는 교사 ID, Job ID, 소단원 ID, 단계, 요청·재사용·AI 생성 수, 제외 문항 수,
traceId를 기록한다. 학생 답안·문항 원문·정답 원문은 기록하지 않는다.

## 10. 테스트 전략

### 백엔드

- 제안 재검증과 20문항 상한 단위 테스트
- REVIEW 후보 우선순위·정확한 ID 재사용 테스트
- SIMILAR 제외 집합과 ORIGIN 필수 조건 테스트
- ADVANCED 발동 조건·구조화 근거 매핑 테스트
- 단계 우선 슬롯 순서 테스트
- 같은 `clientRequestId` 재요청의 멱등성 테스트
- Job 단계 메타데이터 영속화 통합 테스트
- 컨트롤러 인증·권한·도메인 오류 MockMvc 테스트
- `AiClientAccessTest`를 포함한 전체 아키텍처 테스트

### 프론트

- 재출제 제안 → 화면 구성 어댑터 테스트
- 전체 20문항 기본 선택·조정 테스트
- 생성 요청 payload 테스트
- Job 슬롯의 단계·미리보기 변환 테스트
- 일부 실패·재시도 표시 테스트

프론트 저장소 규칙에 따라 에이전트가 브라우저·빌드·테스트를 실행하지 않고, 변경
파일과 사용자가 실행할 검증 항목을 전달한다. 백엔드는 저장소 규칙에 따라 관련 테스트와
`./gradlew build`를 실행한다.

## 11. 구현 순서

1. 공통 `CustomStage` 이동과 호환 Formatter 정리
2. 재출제 제안의 유사 ORIGIN 없는 경우 0건 보정
3. 맞춤 생성 요청 DTO·검증·컨트롤러
4. 맞춤 계획기와 단계별 슬롯 조립
5. Job 단계 메타데이터 마이그레이션·응답 확장
6. 백엔드 테스트와 빌드
7. 프론트 재출제 제안 조회·어댑터·생성 mutation 연동
8. mock 생성 제거와 생성 결과 미리보기 연결
9. 사용자 검증 항목 전달

## 12. 대안과 선택 이유

### 모든 문항을 LLM으로 생성

구현은 단순하지만 비용·지연·실패율이 높고, 최근 오답을 그대로 복습해야 하는 요구를
정확히 만족하지 못한다. 선택하지 않는다.

### 프론트가 후보·난이도·근거를 모두 조립

백엔드 변경은 적지만 화면 데이터가 오래되거나 조작될 수 있고, 재출제 정책이 여러
클라이언트로 분산된다. 선택하지 않는다.

### 서버 제안 재검증 + 문제은행 우선 + AI 부족분 생성

정책이 한 곳에 있고, 기존 비동기 Job과 검증 파이프라인을 재사용하며, 비용과 지연을
줄일 수 있다. 본 설계가 선택한 방식이다.
