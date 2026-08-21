# Problem 도메인 API 명세서

## 문서 범위

이 문서는 Problem 도메인의 현재 구현을 기준으로, 기존에 작성된 세 API를 제외한 나머지 공개 API를 설명한다.

기존 작성 범위:

- `GET /api/teacher/problems/units`
- `POST /api/teacher/problems/generate`
- `POST /api/teacher/assessments/generate`

이번 작성 범위:

| 구분 | Method | Endpoint |
| --- | --- | --- |
| 일반 문제 비동기 생성 접수 | `POST` | `/api/teacher/problems/generate/async` |
| 종합평가 비동기 생성 접수 | `POST` | `/api/teacher/assessments/generate/async` |
| 맞춤 문제 비동기 생성 접수 | `POST` | `/api/teacher/custom-problems/generate/async` |
| 비동기 생성 Job 조회 | `GET` | `/api/teacher/problems/generation-jobs/{jobId}` |
| 문제 저작 Session 상태 조회 | `GET` | `/api/teacher/problems/authoring-sessions/{sessionId}/status` |
| 문제 저작 Snapshot 조회 | `GET` | `/api/teacher/problems/authoring-sessions/{sessionId}/preview` |
| AI 에이전트 문제 수정 | `POST` | `/api/teacher/problems/authoring-sessions/{sessionId}/edit/turns` |

> 기존 문서에 `/api/teacher/problems/unit`으로 적혀 있다면 실제 구현 경로인 `/api/teacher/problems/units`로 수정해야 한다.

> `/generate`와 `/generate/async`는 응답 계약이 다르다. 동기 API는 문제은행 문항 배열을 즉시 반환하지만, 비동기 API는 `jobId`만 반환하고 Job 조회 API에서 생성 결과를 확인한다.

## 공통 인증·권한

- 모든 API는 인증이 필요하다.
- `TEACHER` 권한이 필요하다.
- `Authorization: Bearer {accessToken}` 헤더를 사용한다.
- 요청한 교사의 Job 또는 Session이 아니면 존재 여부를 노출하지 않고 `NOT_FOUND` 계열 오류로 처리한다.

## 공통 응답 형식

성공 응답은 다음 형식을 사용한다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패 응답은 다음 형식을 사용한다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "오류 메시지"
  }
}
```

---

# 일반 문제 비동기 생성 접수

```http
POST /api/teacher/problems/generate/async
```

## 역할

일반 학습 화면에서 선택한 소단원·난이도·문항 수에 맞는 `STEP_FILL` 문항 생성 Job을 접수한다.

먼저 문제은행에서 재사용 가능한 문항을 찾고, 부족한 수량만 AI로 생성한다. 응답 시점에는 전체 문항 생성이 끝나지 않았을 수 있으므로 문항 배열 대신 `jobId`를 반환한다.

## 인증·권한

- 인증 필요
- `TEACHER` 권한 필요
- `Authorization: Bearer {accessToken}` 헤더를 사용한다.

## Request

```json
{
  "clientRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "subUnitId": 13,
      "difficulty": 1,
      "count": 1
    },
    {
      "subUnitId": 13,
      "difficulty": 2,
      "count": 2
    }
  ]
}
```

### Request Body 필드

| 필드 | 타입 | 필수 | 역할 |
| --- | --- | --- | --- |
| `clientRequestId` | UUID | 필수 | 교사별 중복 Job 생성을 방지하는 멱등 키 |
| `items` | array | 필수 | 출제 조건 목록. 하나 이상이어야 한다. |
| `items[].subUnitId` | long | 필수 | 출제할 소단원 ID |
| `items[].difficulty` | short | 필수 | 난이도. `1`, `2`, `3` 중 하나 |
| `items[].count` | int | 필수 | 해당 조건에서 준비할 문항 수. `1~30` |

### 난이도 값

| 값 | 의미 | Snapshot 값 |
| --- | --- | --- |
| `1` | 하 | `low` |
| `2` | 중 | `mid` |
| `3` | 상 | `high` |

## 생성 규칙

- 일반 문제의 문항 유형은 요청에서 받지 않고 항상 `STEP_FILL`로 고정한다.
- 조건별로 문제은행의 재사용 가능한 문항을 먼저 배치한다.
- 문제은행 문항이 부족한 슬롯만 AI 생성·검증을 비동기로 수행한다.
- 각 결과 문항은 독립적인 `sessionId`와 현재 `versionId`를 가진다.
- 결과 슬롯 순서는 요청 `items`의 순서와 각 항목의 `count` 순서를 따른다.
- 동일한 교사가 동일한 `clientRequestId`로 다시 요청하면 새 Job을 만들지 않고 기존 Job을 반환한다.
- `clientRequestId`는 일반·종합평가·맞춤 문제 API 전체에서 교사별로 공유되는 멱등 키이므로, 서로 다른 생성 동작에는 반드시 새로운 UUID를 사용한다.

## Response

```json
{
  "success": true,
  "data": {
    "jobId": 55,
    "status": "QUEUED",
    "totalCount": 3
  },
  "error": null
}
```

### Response 필드

| 필드 | 역할 |
| --- | --- |
| `jobId` | 이후 polling에 사용할 생성 Job ID |
| `status` | 접수 직후 Job 상태 |
| `totalCount` | 요청으로 만들어진 전체 문항 슬롯 수 |

접수 직후 상태는 일반적으로 `QUEUED`이다. 모든 슬롯을 문제은행 문항으로 즉시 준비할 수 있으면 `COMPLETED`가 반환될 수 있다.

## 오류 응답

### 입력값 검증 실패

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_INPUT_VALUE",
    "message": "items[0].count: 문항 수는 1 이상이어야 합니다."
  }
}
```

