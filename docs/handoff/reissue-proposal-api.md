# 맞춤 문제 재출제 제안 API — 문제 생성 담당 사용 안내

분석 도메인이 "이 학생에게 무엇을 몇 개, 어느 난이도로 내야 하는지"를 계산해 준다.
문제 생성 담당은 그 지시대로 **문항을 고르거나 만들면 된다.**

- 작성: 모수환 (analysis)
- 대상: 이하영 (problem)

---

## 1. 무엇을 주고 무엇을 안 주나

| 주는 것 | 안 주는 것 |
|---|---|
| 소단원별 동일·유사·응용 문항 수 | 실제 문항 선택 |
| 유사 문항의 목표 난이도 | 학습지 조립·저장·배포 |
| 동일 문항으로 다시 낼 문항 ID 후보 | LLM 호출 |
| 중복 출제를 막을 제외 문항 ID | 문항 검증 |
| 응용 문항 LLM 프롬프트에 넣을 누적 취약 분포 | |

**분석은 처방만 한다.** 응답을 받은 뒤의 모든 실행은 문제 생성 쪽 몫이다.

---

## 2. 호출

```
GET /api/teacher/analysis/assignments/{assignmentId}/students/{studentId}/reissue-proposal
```

| 파라미터 | 값 |
|---|---|
| `assignmentId` | **원본 학습지 배정 ID.** 맞춤 회차가 몇 번 쌓여도 이 값은 바뀌지 않는다 |
| `studentId` | 재출제 대상 학생 ID |

요청 바디 없음. 조회 전용이라 몇 번을 불러도 같은 결과가 나오고 부수 효과가 없다.

> `assignmentId`에 **직전 맞춤 회차의 배정 ID를 넣으면 안 된다.** 항상 맨 처음 학습지 배정 ID를
> 쓴다. 교사 화면이 이미 그 ID를 들고 있다(같은 화면의 `custom-learning-sessions`와 같은 값).

---

## 3. 응답 전체 모양

```json
{
  "subcategories": [
    {
      "subUnitId": 42,
      "subUnitName": "소인수분해",

      "guidance": {
        "status": "유사 5문항 중 3개 정답(60%)으로 유지 판정, 중 난이도를 이어갑니다.",
        "plan": "동일 1문항(최근 오답), 유사 5문항(중). 응용은 상 난이도가 아니라 내지 않습니다.",
        "weakness": "계산 영역·실행 단계가 약합니다."
      },

      "adaptive": {
        "currentDifficulty": "mid",
        "source": "judgement",
        "placementRate": null,
        "placementMixed": null,
        "customSessionCount": 1,
        "lastStatus": "WATCH"
      },

      "review": {
        "proposedCount": 1,
        "maxCount": 4,
        "candidateQuestionIds": [3101, 3105, 3110, 3122]
      },

      "similar": {
        "proposedCount": 5,
        "maxCount": 10,
        "difficulty": "mid",
        "referenceQuestions": [
          { "questionId": 3101, "incorrectCount": 3, "lastIncorrectAt": "2026-08-14T10:30:00+09:00" }
        ],
        "excludedQuestionIds": [3101, 3105, 3110, 3122, 4001]
      },

      "advanced": {
        "triggered": false,
        "proposedCount": 0,
        "maxCount": 0,
        "historicalIncorrectItemCount": 8,
        "incorrectSessionCount": 3,
        "primaryEvaluationArea": "CALCULATION",
        "primaryTargetStage": "EXECUTE",
        "evaluationAreaEvidence": [
          { "evaluationArea": "CALCULATION", "gradedItemCount": 10,
            "incorrectItemCount": 5, "incorrectRate": 50.00 }
        ],
        "diagnosticStageEvidence": [
          { "diagnosticType": "EXECUTE", "gradedUnitCount": 8,
            "incorrectUnitCount": 5, "incorrectRate": 62.50 }
        ]
      }
    }
  ]
}
```

`subcategories`는 **소단원별 배열**이다. 소단원이 3개면 세 벌을 각각 처리한다.

---

## 4. 먼저 알아야 할 제약 두 가지

이 둘을 어기면 학습지 저장 단계에서 막힌다.

### 4-1. 모든 문항이 `STEP_FILL`이어야 한다

맞춤 학습지는 `GENERAL_LEARNING`으로 저장되고, `WorksheetCommandService`가 전 문항이
`STEP_FILL`인지 검사한다. 아니면 `WORKSHEET_TYPE_MISMATCH`(400)로 막는다.

- `review.candidateQuestionIds`는 **이미 `STEP_FILL`만 걸러서** 준다 (분석 쪽에서 처리)
- `similar` 문항은 **직접 뽑는 쪽에서 `STEP_FILL`로 걸러야 한다**
- `advanced` 문항도 `STEP_FILL` 형태로 생성해야 한다

