# 조건부 시각 문항 생성 및 초안 미리보기 설계

**상태:** 전체 재검토 및 보완 완료, 사용자 구현 승인 대기

## 1. 문서 목적

이 문서는 문제 생성 과정에서 좌표 그래프·데이터 표·수직선·평면도형·입체도형을
결정적 SVG로 생성하는 조건과, 교사가 S3 최종 저장 전에 생성 이미지를 검토하는 흐름을
정의한다. 또한 이미지가 포함된 원본 문항으로 유사·응용 문제를 만들 때 시각 유형과
도식 의미를 보존하는 방법, 검색 인덱스 백필이 서비스에 주는 주기적 부하를 낮추는 방법을
함께 정의한다.

이번 작업은 이미지 생성 모델로 삽화를 만드는 작업이 아니다. 서버가 검증 가능한 구조화
도식 명세를 받아 안전한 SVG로 렌더링하는 문제 저작 기능이다.

## 2. 현재 상태와 문제점

### 2.1 기본 문제 생성 경로에서는 이미지가 생성되지 않는다

- `PROBLEM_SEMANTIC_AUTHORING_ENABLED`의 기본값은 `false`다.
- Legacy 문제 생성 프롬프트와 JSON Schema는 `assets=[]`를 강제한다.
- 비동기 생성에서도 문제은행 재사용분은 새 이미지를 만들지 않고, 부족한
  `AI_GENERATION` 슬롯만 새 후보를 생성한다.
- 의미 저작을 활성화하면 모델이 반환한 `diagrams[]`가 자산 계획으로 바뀌지만,
  현재는 이미지 필요 여부를 판정하는 서버 정책이 없다.

### 2.2 `visualRequired`가 선언만 되어 있다

`SemanticProblemIntent.visualRequired`는 구조화 출력에 존재하지만 다음 불변조건을 검사하지
않는다.

- `visualRequired=false`인데 diagram이 존재하는 경우
- `visualRequired=true`인데 diagram이 없는 경우
- 허용되지 않은 diagram 종류가 생성된 경우
- 발문이 이미지를 참조하지 않는데 자산이 생성된 경우
- 발문이 그래프·표를 참조하지만 자산이 없는 경우

### 2.3 실제 계산값이 SVG 렌더러에 전달되지 않는다

의미 모델 평가기는 매개변수와 계산 결과를 만들지만 자산 Adapter는 빈
`DiagramRenderContext`를 렌더러에 전달한다. 이 때문에 좌표 그래프는 기본 범위와 기본
계수를 사용하고, 데이터 표는 계산된 셀 값 대신 fallback 문자열이나 빈 문자열을 사용할 수
있다.

### 2.4 표 이미지의 화면 연결과 영속화 역할이 어긋난다

- 데이터 표도 SVG 자산을 생성하지만 content block에는 `assetRef` 대신 `markup=assetKey`가
  들어간다.
- 최종 `ProblemAsset`은 자산 계획의 역할을 사용하지 않고 모두 `FIGURE`로 저장된다.
- 의미 기반 Snapshot의 presentation이 diagram 존재 여부와 관계없이 `TEXT_ONLY`로 남는다.

### 2.5 교사가 최종 확정 전에 이미지를 볼 수 없다

생성 Job 상태 응답은 `sessionId`, `versionId`, Snapshot을 반환하지만 로컬 draft SVG의 조회
수단을 제공하지 않는다. 현재 화면에서 실제 이미지를 보려면 학습지 생성으로 문제를
최종화하고 S3 업로드까지 기다려야 한다. 이는 “문제와 이미지를 검토한 뒤 확정한다”는 교사
업무 순서와 반대다.

### 2.6 유사·응용 생성이 원본 시각 의미를 보존하지 못한다

- 검색 문서는 `text-only`, `figure`, `table` 수준만 기록한다.
- `figure` 안에서 좌표 그래프·수직선·평면도형·입체도형을 구분하지 않는다.
- Few-shot 직렬화는 원본의 `diagramKind`, render spec, asset alt text를 전달하지 않는다.
- ORIGIN 의미 보강에서 semantic model을 추출해도 실제 생성 프롬프트가 그 semantic model을
  사용하지 않는다.
