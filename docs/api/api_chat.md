# 개념 챗봇 API (`/api/chat`)

학생이 문제를 푸는 중에 개념을 묻는 API. **서버가 대화를 저장하지 않는다.**

> ⚠️ **이 API 는 클라이언트가 계약을 지켜야만 동작한다.** 뒤의 「프론트가 해야 할 일」을 먼저 읽을 것.
> 두 가지(`history`·`currentConceptId`)를 되돌려주지 않으면 **오류 없이 기능만 조용히 사라진다.**

---

## 1. 요청

```
POST /api/chat
Authorization: Bearer {accessToken}
Content-Type: application/json
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `question` | string | ✅ | 학생 질문. 공백만 있으면 400 |
| `history` | array | — | 이전 대화. 없으면 빈 배열로 취급 |
| `history[].role` | string | ✅ | `user` \| `assistant` (대소문자 무관) |
| `history[].content` | string | ✅ | 발화 내용. 공백만 있으면 400 |
| `currentConceptId` | number | — | **직전 응답이 준 값을 그대로 되돌려준다** |
| `subUnitId` | number | — | 학생이 보고 있는 소단원. 없으면 소단원 개념 목록 주입을 건너뛴다 |

```json
{
  "question": "이해가 안 돼요",
  "history": [
    { "role": "user", "content": "이항이 뭐예요?" },
    { "role": "assistant", "content": "이항은 …" }
  ],
  "currentConceptId": 88
}
```

**서버가 거는 제한**

| 대상 | 규칙 | 처리 |
|---|---|---|
| `question` 길이 | `app.ai.guard.input.max-length` (현재 **500자**) | 초과 시 400 |
| `history` 개수 | **20개** | 초과분을 **오래된 쪽부터 버리고 200**. 거절하지 않는다 |
| `currentConceptId` | 존재하지 않는 id | **오류가 아니다.** 없는 것으로 치고 200 |
| `subUnitId` | 존재하지 않는 id | **오류가 아니다.** 소단원 목록 없이 200 |

`history` 를 자르는 이유: 대화를 오래 하면 반드시 상한을 넘는데, 그때 요청을 거절하면 학생 화면에서는 어느 순간부터 챗봇이 고장 난 것과 구별되지 않는다.

없는 id 를 오류로 만들지 않는 이유: 클라이언트가 **오래된 값을 들고 있을 수 있다.** 400 을 내면 대화가 그냥 끊긴 것으로 보이지만, 앵커를 버리고 키워드로 새로 찾으면 대화는 이어진다.

---

## 2. 응답

### 200

```json
{
  "success": true,
  "data": {
    "answer": "등식은 등호를 사용하여 …",
    "currentConceptId": 84
  },
  "error": null
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `answer` | string | 답변 텍스트 |
| `currentConceptId` | number \| null | **다음 요청에 그대로 실어 보낼 값. 화면에 쓰지 않는다.** 앵커가 잡히지 않은 턴이면 `null` |

> **`currentConceptId` 는 화면에 그릴 정보가 아니라 연속성 토큰이다.**
> 개념 이름·근거·개념 목록을 응답에 싣지 않는다 — 프론트가 렌더링하는 것은 말풍선 하나다.
> 이 값을 UI 에 노출하거나 해석하려 들면 안 된다. **받은 그대로 다음 요청에 돌려주기만 한다.**

### 400

```json
{
  "success": false,
  "data": null,
  "error": { "code": "CHAT_QUESTION_TOO_LONG", "message": "…" }
}
```

| 코드 | 상황 |
|---|---|
| `CHAT_QUESTION_BLANK` | 질문이 비었다 (서비스 계층 판정) |
| `CHAT_QUESTION_TOO_LONG` | 질문이 길이 상한을 넘었다 |
| `CHAT_HISTORY_INVALID` | `role` 이 `user`/`assistant` 가 아니거나 `content` 가 비었다 |
| `INVALID_INPUT_VALUE` | `question` 필드 자체가 없거나 공백 (Bean Validation 단계) |
| `AI_REQUEST_BLOCKED` | 입력 가드가 막았다 (역할 불일치·프롬프트 인젝션 등) |

> **프론트는 `error.code` 로 분기하고 `error.message` 를 그대로 노출하지 않는다.**
> 문구의 단일 출처는 프론트 `labels.js` 다. 서버 메시지는 로그·디버깅용이다.

### 401 / 403

토큰이 없거나 만료면 401. **`/api/chat` 은 `anyRequest().authenticated()` 에 걸려 있어 교사 토큰으로도 인증은 통과하지만**, 에이전트 역할 가드가 `SOLVE_CHAT` 을 학생에게만 허용하므로 교사가 부르면 400 `AI_REQUEST_BLOCKED` 이 나간다(403 이 아니다).

---

## 3. `subUnitId` 가 선택인 이유

프론트 `ConceptChatPanel` 은 우리 `chat_concept.sub_unit_id` 를 갖고 있지 않다(확인함). 있으면 추출 프롬프트에 그 소단원의 개념 이름 목록을 참고로 넣어 **DB 에 없는 이름을 지어내는 일이 줄어든다.** 없으면 그 도움 없이 진행할 뿐 동작은 한다.

---

## 4. 프론트가 해야 할 일

**이 목록이 지켜지지 않으면 붙여도 기능이 죽는다.** 백엔드에서는 확인할 방법이 없다 — 전부 200 으로 보인다.

| # | 항목 | 없으면 |
|---|---|---|
| 1 | **`messages` 를 `history` 로 실어 보낼 것** | 매 턴이 대화의 첫 마디로 보인다 → **하향 탐색 전체가 죽는다.** 첫 발화 가드가 상시 발동해 "어려워요" 가 아무 일도 하지 않는다 |
| 2 | **응답 `currentConceptId` 를 보관했다가 다음 요청에 넣을 것** | 되감김. 한 칸 내려간 앵커가 다음 턴에 대화 첫 개념으로 원위치한다 |
| 3 | `ConceptChatPanel` 에 `white-space: pre-wrap` | 답변의 칸 구조(정의 / 왜·어떻게 / 알아두면 좋은 것)가 한 덩어리로 뭉친다 |
| 4 | 말풍선에 `MathText` 적용 | 수식이 `$\angle AOB$` 처럼 날것으로 노출된다. `PracticeConceptView` 는 이미 쓰고 있다 |
| 5 | `src/mocks/conceptChat.js` → 실제 API 호출로 교체 | — |

**1번과 2번은 서로 다른 기능을 죽인다.** 1번이 없으면 "더 쉽게" 요청 자체가 안 먹고, 2번이 없으면 요청은 먹지만 매번 같은 자리에서 다시 내려간다.

### 최소 구현 모양

```js
const [messages, setMessages] = useState([]);
const [conceptId, setConceptId] = useState(null);   // 화면에 쓰지 않는다

async function ask(question) {
  const { data } = await post('/api/chat', {
    question,
    history: messages.map(m => ({ role: m.role, content: m.content })),
    currentConceptId: conceptId,
  });
  setConceptId(data.currentConceptId);              // 다음 요청에 그대로 돌려준다
  setMessages(prev => [...prev,
    { role: 'user', content: question },
    { role: 'assistant', content: data.answer },
  ]);
}
```