### 4-2. 단계 코드가 두 벌이다

저장 요청(`WorksheetCreateRequest.items[].customStage`)은 이 응답의 키와 이름이 다르다.

| 이 응답의 키 | 저장 요청 값 | DB `custom_stage` |
|---|---|---|
| `review` | `"retrace"` | `REVIEW` |
| `similar` | `"basic"` | `SIMILAR` |
| `advanced` | `"independent"` | `ADVANCED` |

---

## 5. `review` — 동일 문항

**틀렸던 문항을 그대로 다시 낸다.** 새로 찾거나 만들지 않는다.

```json
"review": { "proposedCount": 1, "maxCount": 4, "candidateQuestionIds": [3101, 3105, 3110, 3122] }
```

| 필드 | 쓰는 법 |
|---|---|
| `proposedCount` | 서버 제안 수. 교사가 조정한 최종 수를 쓰되, 없으면 이 값을 쓴다 |
| `maxCount` | 교사가 올릴 수 있는 상한. 후보가 모자라면 후보 수와 같다 |
| `candidateQuestionIds` | **최근 오답 순 후보.** 앞에서부터 필요한 만큼 취한다 |

```
questionId  = candidateQuestionIds 앞에서 N개
customStage = "retrace"
```

**주의**

- `candidateQuestionIds`는 확정 선택이 아니라 **우선순위가 매겨진 후보 목록**이다. 순서를 섞지 말고
  앞에서부터 쓴다 — 앞쪽이 더 최근에 틀린 문항이다
- `proposedCount`가 0이면 다시 낼 오답이 없다는 뜻이다. 이 단계를 건너뛴다
- 임의로 다른 문항으로 바꾸지 않는다. 바꾸면 "지난 시간에 놓친 문제"라는 전제가 깨진다

---

## 6. `similar` — 유사 문항

**문제 은행에서 같은 소단원·난이도의 다른 문항을 고른다.**

```json
"similar": {
  "proposedCount": 5, "maxCount": 10, "difficulty": "mid",
  "referenceQuestions": [ { "questionId": 3101, "incorrectCount": 3, "lastIncorrectAt": "..." } ],
  "excludedQuestionIds": [3101, 3105, 3110, 3122, 4001]
}
```

| 필드 | 쓰는 법 |
|---|---|
| `proposedCount` | 뽑을 문항 수 |
| `difficulty` | **목표 난이도.** `low`/`mid`/`high` → `problem_question.difficulty` 1/2/3 |
| `referenceQuestions` | 유사도 계산의 **기준**. 출제 후보가 아니다 |
| `excludedQuestionIds` | 학생이 이미 받은 문항. 여기서 제외한다 |

### 선택 순서

```
1. sub_unit_id = subUnitId
2. difficulty  = similar.difficulty
3. question_type = STEP_FILL          ← 4-1 제약
4. deleted_at IS NULL
5. excludedQuestionIds 에 없는 문항
6. referenceQuestions 와 유사도 계산
7. 유사도 높은 순으로 proposedCount 개
```

### `referenceQuestions` 가중치

`incorrectCount`가 큰 문항일수록 **반복해서 틀린 문항**이라 유사도 기준으로 더 중요하다.
같은 문항을 여러 회차에서 틀렸어도 배열에는 한 번만 나오고 `incorrectCount`가 올라간다.
배열은 이미 `incorrectCount` 내림차순으로 정렬돼 있다.

### 재고가 모자랄 때

`proposedCount`를 못 채워도 된다. **실제로 출제된 유사 문항 수가 다음 회차 판정의 기준(N)이 되고,**
3개 이하로 내려가면 판정 컷오프가 정답률에서 오답 개수 기준으로 자동 전환된다. 억지로 다른 난이도나
다른 소단원에서 채우면 판정이 망가지니 **모자란 채로 두는 편이 낫다.**

---

## 7. `advanced` — 응용 문항 (LLM 생성)

**특정 오답 하나를 변형하는 것이 아니라, 소단원의 누적 취약 분포를 바탕으로 새 문항을 만든다.**

```json
"advanced": {
  "triggered": false, "proposedCount": 0, "maxCount": 0,
  "historicalIncorrectItemCount": 8, "incorrectSessionCount": 3,
  "primaryEvaluationArea": "CALCULATION", "primaryTargetStage": "EXECUTE",
  "evaluationAreaEvidence": [...], "diagnosticStageEvidence": [...]
}
```

### 먼저 `triggered`를 본다

```
triggered = false  →  생성하지 않는다. 여기서 끝.
triggered = true   →  proposedCount(기본 0)만큼 생성한다.
```