### 존재하지 않는 소단원

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "CURRICULUM_SUB_UNIT_NOT_FOUND",
    "message": "소단원을 찾을 수 없습니다."
  }
}
```

잘못된 UUID나 해석할 수 없는 JSON은 `MALFORMED_REQUEST_BODY`로 반환한다. AI 생성 중 발생한 실패는 접수 응답이 아니라 Job 조회 응답의 `slots[].errorCode`에서 확인한다.

## 프론트 처리 흐름

```text
소단원·난이도별 수량 선택
→ 버튼 클릭 시 clientRequestId 1회 생성
→ POST /api/teacher/problems/generate/async 1회 호출
→ 응답의 jobId 보존
→ GET /api/teacher/problems/generation-jobs/{jobId} 반복 조회
→ COMPLETED 또는 PARTIALLY_FAILED 또는 FAILED에서 polling 종료
→ READY 슬롯의 sessionId·versionId·snapshot을 화면 모델에 보존
```

---

# 종합평가 비동기 생성 접수

```http
POST /api/teacher/assessments/generate/async
```

## 역할

종합평가 화면에서 사용자가 선택한 소단원·문항 유형·난이도·문항 수에 맞는 문항 생성 Job을 접수한다.

문제은행에서 재사용 가능한 문항을 먼저 배치하고 부족한 수량만 AI로 생성한다. 종합평가에서는 `MULTIPLE_CHOICE`, `SHORT_INPUT`, `ESSAY`만 허용한다.

## 인증·권한

- 인증 필요
- `TEACHER` 권한 필요
- `Authorization: Bearer {accessToken}` 헤더를 사용한다.

## Request

```json
{
  "clientRequestId": "1ec0b4a9-14df-45ad-9ba7-c37ab17feee8",
  "items": [
    {
      "subUnitId": 13,
      "questionType": "MULTIPLE_CHOICE",
      "difficulty": 1,
      "count": 1
    },
    {
      "subUnitId": 13,
      "questionType": "SHORT_INPUT",
      "difficulty": 2,
      "count": 1
    },
    {
      "subUnitId": 27,
      "questionType": "ESSAY",
      "difficulty": 3,
      "count": 1
    }
  ]
}
```

### Request Body 필드

| 필드 | 타입 | 필수 | 역할 |
| --- | --- | --- | --- |
| `clientRequestId` | UUID | 필수 | 교사별 중복 Job 생성을 방지하는 멱등 키 |
| `items` | array | 필수 | 출제 조건 목록. 하나 이상이어야 한다. |
| `items[].subUnitId` | long | 필수 | 출제할 소단원 ID |
| `items[].questionType` | enum | 필수 | 출제할 문항 유형 |
| `items[].difficulty` | short | 필수 | 난이도. `1`, `2`, `3` 중 하나 |
| `items[].count` | int | 필수 | 해당 조건에서 준비할 문항 수. `1~10` |

### 허용 문항 유형

| 값 | 화면 표시 | Snapshot 주요 데이터 |
| --- | --- | --- |
| `MULTIPLE_CHOICE` | 객관식 | `choices`, `answerUnits` |
| `SHORT_INPUT` | 단답형 | `answerUnits` |
| `ESSAY` | 서술형 | `answerUnits`, `rubricItems` |

`STEP_FILL`은 종합평가에서 허용하지 않는다.

## 생성 규칙

- 문제은행 재사용과 AI 부족분 생성을 하나의 Job으로 처리한다.
- 문항별 생성·검증은 독립적으로 진행되므로 일부 문항만 실패할 수 있다.
- 결과 슬롯 순서는 요청 `items` 순서를 따른다.
- 각 문항의 `sessionId`는 AI 수정과 문제은행 최종 저장까지 유지해야 한다.
- 동일한 교사와 `clientRequestId` 조합은 같은 Job을 반환한다.

## Response

```json
{
  "success": true,
  "data": {
    "jobId": 56,
    "status": "QUEUED",
    "totalCount": 3
  },
  "error": null
}
```

응답 필드는 일반 문제 비동기 생성 접수와 동일하다. 실제 문항은 공통 Job 조회 API에서 받는다.

## 오류 응답

### `STEP_FILL` 요청

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ASSESSMENT_QUESTION_TYPE_NOT_ALLOWED",
    "message": "종합평가에서는 객관식, 단답형, 서술형 문항만 사용할 수 있습니다."
  }
}
```

### 지원하지 않는 문항 유형 문자열

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "MALFORMED_REQUEST_BODY",
    "message": "요청 본문을 해석할 수 없습니다."
  }
}
```

그 밖에 유효하지 않은 수량·난이도는 `INVALID_INPUT_VALUE`, 존재하지 않는 소단원은 `CURRICULUM_SUB_UNIT_NOT_FOUND`를 반환한다.

## 프론트 처리 흐름

```text
소단원·유형·난이도별 수량 설정
→ 버튼 클릭 시 clientRequestId 1회 생성
→ POST /api/teacher/assessments/generate/async 1회 호출
→ 같은 jobId로 Job 상태 polling
→ READY 슬롯을 종합평가 미리보기로 변환
→ 문항 순서·배점·sessionId·versionId 보존
```

---

# 맞춤 문제 비동기 생성 접수

```http
POST /api/teacher/custom-problems/generate/async
```

## 역할

채점이 완료된 원본 학습지 배정과 학생의 최신 취약점 제안을 기준으로 맞춤 문제 생성 Job을 접수한다.

프론트가 취약점 근거를 임의로 전송하지 않는다. 백엔드는 `sourceAssignmentId`와 `studentId`로 최신 재출제 제안을 다시 계산한 뒤, 교사가 선택한 단계별 문항 수가 해당 제안의 범위 안에 있는지 검증한다.

## 인증·권한

- 인증 필요
- `TEACHER` 권한 필요
- 요청 교사가 소유한 학습지 배정만 사용할 수 있다.
- 해당 학습지를 실제로 배정받은 학생만 사용할 수 있다.
- 원본 학습지는 채점 완료 상태여야 한다.

## Request

```json
{
  "clientRequestId": "a7a57de8-d8de-4d2d-8d9f-23cae4e21349",
  "sourceAssignmentId": 120,
  "studentId": 35,
  "items": [
    {
      "subUnitId": 20,
      "reviewCount": 1,
      "similarCount": 3,
      "advancedCount": 0
    },
    {
      "subUnitId": 21,
      "reviewCount": 1,
      "similarCount": 2,
      "advancedCount": 1
    }
  ]
}
```

### Request Body 필드

| 필드 | 타입 | 필수 | 역할 |
| --- | --- | --- | --- |
| `clientRequestId` | UUID | 필수 | 교사별 중복 Job 생성을 방지하는 멱등 키 |
| `sourceAssignmentId` | long | 필수 | 맞춤 학습의 기준이 되는 원본 학습지 배정 ID. 양수여야 한다. |
| `studentId` | long | 필수 | 맞춤 문제를 생성할 학생 ID. 양수여야 한다. |
| `items` | array | 필수 | 소단원별 단계 수량. `1~20`개 항목 |
| `items[].subUnitId` | long | 필수 | 최신 재출제 제안에 포함된 소단원 ID |
| `items[].reviewCount` | int | 필수 | 틀린 문항을 그대로 복습하는 수량. `0~10` |
| `items[].similarCount` | int | 필수 | 유사 문항 수량. `0~10` |
| `items[].advancedCount` | int | 필수 | 응용 문항 수량. `0~10` |

## 단계별 생성 규칙

| 단계 | `customStage` | 생성 방식 |
| --- | --- | --- |
| 복습 | `review` | 최신 오답 후보를 문제은행에서 그대로 재사용 |
| 유사 | `similar` | 재사용 가능한 유사 문항을 먼저 사용하고 부족분은 AI 생성 |
| 응용 | `advanced` | 상 난이도 `STEP_FILL` 문항을 AI 생성 |

추가 규칙:

- 전체 `reviewCount + similarCount + advancedCount` 합은 `1~20`이어야 한다.
- 같은 `subUnitId`를 `items`에 두 번 넣을 수 없다.
- 최신 재출제 제안에 없는 소단원은 요청할 수 없다.
- 각 단계의 수량은 최신 제안이 제공한 `maxCount`를 넘을 수 없다.
- `similarCount > 0`이면 오답 기준 문항이 하나 이상 존재해야 한다.
- `advancedCount > 0`이면 최신 제안의 응용 단계가 활성화되어 있어야 한다.
- 결과 슬롯은 교육과정 순서 안에서 `review → similar → advanced` 순서를 따른다.

## Response

```json
{
  "success": true,
  "data": {
    "jobId": 91,
    "status": "QUEUED",
    "totalCount": 8
  },
  "error": null
}
```

실제 문항과 `customStage`, 원본 문항 관계는 공통 Job 조회 API에서 확인한다.

## 오류 응답

| HTTP | 코드 | 발생 조건 |
| --- | --- | --- |
| `400` | `CUSTOM_PROBLEM_EMPTY_SELECTION` | 전체 선택 수량이 0인 경우 |
| `400` | `CUSTOM_PROBLEM_TOTAL_LIMIT_EXCEEDED` | 전체 문항 수가 20개를 넘은 경우 |
| `400` | `CUSTOM_PROBLEM_SUB_UNIT_DUPLICATED` | 같은 소단원을 중복 요청한 경우 |
| `400` | `CUSTOM_PROBLEM_SUB_UNIT_NOT_PROPOSED` | 최신 제안에 없는 소단원을 요청한 경우 |
| `400` | `CUSTOM_PROBLEM_COUNT_EXCEEDS_PROPOSAL` | 단계별 제안 상한을 넘은 경우 |
| `400` | `CUSTOM_PROBLEM_SIMILAR_REFERENCE_MISSING` | 유사 문항의 오답 기준 문항이 없는 경우 |
| `400` | `CUSTOM_PROBLEM_ADVANCED_NOT_ALLOWED` | 응용 문항 생성 조건을 충족하지 않은 경우 |
| `400` | `ANALYSIS_REISSUE_NOT_GRADED` | 원본 학습지 채점이 완료되지 않은 경우 |
| `404` | `ANALYSIS_STUDENT_NOT_ASSIGNED` | 해당 배정을 받은 학생이 아닌 경우 |
| `404` | `ANALYSIS_ASSIGNMENT_NOT_FOUND` | 원본 학습지 배정을 찾을 수 없는 경우 |
| `403` | `ANALYSIS_ASSIGNMENT_ACCESS_DENIED` | 다른 교사의 학습지 배정인 경우 |

### 전체 문항 수 초과

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "CUSTOM_PROBLEM_TOTAL_LIMIT_EXCEEDED",
    "message": "맞춤 문항은 한 번에 최대 20개까지 생성할 수 있습니다."
  }
}
```