- 원본 발문이 “다음 그림을 보고 올바른 것을 고르시오”이고 자산 메타데이터도 “그림”뿐이면
  어떤 도식을 그려야 하는지 판단할 근거가 없다.

### 2.7 검색 백필이 60초마다 반복된다

로컬에서 RAG와 인덱싱이 활성화되면 `ProblemSearchBackfillScheduler`가 60초마다 최대 20개
문항을 조회한다. 마지막에 도달하면 cursor를 0으로 되돌려 전체 문제은행을 다시 순회한다.
등록은 멱등이라 데이터 중복은 막지만 다음 문제가 있다.

- 불필요한 JPA 조회와 자식 테이블 조회가 계속 발생한다.
- local 프로파일의 Hibernate bind TRACE 때문에 문항 ID가 매분 로그에 출력된다.
- 애플리케이션 스케줄러 스레드와 DB 연결을 불필요하게 점유한다.
- 최종 승인된 신규 문항은 이미 개별 인덱싱 큐에 등록되므로 1분 전수 백필의 효용이 낮다.

### 2.8 기존 인덱싱 작업은 스키마가 바뀌어도 재실행되지 않는다

`problem_search_index_task.question_id`가 UNIQUE이고 idempotency key도 questionId만으로
만들어진다. 따라서 기존 문항에 `visual_kind`를 새로 색인하려 해도 이전 READY
작업이 새 PENDING 작업 등록을 막는다. 컬럼만 추가하면 기존 행은 영구적으로
`NONE`에 머물 수 있다. 또한 남아 있던 v1 재시도 작업이 v2 작업보다 늦게 완료되면
새 visual kind를 다시 `NONE`으로 덮는 schema downgrade 경쟁도 생길 수 있다.

### 2.9 시각 문항의 semantic 라우팅과 객관식 정답 재료화가 불완전하다

semantic 생성을 활성화하면 visual 자산이 필요하지 않은 문항까지 semantic
pipeline으로 이동할 수 있다. 또한 현재 `SemanticSnapshotFactory`는 `SHORT_INPUT`
정답만 만들고 `MULTIPLE_CHOICE`의 정답 choiceKey를 재료화하지 않는다.
이 상태로는 “다음 그래프를 보고 올바른 것을 고르시오” 유형이 구조 검증을
통과할 수 없다.

### 2.10 운영 컨테이너의 local draft 경로가 영속화되어 있지 않다

현재 draft root 기본값은 `/tmp/cen-edu-problem-drafts`이고 운영 compose의 backend에는
해당 경로를 보존하는 volume이 없다. 따라서 컨테이너 재생성·이미지 교체 사이에 교사가
검토 중인 draft와 S3 재시도 원본이 사라질 수 있다. S3를 preview의 선행조건에서 제거하더라도
local draft 자체의 수명주기가 배포 수명주기보다 짧으면 승인 흐름을 보장할 수 없다.

## 3. 목표

1. 문제를 푸는 데 시각 자료가 필수인 경우에만 SVG 자산을 만든다.
2. 운영에서는 좌표 그래프와 데이터 표만 허용한다.
3. 로컬 실험에서는 설정 allowlist로 5개 diagram family 전체를 테스트할 수 있다.
4. 문제·정답·해설과 동일한 계산값으로 SVG를 렌더링한다.
5. 교사는 최종 확정과 S3 저장 전에 인증된 API로 draft SVG를 본다.
6. 유사 문제는 원본의 시각 유형과 도식 구조를 유지하면서 값만 바꾼다.
7. 응용 문제는 원본 시각 유형을 기본적으로 유지하면서 조건·계산 단계를 어렵게 만든다.
8. 원본 시각 의미를 복원할 수 없으면 임의 이미지를 생성하지 않는다.
9. RAG 검색이 좌표 그래프·표·수직선·평면·입체도형을 구분할 수 있게 한다.
10. 검색 백필은 저빈도·제한된 batch로 실행하고 완료 후 전체 순회를 반복하지 않는다.
11. 시각 기능 활성화가 비시각·미지원 문항의 생성 pipeline을 의도치 않게 변경하지 않게 한다.
12. 기존 인덱싱 작업이 있는 문항도 visual index schema 승격 후 한 번 재색인할 수 있게 한다.
13. 운영 컨테이너 교체 후에도 미확정 draft와 S3 재시도 원본을 동일한 경로에서 읽을 수 있게 한다.