`triggered`는 **직전 회차에서 상 난이도를 통과(CLEAR)했을 때만** 참이다.
응용은 취약한 학생에게 주는 보충이 아니라 **상 난이도를 통과한 학생에게 주는 보너스**다.

> **`proposedCount`는 `triggered`가 참이어도 기본 0이다.** 교사가 직접 올려야 나간다.
> `triggered=true`인데 `proposedCount=0`이면 **생성하지 않는 것이 정상**이다.

### 프롬프트에 넣을 값

| 필드 | 의미 |
|---|---|
| `primaryEvaluationArea` | 우선 반영할 평가 영역. `null`이면 특정 못 함 |
| `primaryTargetStage` | 우선 반영할 풀이 단계. `null`이면 특정 못 함 |
| `evaluationAreaEvidence` | 평가 영역별 **문항 단위** 채점·오답 분포 |
| `diagnosticStageEvidence` | 풀이 단계별 **답안 단위** 채점·오답 분포 |
| `historicalIncorrectItemCount` | 이 소단원에서 틀린 누적 문항 수 |
| `incorrectSessionCount` | 오답이 한 개 이상 난 회차 수 |

**전체 분포를 프롬프트에 넣되 `primary*`를 우선 반영한다.**

코드 값:

```
evaluationArea : UNDERSTANDING / CALCULATION / REASONING / PROBLEM_SOLVING
diagnosticType : INTERPRET / MODEL / EXECUTE / ANSWER
```

### 세 가지 함정

**1. 두 배열의 합계는 다르다.** 평가 영역은 문항 단위, 풀이 단계는 답안 단위다. 한 문항 안에 여러
답안 칸이 있어서 서로 맞지 않는 게 정상이다.

**2. `historicalIncorrectItemCount`와 배열의 오답 합이 다를 수 있다.**
`problem_question.evaluation_area`가 nullable이고 30번 데이터셋(시드 16,063건)은 전부 비어 있다.
즉 **미분류 문항의 오답은 `evaluationAreaEvidence`에 안 잡힌다.**
오답 **규모**는 반드시 `historicalIncorrectItemCount`로 읽는다. 배열만 더하면 실제보다 작게 나온다.

**3. `incorrectRate`가 `null`일 수 있다.** 채점된 문항이 0건이면 `null`이다. `0.0`이 아니다 —
`0.0`은 "다 맞혔다"는 뜻이라 뜻이 정반대다.

---

## 8. `adaptive` / `guidance` — 참고용

**생성 로직에 쓰지 않는다.**

- `adaptive` — 난이도가 지금 값이 된 경위. 로그·디버깅용 맥락이다.
  생성에 쓸 난이도는 `adaptive.currentDifficulty`가 아니라 **`similar.difficulty`** 를 쓴다
  (값은 같지만 `similar` 쪽이 정본이다)
- `guidance` — 교사 화면에 그대로 표시할 한국어 문장 3개. 서버가 규칙으로 만든 것이라 LLM
  프롬프트에 넣을 필요가 없다

---

## 9. 에러

| 코드 | 상태 | 언제 |
|---|---|---|
| `ANALYSIS_ASSIGNMENT_NOT_FOUND` | 404 | 배정이 없음 |
| `ANALYSIS_ASSIGNMENT_ACCESS_DENIED` | 403 | 로그인 교사가 학습지·반을 소유하지 않음 |
| `ANALYSIS_STUDENT_NOT_ASSIGNED` | 404 | 그 학생이 원본 학습지를 배정받지 않음 |
| `ANALYSIS_REISSUE_NOT_GRADED` | 400 | **원본 배정 채점이 끝나지 않음** |

마지막 것이 실제로 자주 걸린다. 채점 전에는 난이도를 정할 근거가 없어 조용히 기본값을 주지 않고
막는다. 채점 완료 후 다시 호출하면 된다.

---

## 10. 요약 체크리스트

- [ ] `assignmentId`에 **원본** 배정 ID를 넣었는가 (직전 회차 ID 아님)
- [ ] `review`는 `candidateQuestionIds` 앞에서부터 그대로 썼는가
- [ ] `similar`는 `STEP_FILL`로 걸렀는가
- [ ] `similar`에서 `excludedQuestionIds`를 제외했는가
- [ ] `referenceQuestions`를 출제 후보로 쓰지 않았는가 (유사도 기준 전용)
- [ ] `advanced`는 `triggered && proposedCount > 0` 일 때만 생성했는가
- [ ] 오답 규모를 `historicalIncorrectItemCount`로 읽었는가 (배열 합 아님)
- [ ] 저장 시 `customStage`를 `retrace` / `basic` / `independent`로 변환했는가

---

## 관련

- 엔드포인트 스펙: `/swagger-ui.html`
- 구현: `domain/analysis/reissue/`
- 응답 계약: `ReissueProposalResponse`