### 채점 전 요청

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS_REISSUE_NOT_GRADED",
    "message": "채점이 완료된 뒤에 재출제를 제안할 수 있습니다."
  }
}
```

## 프론트 처리 흐름

```text
맞춤 문제 재출제 제안 조회
→ 교사가 소단원별 review/similar/advanced 수량 조정
→ 버튼 클릭 시 clientRequestId 1회 생성
→ POST /api/teacher/custom-problems/generate/async 1회 호출
→ 같은 jobId로 공통 Job 상태 polling
→ customStage별 READY 슬롯을 화면에 배치
→ sessionId·versionId를 보관함 저장까지 유지
```

---

# 비동기 생성 Job 조회

```http
GET /api/teacher/problems/generation-jobs/{jobId}
```

## 역할

일반 문제·종합평가·맞춤 문제 비동기 생성 API가 반환한 Job의 전체 진행 상태와 문항별 결과를 조회한다.

세 생성 화면이 공통으로 사용하는 polling API다. 새 Job을 생성하지 않으며 조회할 때마다 동일한 `jobId`를 사용한다.

## 인증·권한

- 인증 필요
- `TEACHER` 권한 필요
- 현재 교사가 생성한 Job만 조회할 수 있다.

## Request

### Path Variable

| 필드 | 타입 | 필수 | 역할 |
| --- | --- | --- | --- |
| `jobId` | long | 필수 | 비동기 생성 접수 응답에서 받은 Job ID |

Request Body는 없다.

## 진행 중 Response

```json
{
  "success": true,
  "data": {
    "jobId": 55,
    "status": "RUNNING",
    "totalCount": 3,
    "completedCount": 1,
    "slots": [
      {
        "slotIndex": 1,
        "itemId": 101,
        "sessionId": 195,
        "customStage": null,
        "sourceQuestionId": 2960,
        "originQuestionId": null,
        "status": "READY",
        "preview": {
          "sessionId": 195,
          "versionId": 227,
          "finalizedQuestionId": null,
          "snapshot": {
            "schemaVersion": 1,
            "metadata": {
              "questionType": "STEP_FILL",
              "presentation": "TEXT_ONLY",
              "difficulty": "low",
              "subUnitId": 13,
              "topicCode": null,
              "evaluationArea": "CALCULATION",
              "derivedFromQuestionId": null
            },
            "contentBlocks": [
              {
                "blockKey": "T1",
                "blockKind": "TEXT",
                "displayOrder": 0,
                "text": "다음 수의 최대공약수를 구하는 과정을 완성하세요.",
                "assetRef": null,
                "markup": null
              }
            ],
            "assets": [],
            "choices": [],
            "steps": [
              {
                "stepKey": "STEP_1",
                "displayOrder": 0,
                "label": "소인수분해",
                "segments": [
                  {
                    "type": "TEXT",
                    "text": "두 수를 각각 소인수분해하면 ",
                    "unitKey": null
                  },
                  {
                    "type": "BLANK",
                    "text": null,
                    "unitKey": "B1"
                  }
                ]
              }
            ],
            "answerUnits": [
              {
                "unitKey": "B1",
                "stepKey": "STEP_1",
                "displayOrder": 0,
                "answerRaw": "2^2 × 3",
                "answerNormalized": "2^2*3",
                "compareMethod": "EXACT",
                "diagnosticType": "EXECUTE",
                "displayUnit": null
              }
            ],
            "explanation": "공통인 소인수의 지수 중 작은 값을 선택합니다.",
            "learningGuide": {
              "conceptTitle": "최대공약수",
              "summary": "공통인 소인수로 최대공약수를 구할 수 있습니다.",
              "keyPoints": [
                "두 수를 소인수분해합니다.",
                "공통인 소인수의 작은 지수를 선택합니다."
              ]
            },
            "rubricItems": []
          }
        },
        "errorCode": null,
        "retryable": false
      },
      {
        "slotIndex": 2,
        "itemId": 102,
        "sessionId": 196,
        "customStage": null,
        "sourceQuestionId": null,
        "originQuestionId": null,
        "status": "VERIFYING",
        "preview": null,
        "errorCode": null,
        "retryable": false
      },
      {
        "slotIndex": 3,
        "itemId": 103,
        "sessionId": 197,
        "customStage": null,
        "sourceQuestionId": null,
        "originQuestionId": null,
        "status": "QUEUED",
        "preview": null,
        "errorCode": null,
        "retryable": false
      }
    ]
  },
  "error": null
}
```

## Job 상태

| 값 | 의미 | polling |
| --- | --- | --- |
| `QUEUED` | Job 접수 후 실행 대기 | 계속 |
| `RUNNING` | 하나 이상의 AI 생성 문항을 처리 중 | 계속 |
| `COMPLETED` | 모든 슬롯 성공 | 종료 |
| `PARTIALLY_FAILED` | 성공 슬롯과 실패 슬롯이 함께 존재 | 종료 |
| `FAILED` | 모든 슬롯 실패 | 종료 |

## Slot 표시 상태

| 값 | 의미 |
| --- | --- |
| `QUEUED` | 문항 처리 대기 |
| `GENERATING_CONTENT` | AI가 문항 내용을 생성 중 |
| `GENERATING_ASSET` | 문항 이미지 등 자산 생성 중 |
| `VALIDATING` | 구조·필드 검증 중 |
| `VERIFYING` | 문항 품질 검증 중 |
| `READY` | 검증을 통과한 현재 Version 사용 가능 |
| `FAILED` | 생성 또는 검증 최종 실패 |

현재 Job 조회 구현은 저장된 Item 상태를 `QUEUED`, `GENERATING_CONTENT`, `VERIFYING`, `READY`, `FAILED`로 변환한다. `GENERATING_ASSET`, `VALIDATING`은 화면 공통 상태 값으로 정의되어 있지만 현재 이 조회 경로에서는 직접 반환되지 않을 수 있다.

## Response 필드

| 필드 | 역할 |
| --- | --- |
| `jobId` | 생성 Job ID |
| `status` | Job 전체 집계 상태 |
| `totalCount` | 전체 슬롯 수 |
| `completedCount` | `READY` 또는 `FAILED`로 종료된 슬롯 수 |
| `slots` | 요청 순서대로 정렬된 문항별 상태 |
| `slots[].slotIndex` | 1부터 시작하는 화면 표시 순서 |
| `slots[].itemId` | Job 내부 문항 처리 항목 ID |
| `slots[].sessionId` | 생성·수정·최종 저장에 사용하는 저작 Session ID |
| `slots[].customStage` | 맞춤 문제 단계. `review`, `similar`, `advanced`; 일반·종합평가는 `null` |
| `slots[].sourceQuestionId` | 문제은행에서 그대로 재사용한 문항 ID. AI 생성이면 `null` |
| `slots[].originQuestionId` | 맞춤 AI 생성의 기준이 된 원본 오답 문항 ID. 없으면 `null` |
| `slots[].status` | 문항의 화면 표시 상태 |
| `slots[].preview` | `READY`일 때만 존재하는 현재 PASSED Snapshot |
| `slots[].errorCode` | 실패 원인을 나타내는 내부 상태 문자열. 성공·진행 중이면 `null` |
| `slots[].retryable` | 서버 정책상 재시도 여지가 있는지 여부 |

`retryable`은 현재 상태 정보이며, Problem 도메인에는 실패 슬롯을 직접 재시도하는 공개 HTTP API가 아직 없다.

## Snapshot 필드

| 필드 | 역할 |
| --- | --- |
| `schemaVersion` | Snapshot 계약 버전. 현재 `1` |
| `metadata.questionType` | `STEP_FILL`, `MULTIPLE_CHOICE`, `SHORT_INPUT`, `ESSAY` |
| `metadata.presentation` | 문항 표현 형식 |
| `metadata.difficulty` | `low`, `mid`, `high` |
| `metadata.subUnitId` | 소단원 ID |
| `metadata.topicCode` | 주제 코드. 없으면 `null` |
| `metadata.evaluationArea` | 평가 영역. 없으면 `null`일 수 있다. |
| `metadata.derivedFromQuestionId` | 파생 원본 문항 ID. 없으면 `null` |
| `contentBlocks` | 발문·그림·표 렌더링 블록 |
| `assets` | Snapshot 안의 이미지 논리 참조 |
| `choices` | 객관식 보기 |
| `steps` | `STEP_FILL` 풀이 단계 |
| `answerUnits` | 채점 가능한 최소 답안 단위 |
| `explanation` | 문항 해설 |
| `learningGuide` | 개념 제목·요약·핵심 내용 |
| `rubricItems` | 서술형 채점 기준 |

모든 목록 필드는 관련 데이터가 없을 때 `null` 대신 빈 배열을 사용한다.

### Snapshot 논리 키

| 영역 | 키 |
| --- | --- |
| 본문 블록 | `blockKey` |
| 이미지 | `assetKey` |
| 객관식 보기 | `choiceKey` |
| 풀이 단계 | `stepKey` |
| 답안 단위 | `unitKey` |
| 채점 기준 | `rubricKey` |

이 키들은 배열 index나 DB ID가 아니다. 같은 Session에서 Version이 변경되어도 같은 구성 요소를 식별하기 위한 논리 키이며 AI 편집의 `targetKey`로 사용한다.

## 실패가 포함된 종료 Response

```json
{
  "success": true,
  "data": {
    "jobId": 55,
    "status": "PARTIALLY_FAILED",
    "totalCount": 2,
    "completedCount": 2,
    "slots": [
      {
        "slotIndex": 1,
        "itemId": 101,
        "sessionId": 195,
        "customStage": null,
        "sourceQuestionId": 2960,
        "originQuestionId": null,
        "status": "READY",
        "preview": {
          "sessionId": 195,
          "versionId": 227,
          "finalizedQuestionId": null,
          "snapshot": {
            "schemaVersion": 1,
            "metadata": {
              "questionType": "SHORT_INPUT",
              "presentation": "TEXT_ONLY",
              "difficulty": "low",
              "subUnitId": 13,
              "topicCode": null,
              "evaluationArea": null,
              "derivedFromQuestionId": null
            },
            "contentBlocks": [
              {
                "blockKey": "T1",
                "blockKind": "TEXT",
                "displayOrder": 0,
                "text": "12의 약수를 하나 쓰세요.",
                "assetRef": null,
                "markup": null
              }
            ],
            "assets": [],
            "choices": [],
            "steps": [],
            "answerUnits": [
              {
                "unitKey": "MAIN",
                "stepKey": null,
                "displayOrder": 0,
                "answerRaw": "3",
                "answerNormalized": "3",
                "compareMethod": "EXACT",
                "diagnosticType": null,
                "displayUnit": null
              }
            ],
            "explanation": "12는 3으로 나누어떨어집니다.",
            "learningGuide": {
              "conceptTitle": "약수",
              "summary": "나누어떨어지게 하는 수가 약수입니다.",
              "keyPoints": ["나머지가 0인지 확인합니다."]
            },
            "rubricItems": []
          }
        },
        "errorCode": null,
        "retryable": false
      },
      {
        "slotIndex": 2,
        "itemId": 102,
        "sessionId": 196,
        "customStage": null,
        "sourceQuestionId": null,
        "originQuestionId": null,
        "status": "FAILED",
        "preview": null,
        "errorCode": "VERIFICATION_ERROR",
        "retryable": true
      }
    ]
  },
  "error": null
}
```

문항 실패는 HTTP 실패가 아니라 성공 응답 안의 Job·Slot 상태로 반환된다. 현재 Worker에서 주로 기록하는 값은 `GENERATION_FAILED`, `CANDIDATE_INVALID`, `FAILED`, `VERIFICATION_ERROR`다.

## 오류 응답

### Job 없음 또는 소유권 불일치

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "PROBLEM_GENERATION_JOB_NOT_FOUND",
    "message": "문항 생성 작업을 찾을 수 없습니다."
  }
}
```