## 4. 범위

### 4.1 운영 허용 범위

- `COORDINATE_GRAPH`
- `DATA_TABLE`

초기 visual 생성을 허용할 문항 유형은 `MULTIPLE_CHOICE`, `SHORT_INPUT`으로
제한한다. `STEP_FILL`, `ESSAY`는 기존 Legacy 생성을 유지하고 visual mode를
`NONE`으로 정규화한다. 추후 해당 semantic Snapshot materialization이 완전히
검증되면 별도로 허용한다.

### 4.2 로컬 실험 허용 범위

- `NUMBER_LINE`
- `COORDINATE_GRAPH`
- `DATA_TABLE`
- `PLANE_GEOMETRY`
- `SOLID_GEOMETRY`

로컬 실험 허용은 운영 준비 완료를 의미하지 않는다. 운영 allowlist에는 family별 구조 검증,
브라우저 미리보기, 유사·응용 의미 보존, 골든 시나리오 검증을 통과한 종류만 추가한다.

### 4.3 이번 작업에서 제외하는 것

- 삽화, 사진, 캐릭터, 배경 이미지 생성
- 자유 형식 SVG를 LLM이 직접 출력하는 방식
- 보기별로 서로 다른 이미지를 생성하는 객관식
- 한 문항에 시각 자산을 두 개 이상 생성하는 방식
- 애니메이션 SVG, 외부 URL, script, foreignObject
- 이미지 OCR이나 기존 bitmap을 자동으로 벡터화하는 기능
- 수직선·평면·입체도형을 즉시 운영 allowlist에 넣는 것
- 여러 backend 인스턴스가 local draft를 공유하는 분산 저장 구조

## 5. 이미지 생성 조건

### 5.1 시각 필수 문항 정의

학생이 본문과 보기의 텍스트만 읽었을 때 정답을 유일하게 구할 수 없고, 자산에 표시된
값·위치·형태·관계를 읽어야만 풀 수 있는 문항을 시각 필수 문항으로 정의한다.

다음 조건을 모두 만족할 때만 이미지를 생성한다.

1. 발문이 “다음 좌표 그래프를 보고”, “다음 표를 이용하여”처럼 대상을 구체적으로 참조한다.
2. diagram kind가 현재 설정의 allowlist에 포함된다.
3. 한 문항의 diagram 수가 정확히 1개다.
4. 이미지가 표현하는 값이 의미 모델의 parameter/computation 결과와 연결된다.
5. text에 이미지의 모든 정보를 중복 기재하지 않는다.
6. diagram을 제거하면 정답이 계산 불가하거나 둘 이상으로 모호해진다.
7. 발문 block, asset reference, asset plan의 `assetKey`가 일치한다.
8. question type이 현재 visual 생성 지원 범위에 포함된다.

### 5.2 생성하지 않는 경우

- 장식 목적의 그림
- 본문에 모든 수치와 관계가 적혀 있어 이미지 없이 풀 수 있는 문제
- “그래프”, “표”, “그림”이라는 단어만 언급하고 실제 대상을 참조하지 않는 문제
- 허용되지 않은 diagram kind
- 보기마다 별도 이미지가 필요한 문제
- 원본의 시각 종류와 render spec을 복원할 수 없는 유사·응용 문제

### 5.3 서버 불변조건

`VisualGenerationPolicy`는 LLM 판단을 신뢰하지 않고 다음을 서버에서 강제한다.

```text
visual mode NONE
  → visualRequired=false
  → diagrams=[]

visual mode AUTO
  → visualRequired=false이면 diagrams=[]
  → visualRequired=true이면 allowlist diagram 정확히 1개

visual mode PRESERVE_ORIGIN
  → ORIGIN visual kind가 NONE이면 diagrams=[]
  → ORIGIN visual kind가 지원 대상이면 같은 kind의 diagram 정확히 1개
  → ORIGIN visual kind가 UNKNOWN 또는 미지원이면 생성 실패
```

불변조건 위반 시 이미지를 제거해 text-only 문제로 바꾸지 않는다. 후보를 실패시키고 기존
Worker 재생성 정책 안에서 새 후보를 받는다.

