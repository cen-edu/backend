# 해설 채팅 연결

## 요청

`POST /api/student/assignments/{assignmentStudentId}/result/items/{worksheetItemId}/chat`

학생 인증이 필요하다. assignmentStudentId는 학생 배정 ID이며 assignmentId가 아니다. worksheetItemId는 결과 조회 응답 items[].worksheetItemId다.

```json
{
  "question": "왜 이 보기가 정답인지 설명해줘",
  "history": [
    {"role": "user", "content": "풀이 두 번째 줄이 이해가 안 돼"},
    {"role": "assistant", "content": "어느 계산 부분이 어려운지 알려 주세요."}
  ]
}
```

본문에는 페이지 모드, 정답, 해설, 학생 ID를 넣지 않는다. 서버가 공개된 본인 결과에서 현재 문항의 본문·보기·풀이 단계·정답·학생 답안을 조회한다.

응답은 기존 ApiResponse<ChatResponse> 형식이며 `data.answer`가 답변 텍스트다. 해설은 특정 문항에 고정되므로 `data.currentConceptId`는 null이다.

## 프론트 연결

- 풀이 화면은 기존 POST /api/chat을 사용한다.
- 해설 화면은 위 경로를 사용하고, 선택 문항 변경 시 history를 비운다.
- history는 해당 문항의 대화만 보내며 서버는 최근 20개까지 사용한다.
- 기존 400 AI_REQUEST_BLOCKED 응답의 안내 문구를 보여준다. 409는 해설이 아직 공개되지 않았음을 안내한다.
- 이 작업 공간에는 프론트 프로젝트가 없으므로 화면의 HTTP 호출 변경은 별도로 필요하다.

## 동작과 한계

학생 권한 → 본인 배정/공개 여부 → 문항 소속 → Dispatcher 입력 가드 → 해설 정책 RAG → ReviewChatAgent → 출력 가드 순서다.

기존 공통 LlmClient와 Dispatcher를 재사용한다. 개념 검색용 ConceptChatEngine 대신 공개 해설 자료용 Agent를 등록하여 현재 문항 설명을 생성한다. 해당 문항의 공개 데이터만 넘기며 필기 이미지 URL과 전체 학습지는 넘기지 않는다. 이미지 자체를 읽는 기능은 없으므로 그림만으로 주어진 조건/필기는 설명이 제한될 수 있다.

기존 결과 조회를 재사용하므로 매 턴 학습지 전체 결과를 조립한 뒤 문항을 선택한다. 별도 단일 문항 조회 최적화는 하지 않았다. 실제 모델 정확도와 출력의 정책 준수는 측정 전이다.

## 직접 확인

빌드/테스트/실제 호출은 실행하지 않았다. ./gradlew.bat compileJava와 ./gradlew.bat test를 직접 실행한다. 공개된 본인 문항 성공, 타인 배정/없는 문항 404, 공개 전 409, 비학생 403, 동일 질문의 풀이 BLOCK/해설 ALLOW, 정책 공급자 장애 시 차단을 확인한다.