## 프론트 처리 흐름

```text
생성 POST 응답에서 jobId 수신
→ 1초 정도의 간격으로 같은 jobId 조회
→ QUEUED/RUNNING이면 계속 조회
→ COMPLETED/PARTIALLY_FAILED/FAILED이면 조회 종료
→ READY 슬롯만 preview.snapshot 변환
→ PARTIALLY_FAILED 정책에 따라 성공 문항 유지 또는 전체 재요청 안내
```

페이지 이탈 또는 새 생성 요청 시작 시 이전 polling 요청을 중단해야 한다. polling 과정에서 생성 POST를 다시 호출하거나 새로운 `clientRequestId`를 만들면 안 된다.

---

# 문제 저작 Session 상태 조회

```http
GET /api/teacher/problems/authoring-sessions/{sessionId}/status
```

## 역할

한 문항의 생성·AI 수정·검증·최종 저장 상태를 조회한다.

Snapshot 본문은 반환하지 않고 Session 상태와 현재·대기 Version ID, 최종 저장 가능 여부만 반환한다.

## 인증·권한

- 인증 필요
- `TEACHER` 권한 필요
- 현재 교사가 소유한 Session만 조회할 수 있다.

## Request

### Path Variable

| 필드 | 타입 | 필수 | 역할 |
| --- | --- | --- | --- |
| `sessionId` | long | 필수 | 비동기 생성 결과의 `slots[].sessionId` |