`VisualSnapshotConsistencyValidator`는 materialize 후의 Snapshot에서 아래를 추가로
강제한다.

- visualRequired인데 발문에 kind별 구체 대상(좌표 그래프, 표, 수직선, 평면도형,
  입체도형)이 없는 경우를 거절한다.
- visualRequired가 아닌데 발문이 시각 대상을 필수 정보처럼 참조하는 경우를 거절한다.
- Snapshot FIGURE block, `assetRef`, Snapshot asset, plan의 key·role·presentation을 대조한다.
- “다음 그림”처럼 kind를 알 수 없는 generic 발문만 있으면 거절한다.

이미지 정보가 본문에 전부 중복되는지, 이미지를 제거했을 때 정답이 모호해지는지는
문자열 규칙만으로 완전히 판정할 수 없으므로 자산 검증 LLM의 `UNNECESSARY`,
`TEXT_DUPLICATION`, `GENERIC_REFERENCE` 판정을 추가한다. 이 판정도 실패면 후보를
승격하지 않는다.

## 6. 생성 목적별 시각 정책

### 6.1 일반학습·종합평가 부족분

- `VisualGenerationMode.AUTO`를 사용한다.
- 모델은 교육과정과 문제 전략상 시각 자료가 필수인 경우에만 허용 family를 선택한다.
- 시각 자료를 선택했다면 발문·diagram·정답·해설을 하나의 semantic model로 출력한다.
- 문제은행에 충분한 문항이 있으면 은행 문제를 재사용하므로 새 이미지를 만들지 않는다.

### 6.2 맞춤 SIMILAR

- `VisualGenerationMode.PRESERVE_ORIGIN`을 사용한다.
- 틀린 ORIGIN이 `DATA_TABLE`이면 새 문제도 `DATA_TABLE`이다.
- ORIGIN이 `COORDINATE_GRAPH`이면 새 문제도 `COORDINATE_GRAPH`다.
- 표의 행·열 의미, 그래프의 함수 family와 풀이 전략은 유지하고 값만 새로 만든다.
- 원본 semantic model이 없으면 lazy extraction을 시도한다.
- extraction이 `UNSUPPORTED`, `INVALID_SOURCE`, `TECHNICAL_ERROR`이면 visual 문제를 Legacy
  text-only 생성으로 우회하지 않고 명시적으로 실패한다.

### 6.3 맞춤 APPLICATION

- 기본값은 `VisualGenerationMode.PRESERVE_ORIGIN`이다.
- visual kind는 유지하고 조건 수, 추론 단계, 계산 난이도를 높인다.
- 서로 다른 visual kind로 교체하는 것은 이번 작업에서 허용하지 않는다.
- 향후 교사가 유형 변경을 명시하는 기능이 생기면 별도 mode로 확장한다.

## 7. 시각 의미 정본

### 7.1 정본 우선순위

원본의 시각 의미는 다음 순서로 결정한다.

1. `ProblemQuestion.semanticModel.diagrams[]`
2. `ProblemAsset.renderSpec`
3. `SnapshotMetadata.presentation`과 구체적인 alt text
4. 위 정보가 불충분하면 `UNKNOWN_FIGURE`

`UNKNOWN_FIGURE`는 자동 재생성 가능한 종류가 아니다. 검색 결과 표시와 진단에는 사용할 수
있지만 `PRESERVE_ORIGIN` 생성의 근거로 사용할 수 없다.

### 7.2 시각 종류 값

검색·생성 경계에서 다음 값을 사용한다.

```text
NONE
UNKNOWN_FIGURE
NUMBER_LINE
COORDINATE_GRAPH
DATA_TABLE
PLANE_GEOMETRY
SOLID_GEOMETRY
```

### 7.3 Generic 발문 처리

“다음 그림을 보고 올바른 것을 고르시오”라는 문장 자체는 시각 의미를 제공하지 않는다.

- semantic model 또는 render spec이 있으면 그 구조를 사용한다.
- 구체적인 alt text만 있으면 semantic extraction 입력으로 사용할 수 있다.
- asset metadata가 “그림”처럼 일반적이면 자동 생성하지 않는다.
- 모델이 임의로 그래프나 도형을 선택하게 하지 않는다.