Request Body는 없다.

## Response

```json
{
  "success": true,
  "data": {
    "sessionId": 195,
    "lifecycleStatus": "DRAFT",
    "operationStatus": "IDLE",
    "interactionStatus": "IDLE",
    "currentVersionId": 227,
    "pendingVersionId": null,
    "readyForFinalization": true,
    "finalizedQuestionId": null,
    "errorCode": null
  },
  "error": null
}
```

## 상태 값

### `lifecycleStatus`

| 값 | 의미 |
| --- | --- |
| `DRAFT` | 생성·수정 중인 임시 문항 |
| `FINALIZED` | 문제은행 문항으로 최종 저장됨 |
| `CANCELLED` | 최종화 전 취소됨 |
| `EXPIRED` | 유효 시간이 지나 만료됨 |

### `operationStatus`

| 값 | 의미 |
| --- | --- |
| `IDLE` | 실행 중인 생성·수정·검증 작업 없음 |
| `GENERATING` | 문항 생성 중 |
| `MODIFYING` | 확인된 수정 실행 중 |
| `VERIFYING` | 후보 Version 검증 중 |
| `FAILED` | 최근 생성 또는 수정 실행 실패 |

### `interactionStatus`

| 값 | 의미 |
| --- | --- |
| `IDLE` | AI 수정 대화가 열려 있지 않음 |
| `COLLECTING` | 수정 요구사항 수집 중 |
| `AWAITING_CONFIRMATION` | 교사의 최종 적용 확인을 기다리는 중 |

## Response 필드

| 필드 | 역할 |
| --- | --- |
| `sessionId` | 저작 Session ID |
| `lifecycleStatus` | Session 생명주기 상태 |
| `operationStatus` | 생성·수정·검증 실행 상태 |
| `interactionStatus` | AI 수정 대화 상태 |
| `currentVersionId` | 검증을 통과해 현재로 승격된 Version ID. 없으면 `null` |
| `pendingVersionId` | 검증 중인 후보 Version ID. 없으면 `null` |
| `readyForFinalization` | 현재 상태에서 문제은행 최종 저장이 가능한지 여부 |
| `finalizedQuestionId` | 최종 저장된 문제은행 문항 ID. DRAFT이면 `null` |
| `errorCode` | 최근 실행 실패 이유. 없으면 `null` |

## `readyForFinalization` 규칙

다음 조건을 모두 만족할 때만 `true`다.

- `lifecycleStatus == DRAFT`
- `operationStatus == IDLE`
- `interactionStatus == IDLE`
- `currentVersionId`가 존재함
- `pendingVersionId == null`
- 현재 Version이 검증 `PASSED` 상태임

AI 수정 실패로 `operationStatus == FAILED`가 되어도 기존 `currentVersionId`는 유지될 수 있다. 이때 기존 Snapshot은 조회할 수 있지만 `readyForFinalization`은 `false`다.

## 실패 상태 Response

```json
{
  "success": true,
  "data": {
    "sessionId": 195,
    "lifecycleStatus": "DRAFT",
    "operationStatus": "FAILED",
    "interactionStatus": "IDLE",
    "currentVersionId": 227,
    "pendingVersionId": null,
    "readyForFinalization": false,
    "finalizedQuestionId": null,
    "errorCode": "VERIFICATION_FAILED"
  },
  "error": null
}
```

`errorCode`는 `ApiResponse.error.code`와 다른 Session 내부 실행 상태 문자열이다.

## 오류 응답

### Session 없음 또는 소유권 불일치

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "PROBLEM_AUTHORING_SESSION_NOT_FOUND",
    "message": "문항 작성 세션을 찾을 수 없습니다."
  }
}
```

## 프론트 처리 흐름

```text
생성 결과의 sessionId 보존
→ AI 수정 적용 후 Session 상태 조회
→ operationStatus와 interactionStatus 확인
→ IDLE + readyForFinalization=true이면 preview 재조회
→ FAILED이면 errorCode 표시 및 기존 Version 유지
```

---

# 문제 저작 Snapshot 조회

```http
GET /api/teacher/problems/authoring-sessions/{sessionId}/preview
```

## 역할

Session에서 검증을 통과해 현재 Version으로 승격된 문제 Snapshot을 조회한다.

검증 중인 `pendingVersionId`의 후보를 반환하지 않으므로, AI 수정이 실패해도 기존 PASSED Snapshot이 유지된다.

## 인증·권한

- 인증 필요
- `TEACHER` 권한 필요
- 현재 교사가 소유한 Session만 조회할 수 있다.

## Request

### Path Variable

| 필드 | 타입 | 필수 | 역할 |
| --- | --- | --- | --- |
| `sessionId` | long | 필수 | 조회할 문제 저작 Session ID |

Request Body는 없다.

## Response

```json
{
  "success": true,
  "data": {
    "sessionId": 195,
    "versionId": 228,
    "finalizedQuestionId": null,
    "snapshot": {
      "schemaVersion": 1,
      "metadata": {
        "questionType": "MULTIPLE_CHOICE",
        "presentation": "TEXT_ONLY",
        "difficulty": "mid",
        "subUnitId": 13,
        "topicCode": null,
        "evaluationArea": "UNDERSTANDING",
        "derivedFromQuestionId": null
      },
      "contentBlocks": [
        {
          "blockKey": "T1",
          "blockKind": "TEXT",
          "displayOrder": 0,
          "text": "110의 약수인 것을 고르세요.",
          "assetRef": null,
          "markup": null
        }
      ],
      "assets": [],
      "choices": [
        {
          "choiceKey": "C1",
          "displayOrder": 0,
          "content": "5"
        },
        {
          "choiceKey": "C2",
          "displayOrder": 1,
          "content": "12"
        }
      ],
      "steps": [],
      "answerUnits": [
        {
          "unitKey": "MAIN",
          "stepKey": null,
          "displayOrder": 0,
          "answerRaw": "C1",
          "answerNormalized": "C1",
          "compareMethod": "CHOICE",
          "diagnosticType": null,
          "displayUnit": null
        }
      ],
      "explanation": "110은 5로 나누어떨어집니다.",
      "learningGuide": {
        "conceptTitle": "약수",
        "summary": "나누어떨어지게 하는 수가 약수입니다.",
        "keyPoints": [
          "나머지가 0인지 확인합니다."
        ]
      },
      "rubricItems": []
    }
  },
  "error": null
}
```

## Response 필드

| 필드 | 역할 |
| --- | --- |
| `sessionId` | 동일 문항의 생성·수정 흐름을 유지하는 Session ID |
| `versionId` | 현재 PASSED Snapshot의 Version ID |
| `finalizedQuestionId` | 문제은행 최종 저장 후 부여된 문항 ID. DRAFT이면 `null` |
| `snapshot` | 현재 화면에 반영할 `QuestionSnapshotV1` |

Snapshot의 세부 필드와 논리 키 규칙은 비동기 생성 Job 조회의 Snapshot 항목과 같다.

## 조회 규칙

- `currentVersionId`가 없는 생성 진행 중 Session은 조회할 수 없다.
- 현재 Version이 검증을 통과하지 않았다면 조회할 수 없다.
- AI 수정 완료 후 같은 `sessionId`로 다시 조회하면 새 `versionId`가 반환된다.
- 프론트는 수정 결과를 새 문항으로 추가하지 않고 같은 `sessionId`의 기존 화면 문항을 교체한다.
- 종합평가 배점과 화면 순서는 Snapshot 밖의 화면 상태이므로 새 Snapshot을 적용할 때 별도로 보존한다.

## 오류 응답

| HTTP | 코드 | 발생 조건 |
| --- | --- | --- |
| `404` | `PROBLEM_AUTHORING_SESSION_NOT_FOUND` | Session 없음 또는 소유권 불일치 |
| `404` | `PROBLEM_AUTHORING_VERSION_NOT_FOUND` | Session이 가리키는 Version을 찾을 수 없음 |
| `409` | `PROBLEM_AUTHORING_VERSION_NOT_VERIFIED` | 현재 PASSED Version이 준비되지 않음 |
| `500` | `PROBLEM_AUTHORING_DATA_INVALID` | 저장된 Snapshot을 해석할 수 없음 |

### 검증된 Version 미준비

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "PROBLEM_AUTHORING_VERSION_NOT_VERIFIED",
    "message": "검증을 통과한 문항만 사용할 수 있습니다."
  }
}
```

## 프론트 처리 흐름

```text
Job READY 또는 AI 수정 성공 확인
→ GET /preview
→ sessionId 일치 확인
→ 새 versionId와 snapshot 저장
→ Snapshot 논리 키를 유지한 채 화면 모델로 변환
```

---

# AI 에이전트 문제 수정

```http
POST /api/teacher/problems/authoring-sessions/{sessionId}/edit/turns
```

## 역할

일반 문제·종합평가 생성 결과에 대해 교사의 자연어 수정 요청 한 턴을 처리한다.

사용자 입력은 `AgentDispatcher → PROBLEM_EDIT` 경로를 거친다. AI는 수정 요청을 바로 적용하지 않고 수정 내용을 구조화한 뒤 교사의 확인을 요청한다. 교사가 확인 의사를 담은 다음 턴을 보내면 후보 Version 생성·검증을 수행하고, 검증을 통과한 경우에만 현재 Version을 교체한다.

## 인증·권한

- 인증 필요
- `TEACHER` 권한 필요
- 현재 교사가 소유한 Session만 수정할 수 있다.
- 검증을 통과한 `currentVersionId`가 있는 Session만 수정할 수 있다.

## 첫 수정 Request

```json
{
  "userInput": "첫 번째 풀이 단계를 더 쉽게 설명해 주세요.",
  "history": [],
  "selectedTarget": {
    "targetType": "STEP",
    "targetKey": "STEP_1"
  }
}
```

### Request Body 필드

| 필드 | 타입 | 필수 | 역할 |
| --- | --- | --- | --- |
| `userInput` | string | 필수 | 교사가 입력한 현재 턴의 수정 요청. 공백일 수 없다. |
| `history` | array | 선택 | 이전 사용자·AI 대화. 생략 또는 `null`이면 빈 배열로 처리한다. |
| `history[].role` | enum | 필수 | `USER` 또는 `ASSISTANT` |
| `history[].content` | string | 필수 | 해당 대화 턴의 내용 |
| `selectedTarget` | object | 선택 | 화면에서 교사가 선택한 수정 영역 |
| `selectedTarget.targetType` | enum | 조건부 | 수정 영역 유형 |
| `selectedTarget.targetKey` | string | 조건부 | 반복 가능한 영역의 Snapshot 논리 키 |

환영 문구, 로딩 안내, 버튼 안내 같은 UI 문장은 `history`에 포함하지 않는다.