## 8. 의미 평가값과 SVG 렌더링

`DefaultProblemSemanticMaterializer`는 semantic model을 평가한 뒤 동일한 평가값을 Snapshot과
자산 계획에 모두 사용한다.

```text
ProblemSemanticModelV1
→ SemanticComputationEngine.evaluate()
→ resolved values
├─ SemanticSnapshotFactory
└─ SemanticAssetPlanFactory
   → AssetGenerationSpecification.resolvedValues
   → LocalDraftAssetProductionAdapter
   → DiagramRenderContext(resolvedValues)
   → deterministic SVG
```

Renderer는 기본값으로 누락된 값을 감추지 않는다. 필수 key가 없거나 숫자로 해석할 수 없으면
자산 생성을 실패시킨다. artifact에는 SVG checksum과 실제 viewport width/height를 기록한다.

`STRUCTURED_RENDER` 모드에서 diagram spec이 없으면 설명 문구만 담은 placeholder
SVG를 만들지 않고 자산 생성을 실패시킨다.

`MULTIPLE_CHOICE`는 각 choice의 `valueKey`를 resolved value로 변환한 뒤
`intent.targetKey`의 resolved value와 일치하는 choice가 정확히 하나인지 확인하고, 그
`choiceKey`를 `CompareMethod.CHOICE`의 정답으로 만든다. 0개나 2개 이상이면
후보를 실패시킨다.

## 9. Snapshot과 자산 연결

- 그래프와 표 모두 화면에서는 `SnapshotBlockKind.FIGURE`와 `assetRef`로 연결한다.
- `SnapshotBlockKind.TABLE`은 현재 계약상 HTML/문자열 `markup`용이므로 SVG 표에는 사용하지
  않는다. 표라는 업무 의미는 `AssetRole.TABLE`과 `PresentationType.WITH_TABLE`로 보존한다.
- 자산 역할은 그래프·수직선·도형이면 `AssetRole.FIGURE`, 표면 `AssetRole.TABLE`이다.
- metadata presentation은 그래프·수직선·도형이면 `WITH_FIGURE`, 표면 `WITH_TABLE`이다.
- `SnapshotAssetReference.assetKey`, content block `assetRef`, `GeneratedAssetPlan.assetKey`는 같아야
  한다.
- 최종 `ProblemAsset.role`은 자산 계획의 역할을 그대로 사용한다.

## 10. 초안 미리보기

### 10.1 API

```http
GET /api/teacher/problems/authoring-sessions/{sessionId}/versions/{versionId}/assets/{assetKey}/preview
```

응답은 공통 `ApiResponse<DraftAssetPreviewResponse>`를 사용한다.

```json
{
  "success": true,
  "data": {
    "assetKey": "F1",
    "contentType": "image/svg+xml",
    "dataUrl": "data:image/svg+xml;base64,...",
    "widthPx": 640,
    "heightPx": 240,
    "checksum": "..."
  },
  "error": null
}
```

Job 상태 응답의 preview에는 이미 `sessionId`, `versionId`, Snapshot의 `assets[].assetKey`가
있으므로 대용량 base64를 Job polling 응답에 포함하지 않는다. 프론트는 READY 슬롯의 자산마다
preview API를 한 번 호출하고 `dataUrl`을 `<img src>`에 사용한다.
프론트의 Content-Security-Policy가 있다면 preview 화면의 `img-src`에만 `data:`를 허용하거나,
base64를 `Blob` URL로 변환해 표시한다. 이미지 `load` 실패 시 확정 버튼은 활성화하지 않는다.

### 10.2 접근 조건

- `TEACHER` JWT가 필요하다.
- session의 `ownerTeacherId`가 로그인 교사와 같아야 한다.
- version이 해당 session 소속이어야 한다.
- version 검증 상태가 `PASSED`여야 한다.
- manifest artifact 상태가 `READY`여야 한다.
- draft 경로가 설정된 draft root 내부의 정규 파일이어야 한다.
- 파일 checksum이 manifest checksum과 같아야 한다.
- content type은 `image/svg+xml`만 허용한다.
- 응답 크기 상한을 적용한다.
- 실제 경로를 기준으로 draft root 밖이거나 symbolic link인 파일을 거절한다.
- 초안이 proxy·공용 브라우저 캐시에 남지 않도록 `Cache-Control: no-store`를 반환한다.

### 10.3 수명주기

```text
생성·검증 성공
→ local draft 보존
→ 교사 preview
→ 교사 확정
→ DB 최종화 및 S3 storage task 등록
→ S3 업로드 성공
→ local draft 삭제
```

교사가 확정하지 않은 draft는 기존 TTL 정리 정책으로 삭제한다. S3 업로드가 실패한 경우 기존
재시도와 실패 원본 보존 정책을 유지한다.

### 10.4 운영 저장 경로와 배포 제약

- draft를 생성·preview·정리·S3 업로드하는 모든 구현체는 하나의 typed 설정
  `app.problem-authoring.draft.root`를 사용한다.
- 공통 `ProblemDraftPathResolver`가 상대 경로 정규화, root containment, symbolic link와
  비정규 파일 거절을 담당하며 각 구현체가 별도 경로 판정 코드를 만들지 않는다.
- 로컬 기본값은 `/tmp/cen-edu-problem-drafts`로 유지한다.
- 운영은 `PROBLEM_DRAFT_ROOT=/var/lib/cen-edu/problem-drafts`를 사용하고 backend container에
  `problem-drafts` named volume을 같은 경로로 mount한다.
- 실행 이미지가 non-root `cenedu` 사용자로 동작하므로 Docker image 생성 시 mount 대상
  디렉터리를 미리 만들고 해당 사용자에게 소유권을 부여한다.
- named volume은 컨테이너 재생성·image tag 교체 중 draft를 보존하지만 TTL 정리 및 S3 업로드
  성공 후 삭제 정책은 그대로 적용된다.
- 현재 운영 compose는 backend 단일 인스턴스를 전제로 한다. 여러 인스턴스로 확장할 때는
  공유 파일시스템·draft 전용 object storage 또는 요청 affinity 중 하나를 먼저 설계해야 하며,
  인스턴스별 local volume 상태에서 scale-out하지 않는다.

이 저장소는 백엔드 전용이므로 프론트엔드 코드 수정은 이 작업의 직접 범위가
아니다. 대신 API 문서에 Job READY 후 preview API를 호출하고, 확정 버튼은
이미지 load 성공 후에만 활성화하는 프론트 연동 계약을 명시한다.

## 11. RAG와 원본 시각 유형 보존

### 11.1 검색 인덱스

`problem_search_index`에 `visual_kind`를 저장한다. 검색 문서에도 `[시각유형]` 레이블을 넣어
embedding이 시각 구조를 반영하게 한다.

인덱싱 command에 `indexSchemaVersion=2`를 추가하고 idempotency key를
`problem-search:v2:{questionId}`로 만든다. DB 작업 유일성도 `question_id` 단독에서
`(question_id, index_schema_version)`으로 바꾼다. 그래야 기존 v1 READY 작업을 보존하면서
모든 기존 문항을 v2로 한 번 재색인할 수 있다.

`problem_search_index` 본체에도 `index_schema_version`을 저장한다. upsert는 incoming
version이 현재 행보다 같거나 높을 때만 update한다. 따라서 배포 시 남아 있던 v1
PENDING/RETRY_WAIT 작업이 v2 이후 완료되어도 v2 `visual_kind`와 문서를 낮은 schema로
되돌리지 않는다. Worker는 READY metadata의 version이 command보다 높으면 embedding 호출 전
해당 낮은 version task를 `SKIPPED`로 끝내고, 조건부 upsert는 동시 실행 경쟁에 대한
최종 방어선으로 둔다.

- SIMILAR: origin visual kind가 알려져 있으면 같은 visual kind를 hard filter한다.
- APPLICATION: 이번 작업에서는 origin visual kind가 알려져 있으면 같은 visual kind를 hard
  filter한다.
- AUTO 생성: origin이 없으므로 visual kind hard filter를 사용하지 않는다.
- `UNKNOWN_FIGURE`는 visual 생성 참고 문제로 선택하지 않는다.

### 11.2 참고 문제 전달