## 수정 대상

| `targetType` | 대상 | `targetKey` |
| --- | --- | --- |
| `WHOLE_QUESTION` | 문제 전체 | `null` |
| `QUESTION_BODY` | 발문 전체 | `null` |
| `CONTENT_BLOCK` | 특정 본문 블록 | `blockKey` |
| `CHOICE` | 특정 객관식 보기 | `choiceKey` |
| `STEP` | 특정 풀이 단계 | `stepKey` |
| `ANSWER_UNIT` | 특정 답안 단위 | `unitKey` |
| `EXPLANATION` | 해설 | `null` |
| `LEARNING_GUIDE` | 개념 설명 | `null` |
| `RUBRIC_ITEM` | 특정 채점 기준 | `rubricKey` |
| `ASSET` | 특정 이미지 | `assetKey` |
| `QUESTION_TYPE` | 문항 유형 | `null` |
| `DIFFICULTY` | 난이도 | `null` |

반복 영역의 `targetKey`로 배열 index나 DB ID를 보내면 안 된다. 특히 답안 단위의 실제 키 필드명은 `answerUnitKey`가 아니라 `unitKey`다.

## 첫 수정 Response

```json
{
  "success": true,
  "data": {
    "action": "REQUEST_CONFIRMATION",
    "instructionDeltas": [
      {
        "targetType": "WHOLE_QUESTION",
        "targetKey": null,
        "changeNature": "SEMANTIC",
        "instruction": "문제에서 다루는 수를 432에서 110으로 변경하고, 110의 약수를 구하는 내용이 되도록 수정한다."
      }
    ],
    "semanticPatch": null,
    "assistantMessage": "문제의 수를 432에서 110으로 바꾸고, 110의 약수를 구하는 내용으로 수정할까요?",
    "preview": null
  },
  "error": null
}
```

### `action` 값

| 값 | 의미 | 프론트 처리 |
| --- | --- | --- |
| `CONTINUE_COLLECTION` | 수정 실행에 필요한 정보가 부족함 | AI 질문을 표시하고 추가 입력을 받음 |
| `REQUEST_CONFIRMATION` | 적용할 변경 사항이 준비됨 | 변경 요약과 적용·취소 버튼 표시 |
| `CONFIRM_EXECUTION` | 교사 확인 후 수정 실행 단계가 처리됨 | Session 상태와 preview를 재조회 |
| `CANCEL` | 수정 대화 취소 | 기존 Snapshot 유지, 편집 패널 초기화 |

### `instructionDeltas[].changeNature`

| 값 | 의미 |
| --- | --- |
| `PRESENTATIONAL` | 문구·표현·스타일 중심 변경 |
| `SEMANTIC` | 수치·조건·정답 등 의미 변경 |
| `STRUCTURAL` | 문항 구조·유형 등 구조 변경 |

## 교사 확인 Request

확인 전용 boolean 필드는 없다. 교사가 적용 버튼을 누르면 확인 의사를 나타내는 사용자 턴을 같은 API로 다시 전송한다.

```json
{
  "userInput": "변경 사항을 적용해 주세요.",
  "history": [
    {
      "role": "USER",
      "content": "문제의 수를 432에서 110으로 바꿔 주세요."
    },
    {
      "role": "ASSISTANT",
      "content": "문제의 수를 432에서 110으로 바꾸고, 110의 약수를 구하는 내용으로 수정할까요?"
    }
  ],
  "selectedTarget": {
    "targetType": "WHOLE_QUESTION",
    "targetKey": null
  }
}
```

## 수정 실행 Response

```json
{
  "success": true,
  "data": {
    "action": "CONFIRM_EXECUTION",
    "instructionDeltas": [],
    "semanticPatch": null,
    "assistantMessage": "변경 사항을 적용했습니다.",
    "preview": {
      "previewVersionId": 228,
      "mode": "PARAMETRIC_PATCH",
      "parameterChanges": [
        {
          "key": "TARGET_NUMBER",
          "oldValue": "432",
          "newValue": "110",
          "oldUnit": null,
          "newUnit": null
        }
      ],
      "impactedAreas": [
        "STEM",
        "ANSWERS",
        "EXPLANATION"
      ],
      "structuralChange": false,
      "revalidationRequired": true,
      "legacyFallback": false
    }
  },
  "error": null
}
```

수정 실행은 현재 구현에서 확인 턴의 HTTP 요청 안에서 수행된다. 응답을 받은 뒤에도 최종 성공 여부와 현재 승격 Version은 Session 상태 및 Snapshot 조회 API로 확인한다.

실행 경로에 따라 `preview`가 `null`일 수 있으므로 `CONFIRM_EXECUTION`만으로 새 Snapshot을 구성하지 않는다.

## Response 필드

| 필드 | 역할 |
| --- | --- |
| `action` | 현재 대화 턴의 처리 결과 |
| `instructionDeltas` | 이번 턴에서 새로 추출한 구조화 수정 지시 |
| `semanticPatch` | 의미 모델에 적용할 서버 생성 patch. 없으면 `null` |
| `assistantMessage` | 화면에 표시할 AI 응답 |
| `preview` | 확인 실행 결과의 정답 비노출 요약. 실행 전 또는 경로에 따라 `null` |
| `preview.previewVersionId` | 실행으로 생성된 후보 Version ID |
| `preview.mode` | 수정 실행 방식 |
| `preview.parameterChanges` | 변경된 의미 파라미터 목록 |
| `preview.impactedAreas` | 변경 영향을 받은 문항 영역 |
| `preview.structuralChange` | 구조 변경 포함 여부 |
| `preview.revalidationRequired` | 재검증 필요 여부 |
| `preview.legacyFallback` | 의미 모델 patch 대신 기존 AI 수정 경로를 사용했는지 여부 |

### `preview.mode`

| 값 | 의미 |
| --- | --- |
| `PRESENTATIONAL_PATCH` | 표현 중심 부분 수정 |
| `PARAMETRIC_PATCH` | 수치·단위 파라미터 수정 |
| `STRUCTURAL_REGENERATION` | 구조 변경을 포함한 재생성 |
| `RESTORE` | 이전 PASSED Version 복원 |
| `REJECTED` | 지원하지 않는 수정 |

### `preview.impactedAreas`

`STEM`, `CHOICES`, `STEPS`, `ANSWERS`, `EXPLANATION`, `LEARNING_GUIDE`, `RUBRICS`, `ASSETS` 중 하나 이상을 반환할 수 있다.

## `semanticPatch` 형식

Agent가 의미 기반 수정으로 해석한 경우 확인 응답에 다음 구조가 포함될 수 있다.