`GenerationReference`는 이미 `semanticModel`을 담을 수 있다. Prompt serializer가 ORIGIN의
semantic model 또는 최소 visual descriptor를 실제 생성 입력에 포함하도록 변경한다.

- 정답 원문을 Few-shot에 추가하지 않는다.
- ORIGIN semantic model의 diagram 구조와 풀이 전략은 전달한다.
- EXAMPLE은 visual kind와 표현 요약을 전달하되 정답을 전달하지 않는다.
- `directCopyForbidden=true`를 유지해 숫자·문장·정답 복사를 막는다.

## 12. Family별 운영 승격 기준

### 12.1 좌표 그래프

- direct proportion과 inverse proportion의 축 범위·계수·함수 path가 실제 계산값을 사용한다.
- 점, 선분, 직선, 함수가 viewport를 벗어나지 않는다.
- 분모 0, 동일한 축 최소·최대, 0 이하 tick 간격을 거절한다.
- 발문과 그래프가 같은 함수 family를 참조한다.

### 12.2 데이터 표

- 행·열은 1~12 범위다.
- 셀 좌표 중복과 범위 이탈을 거절한다.
- 계산된 셀 값과 text fallback을 명확히 구분한다.
- 행·열 header와 셀 텍스트가 viewport를 넘지 않도록 길이 상한을 둔다.

### 12.3 수직선

- 실험 allowlist에서 생성한다.
- min < max, tick > 0, point와 interval 범위를 검증한다.
- 운영 승격 전 열린 점·닫힌 점·구간·화살표 브라우저 QA를 수행한다.

### 12.4 평면도형

- 실험 allowlist에서만 생성한다.
- 점 참조, 선분, 각, 다각형, 원, 호, 측정 대상의 존재 여부를 검증한다.
- 삼각형 부등식과 좌표 기반 길이·각도 일관성 검증이 필요하다.
- label 충돌과 잘못된 각 호 표시를 브라우저 QA한다.

### 12.5 입체도형

- 실험 allowlist에서만 생성한다.
- solid kind별 필수 치수와 금지 치수를 구분한다.
- 양수 치수, 원기둥·원뿔 반지름, 각기둥 polygon side 범위를 검증한다.
- 투영도는 실제 길이 비율을 정밀 묘사하는 도면이 아니라 문제 풀이용 schematic임을 유지한다.

## 13. 검색 백필 부하 정책

### 13.1 설정값

```yaml
app.problem.rag.indexing:
  backfill-initial-delay: 10m
  backfill-delay: 1h
  backfill-batch-size: 50
```

- 애플리케이션 기동 직후 다른 초기화와 경쟁하지 않도록 10분 뒤 처음 실행한다.
- 한 시간에 한 번만 실행한다.
- 한 번에 최대 50문항만 조회한다.
- 기존 indexing worker의 5초 주기는 실제 PENDING task 처리용이므로 유지한다.

### 13.2 cursor 정책

- batch가 비어도 cursor를 0으로 되돌리지 않는다.
- 마지막 문항 ID를 유지하고 다음 실행에서 더 큰 ID만 찾는다.
- 신규 최종화 문항은 기존 `enqueueFinalized()` 경로로 즉시 인덱싱 큐에 들어간다.
- 애플리케이션 재시작 후 cursor가 0에서 시작하더라도 회당 50개 제한과 멱등 키로 부하와 중복을
  제한한다.
- 다중 인스턴스의 완전한 분산 cursor는 이번 범위에서 추가하지 않는다.
- 백필이 등록하는 v2 작업은 기존 v1 task의 `question_id` 유일성에 막히지 않아야 한다.

### 13.3 로그 정책

- 운영은 기존처럼 Hibernate bind 로그를 끈다.
- local TRACE는 SQL 진단을 위해 유지하되, 백필 자체가 시간당 한 번만 실행되게 한다.
- 백필 실행 로그에는 cursor, scanned, enqueued, rejected, exhausted, elapsedMs만 남긴다.
- 문제 본문과 정답은 로그에 남기지 않는다.

## 14. 오류 처리

새 오류는 `global/common/ErrorCode`에 한 번만 추가하고 Controller가 상태 코드를 직접 만들지
않는다.