```json
{
  "schemaVersion": 1,
  "requestId": "7c834d59-b6ad-4fa8-bb51-acde9f9198dc",
  "baseVersionId": 227,
  "mode": "PARAMETRIC_PATCH",
  "operations": [
    {
      "type": "SET_PARAMETER_VALUE",
      "path": "/parameters/TARGET_NUMBER/value",
      "expectedOldValue": "432",
      "newValue": "110"
    }
  ],
  "assistantMessage": "432를 110으로 변경합니다."
}
```

`semanticPatch`는 서버가 생성하고 검증하는 응답 데이터다. 프론트가 이를 변경해 다음 요청에 다시 보내지 않는다.

## 수정 적용 규칙

- `REQUEST_CONFIRMATION` 단계에서는 현재 Snapshot을 변경하지 않는다.
- 확인된 수정만 후보 Version으로 생성한다.
- 구조·의미·품질 검증을 통과한 후보만 `currentVersionId`로 승격한다.
- 검증 실패 시 기존 PASSED Version을 유지한다.
- 새 Version이 생성되어도 `sessionId`는 변경하지 않는다.
- 적용 완료 후 `/status`에서 `operationStatus`, `currentVersionId`, `errorCode`를 확인한다.
- 성공한 경우 `/preview`를 다시 조회해 같은 `sessionId`의 화면 문항만 교체한다.

## 오류 응답

| HTTP | 코드 | 발생 조건 |
| --- | --- | --- |
| `400` | `INVALID_INPUT_VALUE` | `userInput`이 없거나 공백인 경우 |
| `400` | `AI_REQUEST_BLOCKED` | 공통 입력 가드레일이 요청을 차단한 경우 |
| `400` | `PROBLEM_SEMANTIC_EDIT_REJECTED` | 지원하지 않거나 교육과정 범위를 벗어난 의미 수정 |
| `404` | `PROBLEM_AUTHORING_SESSION_NOT_FOUND` | Session 없음 또는 소유권 불일치 |
| `404` | `PROBLEM_AUTHORING_VERSION_NOT_FOUND` | 기준 Version을 찾을 수 없음 |
| `409` | `PROBLEM_AUTHORING_VERSION_NOT_VERIFIED` | 현재 PASSED Version이 없음 |
| `409` | `PROBLEM_EDIT_COMMAND_STALE` | 확인하려는 수정 명령의 기준 Version이 현재와 다름 |
| `409` | `PROBLEM_SEMANTIC_MODEL_UNSUPPORTED` | 현재 문항의 의미 모델로 요청을 처리할 수 없음 |
| `422` | `PROBLEM_SEMANTIC_MODEL_INVALID` | 의미 모델 또는 의미 patch 검증 실패 |
| `422` | `PROBLEM_DIAGRAM_RENDER_FAILED` | 수정된 도형을 렌더링하지 못함 |
| `500` | `AI_RESPONSE_BLOCKED` | 공통 출력 가드레일이 AI 응답을 차단한 경우 |
| `500` | `AI_CLIENT_CALL_FAILED` | AI 호출 실패 |
| `500` | `AI_CLIENT_EMPTY_RESPONSE` | AI 응답 내용이 비어 있음 |
| `503` | `PROBLEM_AI_PORT_NOT_CONFIGURED` | 문제 수정 AI 처리기가 구성되지 않음 |

### stale 수정 명령

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "PROBLEM_EDIT_COMMAND_STALE",
    "message": "현재 문항과 일치하지 않는 수정 요청입니다."
  }
}
```

### 의미 모델 검증 실패

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "PROBLEM_SEMANTIC_MODEL_INVALID",
    "message": "문항 의미 모델이 올바르지 않습니다."
  }
}
```

> 현재 컨트롤러의 일부 Swagger 예시에는 `PROBLEM_EDIT_REJECTED`, `PROBLEM_EDIT_STALE_BASE`, `PROBLEM_SEMANTIC_VALIDATION_FAILED`라는 과거 이름이 남아 있다. 실제 공통 `ErrorCode`와 HTTP 응답은 각각 `PROBLEM_SEMANTIC_EDIT_REJECTED`, `PROBLEM_EDIT_COMMAND_STALE`, `PROBLEM_SEMANTIC_MODEL_INVALID`를 사용한다.

## 프론트 처리 흐름

```text
READY 문항의 sessionId·versionId·Snapshot 논리 키 보존
→ 사용자가 수정 영역 선택
→ userInput + history + selectedTarget 전송
→ CONTINUE_COLLECTION이면 추가 입력
→ REQUEST_CONFIRMATION이면 변경 요약과 적용·취소 버튼 표시
→ 적용 버튼에서 확인 사용자 턴 전송
→ CONFIRM_EXECUTION 응답
→ GET /status로 성공·실패와 currentVersionId 확인
→ 성공 시 GET /preview
→ 같은 sessionId 문항의 Snapshot과 versionId 교체
```

---

# 공통 인증 오류

## 인증 실패

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다."
  }
}
```

## 권한 부족

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "FORBIDDEN",
    "message": "접근 권한이 없습니다."
  }
}

```

# 전체 연동 흐름

```text
단원·출제 조건 또는 맞춤 제안 선택
→ 비동기 생성 POST 1회
→ 같은 jobId로 Job polling
→ READY 슬롯의 sessionId·versionId·snapshot 보존
→ 필요하면 AI 수정 대화
→ 교사 확인 후 수정 실행
→ Session 상태 확인
→ 새 current Version preview 조회
→ Worksheet 도메인의 저장 API에 sessionId 전달
→ Session FINALIZED 및 finalizedQuestionId 확인
```

문제은행 최종 저장과 보관함 생성은 Worksheet 도메인의 API이므로 이 문서의 Endpoint 명세 범위에는 포함하지 않는다.

## 교사 draft 이미지 preview

생성된 구조화 SVG는 S3 업로드 전에도 교사가 확인할 수 있다.

```text
GET /api/teacher/problems/authoring-sessions/{sessionId}/versions/{versionId}/assets/{assetKey}/preview
Authorization: Bearer <teacher access token>
```

응답의 `data.dataUrl`을 `<img src="dataUrl">`에 사용한다. preview는 해당 교사가 소유한
session이고 version 검증 상태가 `PASSED`이며 asset artifact가 `READY`인 경우에만 제공된다.
서버는 draft 파일의 경로, 크기, SVG 형식, SHA-256 checksum을 다시 확인하므로 S3 URL이 없어도
교사가 확정 전에 실제 생성 이미지를 확인할 수 있다. 응답에는 `Cache-Control: no-store`가 적용된다.

프론트 연동 순서는 `Job READY → assetKey별 preview 호출 → 모든 이미지 load 성공 → 확정 활성화`다.
브라우저 CSP가 `data:` 이미지를 차단하는 경우 preview 화면의 `img-src`에 `data:`를 허용하거나
data URL을 Blob URL로 변환해야 하며, 이미지 load 실패 시 확정 버튼은 계속 비활성화한다.