```text
PROBLEM_VISUAL_POLICY_VIOLATION
PROBLEM_VISUAL_SOURCE_UNSUPPORTED
PROBLEM_DRAFT_ASSET_NOT_FOUND
PROBLEM_DRAFT_ASSET_NOT_READY
PROBLEM_DRAFT_ASSET_INTEGRITY_FAILED
```

- 정책·구조 위반은 후보 생성 실패로 처리한다.
- preview 소유권 위반은 session/version not found와 같은 방식으로 외부에 상세 존재 여부를
  노출하지 않는다.
- 파일 무결성 실패는 500/내부 오류로 처리하고 파일 경로를 응답에 넣지 않는다.

## 15. 보안

- LLM이 raw SVG를 출력하지 않는다.
- SVG는 서버 renderer가 만들고 `SafeSvgSanitizer`를 통과한다.
- script, event handler, 외부 URL, javascript scheme, foreignObject를 허용하지 않는다.
- preview 응답에 로컬 파일 경로를 포함하지 않는다.
- preview API는 교사 소유권을 확인한다.
- base64 data URL만 반환하고 draft directory를 정적 파일 경로로 공개하지 않는다.
- 운영 draft named volume은 backend 서비스에만 mount하고 frontend·postgres에는 공유하지 않는다.
- 문제 원문, 정답, 사용자 입력 원문을 로그에 남기지 않는다.

## 16. 테스트 전략과 실행 순서

사용자 요청에 따라 이번 작업은 red-green 순서로 진행하지 않는다.

```text
전체 구현
→ 구현 코드 정적 검토
→ 테스트 코드 작성·보강
→ 관련 테스트 실행
→ 웹서비스 수동 시나리오 확인
→ 전체 bash gradlew build 1회
```

- 구현 중 `bash gradlew build`를 실행하지 않는다.
- 테스트 코드는 모든 구현 작업이 끝난 뒤 작성·수정한다.
- 관련 테스트가 실패하면 원인을 수정하고 관련 테스트만 다시 실행한다.
- 전체 `bash gradlew build`는 모든 관련 테스트와 수동 확인 준비가 끝난 마지막 단계에서 한 번만
  실행한다.
- 외부 OpenAI·S3 자격 증명이 필요한 수동 시나리오와 자격 증명 없이 재현 가능한 fake
  port 통합 테스트를 분리한다.

## 17. 완료 기준

1. text-only 문항은 자산 계획이 비어 있다.
2. 운영 설정에서 좌표 그래프와 표만 생성된다.
3. experimental 설정에서 5개 family가 모두 draft SVG를 만든다.
4. 실제 semantic 평가값이 그래프 계수·축 범위·표 셀에 표시된다.
5. 시각 정책 불일치 후보는 검증 전에 거절된다.
6. 교사는 S3 없이 PASSED draft 이미지를 조회한다.
7. 다른 교사의 draft는 조회할 수 없다.
8. 학습지 확정 후 기존 S3 업로드와 URL 조회가 유지된다.
9. SIMILAR과 APPLICATION은 알려진 origin visual kind를 유지한다.
10. generic figure의 의미를 복원할 수 없으면 visual 생성이 실패한다.
11. RAG 검색 인덱스와 query가 visual kind를 반영한다.
12. 백필은 기동 10분 뒤 시작하고 이후 한 시간 간격, 회당 50개로 제한된다.
13. 백필 완료 후 cursor가 0으로 돌아가지 않는다.
14. 전체 `bash gradlew build`를 마지막에 한 번 실행해 통과한다.
15. 객관식 시각 문항의 정답 choiceKey가 semantic target에서 유일하게 재료화된다.
16. visual feature가 켜진 상태에서도 미지원 문항 유형은 기존 Legacy 경로를 유지한다.
17. 기존 검색 task가 있는 문항도 v2 visual kind로 재색인된다.
18. preview는 symbolic link·root 이탈 파일을 거절하고 `Cache-Control: no-store`를 반환한다.
19. 운영 backend 컨테이너를 재생성해도 TTL 이내 draft preview와 S3 재시도 원본이 유지된다.
20. draft 생성·preview·정리·S3 worker가 동일한 typed draft root 설정을 사용한다.
21. 늦게 완료된 v1 인덱싱 작업이 v2 검색 문서와 visual kind를 덮어쓰지 않는다.
