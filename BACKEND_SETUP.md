# 백엔드 초기 환경 설정 계획

> 프론트엔드(React + Vite) 구현 범위를 기준으로 잡은 백엔드 초기 세팅 계획서.
> 팀원별 작업 영역 분리와 스프린트 0 착수 순서까지 포함한다.

## 0. 전제

| 항목 | 결정 |
|---|---|
| 언어/런타임 | Java 21 (LTS) |
| 프레임워크 | Spring Boot 3.5.x |
| 빌드 | Gradle (Groovy DSL), 단일 모듈 |
| DB | PostgreSQL 17 + pgvector (Docker) |
| 마이그레이션 | Flyway |
| 인증 | Spring Security + JWT (TEACHER / STUDENT role) |
| LLM | Anthropic 공식 Java SDK (`com.anthropic:anthropic-java`), 모델 `claude-opus-5` |
| 리포지토리 | 프론트와 분리된 `backend` 신규 리포 |

패키지 루트는 `com.senny.backend` 로 제안한다(프론트 `senny-chatbot` 자산에서 따옴). 팀에서 정한 이름이 있으면 교체.

### 팀 구성과 역할

| 팀원 | 역할 |
|---|---|
| 이동규 | 프론트엔드, 프롬프트 라우터, QA |
| 모수환 | 학생 취약점 분석 기능, 인프라 |
| 이하영 | 문제 은행 데이터 수집·전처리, 관련 테이블 설계, 문제 생성·수정 에이전트 |
| 배세빈 | 문제 은행 데이터 전처리, 백엔드 공통 영역, 개념 챗봇, 답안 검증·서술형 채점 에이전트 |

---

## 1. 의존성 세트 (Spring Initializr 기준)

**공통 필수**

- Spring Web, Validation, Spring Data JPA, Spring Security
- PostgreSQL Driver, Flyway Migration
- Lombok, Spring Boot DevTools, Actuator

**추가**

- `io.jsonwebtoken:jjwt` — JWT
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` — Swagger UI (프론트 연동 계약서 역할)
- `com.anthropic:anthropic-java` — LLM 호출
- `org.testcontainers:postgresql` — 통합 테스트 (pgvector 이미지 그대로 사용)

> 초기엔 Redis를 넣지 않는다. refresh token은 DB 테이블로 시작하고, 세션/캐시 필요가 실제로 생기면 그때 추가.

---

## 2. 패키지 구조 = 팀원 작업 영역

단일 모듈 안에서 **패키지가 곧 소유 경계**다. 아래 owner를 그대로 `.github/CODEOWNERS`에 옮기면 PR 리뷰어가 자동 지정된다.

```
com.senny.backend
├── global/                     # 배세빈 (공통 영역)
│   ├── config/                 #   Web, Jpa, Swagger, Cors 설정
│   ├── security/               #   JwtProvider, Filter, SecurityConfig, @LoginUser
│   ├── common/                 #   ApiResponse, ErrorCode, GlobalExceptionHandler, BaseTimeEntity
│   └── util/
├── ai/                         # 이동규(디스패처·가드레일) + 각 에이전트 담당자
│   ├── dispatcher/             #   AgentDispatcher: 모든 에이전트 호출의 유일한 진입점     [이동규]
│   ├── guard/
│   │   ├── input/              #     공통 입력 가드레일 (역할·인젝션·범위·개인정보)        [이동규]
│   │   └── output/             #     출력 검증 인터페이스 (구현은 각 에이전트 경로)        [이동규]
│   ├── prompt/                 #   프롬프트 템플릿 버전 관리 (리소스 파일 + 버전 태그)      [이동규]
│   ├── client/                 #   AnthropicClient 래퍼, 재시도, 토큰 사용량 로깅
│   ├── embedding/              #   임베딩 클라이언트 (Anthropic 아님 — 3절 참고)
│   └── agent/                  #   AgentRequest/AgentResponse, 에이전트 인터페이스
├── domain/
│   ├── auth/                   # 배세빈   로그인, 토큰 재발급, 비밀번호 변경
│   ├── member/                 # 배세빈   교사·학생·반(class), 명단 등록
│   ├── curriculum/             # 이하영   학년>과목>학기>대>중>소 단원 트리, 개념
│   ├── problem/                # 이하영   문제 은행, 문제 생성·수정 에이전트
│   ├── worksheet/              # 배세빈   학습지(일반/종합평가/맞춤), 배정
│   ├── submission/             # 배세빈   풀이 제출, 필기 획 데이터 → 이미지
│   ├── grading/                # 배세빈   자동 채점, 답안 검증·서술형 채점 에이전트
│   ├── analysis/               # 모수환   취약점 분석, 맞춤 학습 처방
│   ├── dashboard/              # 모수환   반×학기 집계
│   └── chat/                   # 배세빈   개념 챗봇 (RAG)
└── infra/
    ├── vector/                 # 모수환   pgvector 리포지토리, 유사도 검색
    └── storage/                # 모수환   필기 이미지 저장 (로컬 → MinIO/S3)
```

각 도메인 패키지 내부는 `controller / service / repository / entity / dto` 로 통일한다.

### 교차 도메인 규칙 (초기에 못 박아야 나중에 안 터짐)

1. 다른 도메인의 **엔티티를 직접 참조하지 않는다.** FK는 두되 JPA 연관관계 대신 ID(`Long studentId`)로 들고, 필요한 데이터는 상대 도메인의 `...Service` public 메서드로 조회한다.
2. 도메인 간 호출은 **service 레이어만** 허용. 남의 `repository`를 직접 호출 금지.
3. 여러 도메인이 함께 쓰는 상수·라벨(난이도 `low|mid|high`, 진행 상태 `not-started|in-progress|submitted`, 문항 유형 `choice|short|essay`)은 `global/common/enums`에 **enum으로 한 번만** 선언한다. 프론트 `src/mocks/labels.js`가 단일 기준이므로 값 문자열을 그대로 맞춘다.
4. **`ai/client`의 Anthropic 클라이언트를 도메인 패키지에서 직접 호출하지 않는다.** LLM 호출은 예외 없이 `AgentDispatcher`를 거친다 (3-4절). 이 규칙은 ArchUnit 테스트로 CI에서 강제한다.

---

## 3. AI 연동 레이어 — 먼저 정해야 할 것

### 3-1. 모델과 호출 방식

- 기본 모델은 전 에이전트 `claude-opus-5`. 비용 조절은 모델 다운그레이드가 아니라 **effort 단계**(`low`~`max`)로 한다. 라우터가 이 값을 관리한다.
- 추론이 필요한 작업(문제 생성, 서술형 채점)은 **adaptive thinking** 사용.
- 출력이 긴 작업(문제 생성)은 **스트리밍 호출**. 비스트리밍으로 큰 `max_tokens`를 쓰면 HTTP 타임아웃에 걸린다.
- **개념 챗봇은 스트리밍하지 않는다.** 응답 전체를 받아 정답 누설 검증(3-4절)을 통과시킨 뒤 한 번에 내려준다. 개념 설명은 답변이 길지 않아 대기가 1~2초 수준이고, 잘못 나간 답을 회수하는 것보다 낫다. 프론트는 `ConceptChatPanel`에 "생각 중…" 상태를 표시한다.
- API 키는 `ANTHROPIC_API_KEY` 환경변수로만 주입. `application.yml`이나 리포에 절대 커밋하지 않는다.

### 3-2. 임베딩 모델은 별도 결정 필요 (선행 결정 항목)

Anthropic은 임베딩 API를 제공하지 않는다. pgvector에 넣을 벡터를 만들 모델을 따로 골라야 하고, **초기에 정해야 한다 — 나중에 바꾸면 전체 재색인**이다.

| 선택지 | 차원 | 비고 |
|---|---|---|
| BGE-m3 자체 호스팅 (Docker) | 1024 | 한국어 강함, API 비용 0, 컨테이너 1개 추가 |
| Voyage AI `voyage-3` | 1024 | 관리 부담 없음, 외부 의존·비용 발생 |
| OpenAI `text-embedding-3-large` | 3072 | 차원 큼, 저장 비용 증가 |

**추천: BGE-m3 자체 호스팅.** 중학 수학 개념·문제 텍스트가 전부 한국어이고, docker-compose에 컨테이너 하나 더 얹는 수준이라 인프라 담당(모수환)이 초기에 같이 세우면 된다. 앞의 두 선택지 모두 1024차원이라 `vector(1024)`로 스키마를 잡아두면 교체해도 스키마는 유지된다.

### 3-3. 에이전트 4종과 담당

| 에이전트 | 담당 | 트리거 | 입력 → 출력 |
|---|---|---|---|
| 문제 생성·수정 | 이하영 | 교사 자연어 | 단원+난이도+문항수 → `steps[].segments[]` 구조 JSON |
| 개념 챗봇 | 배세빈 | 학생 자연어 | 질문 + RAG(개념 청크) → 답변 (비스트리밍) |
| 답안 검증 | 배세빈 | 시스템 (제출 이벤트) | 필기 이미지 → 인식 텍스트 + 정오 판정 |
| 서술형 채점 | 배세빈 | 시스템 (채점 요청) | 답안 + 채점 기준 → 점수 + 근거 |

에이전트 출력이 프론트 데이터 구조와 정확히 맞아야 하므로 **structured outputs(JSON 스키마 강제)** 를 기본으로 쓴다. 프롬프트로 "JSON만 출력하세요"라고 시키고 파싱 재시도하는 방식은 쓰지 않는다.

필기 답안은 프론트가 IndexedDB에 획 좌표를 저장하고, `AGENTS.md`에 "교사 채점 화면은 서버가 만들어 준 `answerImage`를 사용한다"고 명시돼 있다. 즉 **획 데이터 JSON 업로드 → 서버에서 PNG 렌더링 → 스토리지 저장 → 이미지로 인식** 파이프라인이 필요하다. 배세빈 영역의 초기 설계 항목으로 잡는다.

### 3-4. AgentDispatcher (이동규)

#### 존재 이유

> 챗봇이 N개로 늘어날 때 가드레일을 N번 구현하면 **N개의 서로 다른 안전 수준**이 생긴다. 디스패처는 공통 정책을 단일 지점에서 집행해 이 편차를 없애고, 새 챗봇을 추가할 때 안전성이 기본값으로 따라오게 만든다. (에이전트별 고유 정책은 각자 유지)

공유 유틸 모듈로도 재사용은 되지만 **안 부르면 그만**이다. 새 에이전트를 만드는 사람이 호출을 빠뜨리면 그 에이전트만 무방비가 되고 아무도 모른다. **공유 모듈은 규칙을 제공하고, 디스패처는 규칙을 집행한다.** 이 논리가 성립하려면 아래 제약이 반드시 지켜져야 한다.

> **제약: 에이전트를 디스패처 없이 직접 호출하는 경로가 하나도 없어야 한다.**
> 자연어 입력이 없는 시스템 트리거 요청(답안 검증·서술형 채점)도 예외가 아니다. 예외 경로를 하나 열어두면 그 경로만 무방비가 되고, 나중에 누가 거기에 사용자 입력을 얹으면 원칙이 무너진다. 대신 **요청 타입에 따라 실행되는 검증 항목이 다르다** (아래 표).
> 강제 수단: 도메인 패키지에서 `ai/client` 직접 참조를 금지하는 ArchUnit 테스트를 CI에 넣는다 (2절 교차 도메인 규칙 4).

#### 흐름

```
사용자 ─ 프롬프트 ─▶ [AgentDispatcher] ─▶ 대상 에이전트 ─▶ [출력 검증] ─▶ 사용자
                       입력 가드레일(공통)                    경로별(고유)
```

#### 입력 가드레일 — 공통 (디스패처)

| 검증 | 내용 | 자연어 요청 | 시스템 요청 |
|---|---|---|---|
| 역할 | 교사인지 학생인지 → 허용 범위 결정 | ✅ | ✅ |
| 유효성 | 프롬프트 인젝션, 욕설, 서비스 범위 이탈, 개인정보 | ✅ | — |
| 의도 분류 | 문제 생성 / 개념 설명 중 어디로 보낼지 | ✅ | — (타입 지정됨) |
| 스키마 | 요청 페이로드 구조 검증 | — | ✅ |

#### 출력 검증 — 고유 (각 에이전트 경로)

| 경로 | 검증 대상 |
|---|---|
| 개념 설명 | 정답 누설 (아래 3층 구조) |
| 문제 생성 | 출력 JSON 스키마 (`steps[].segments[]` 구조 준수) |
| 답안 검증 / 서술형 채점 | 점수 범위·필수 필드 |

**God object 방지:** 특정 에이전트만 아는 도메인 규칙(예: "교사는 타 학급 문제 수정 불가")을 디스패처에 몰아넣지 않는다. 전부 디스패처가 알아야 하면 챗봇 하나 추가할 때마다 디스패처를 고쳐야 해서 원래 장점이 사라진다. 디스패처는 `OutputValidator` 인터페이스만 알고, 구현은 각 에이전트 패키지가 등록한다.

#### 정답 누설 방지 3층 (개념 설명 전용)

| 층 | 방법 | 비용 |
|---|---|---|
| 1층 · 미제공 | 에이전트 컨텍스트에 **정답을 아예 넣지 않는다.** 개념 설명은 문제 지문만 있으면 된다 | 0 |
| 2층 · 규칙 검사 | 객관식 → 정답 번호/보기 문구 단정적 등장 여부, 단답·수치 → 정규화 후 문자열·숫자 매칭 | ≈0 |
| 3층 · LLM 판정 | 서술형만. `{ leaked: boolean, reason: string }` 구조화 출력 | 높음 |

1층만으로는 부족하다. **정답을 안 줘도 LLM이 스스로 계산해서 말해버리기 때문**이다. 그래서 2·3층이 필요하다.

> **중요한 구조 전제 — 검증기는 정답을 알아야 한다.** 응답에 정답이 들어있는지 판단하려면 정답을 갖고 비교해야 한다. 즉 학생 챗봇 세션이 어떤 문항에 붙어있는지(`problemId` → 정답)를 **검증기에만** 넘긴다. **에이전트는 정답을 모르고, 검증기만 아는 형태.**

대부분 2층에서 걸린다. LLM judge는 서술형에만 쓴다.

#### 차단 시 동작

1. 더 강한 지시를 추가해 **재생성 1회**
2. 또 걸리면 정형 문구로 대체 — 예: "이건 직접 풀어보는 게 좋겠어요. 대신 힌트를 줄게요…"
3. 차단 이벤트를 로깅 (5절 `guard_block_log`). 교사 대시보드의 "이 학생이 답을 계속 물어봄" 신호로 재활용 가능 → 모수환 분석 도메인과 연결

#### 알려진 한계 (문서에 남겨둘 것)

1. **입력 가드레일은 입력만 막는다.** 환각·형식 붕괴 같은 출력 사고는 각 경로의 출력 검증이 담당한다. 두 층이 다 있어야 "일관된 안정성"이 완성된다.
2. **멀티턴 우회에 약하다.** 매 턴 마지막 발화만 보면 여러 턴에 걸쳐 조금씩 유도하는 공격은 통과한다. → 대화 히스토리를 몇 턴까지 검증에 넘길지 초반에 정한다 (10절 결정 항목).

---

## 4. Docker 개발 환경 (모수환)

`docker/compose.yml`:

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    container_name: senny-postgres
    environment:
      POSTGRES_DB: senny
      POSTGRES_USER: senny
      POSTGRES_PASSWORD: senny_local
      TZ: Asia/Seoul
    ports: ["5432:5432"]
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U senny -d senny"]
      interval: 5s
      retries: 10

  embedding:                      # BGE-m3 선택 시
    image: ghcr.io/huggingface/text-embeddings-inference:cpu-latest
    command: ["--model-id", "BAAI/bge-m3"]
    ports: ["8081:80"]
    volumes: ["embedding-cache:/data"]

volumes:
  postgres-data:
  embedding-cache:
```

- `CREATE EXTENSION vector`는 init 스크립트가 아니라 **Flyway `V1__init.sql` 첫 줄**에 둔다. 컨테이너를 지웠다 다시 띄워도 항상 재현되고, 운영 DB에도 같은 경로로 적용된다.
- 로컬 실행 명령을 README 맨 위에 고정한다.

```bash
docker compose -f docker/compose.yml up -d
```

- 앱 실행은 `local` 프로파일 기본. `application-local.yml`은 커밋하고, 비밀값은 `.env` + 환경변수로 분리한다. `.env.example`을 커밋해 필요한 키 목록만 공유한다.

---

## 5. DB 스키마와 마이그레이션 규칙

**Flyway 버전 충돌이 초기 팀 작업에서 가장 자주 터진다.** 순번(`V1`, `V2`)을 쓰면 두 사람이 동시에 `V5`를 만들어 머지가 깨진다. 타임스탬프 버전으로 간다.

```
V20260810_1430__problem_create_tables.sql        (이하영)
V20260810_1615__analysis_add_weakness_score.sql  (모수환)
```

규칙:

- 파일명 = `V{yyyyMMdd_HHmm}__{도메인}_{설명}.sql`
- **적용된 마이그레이션 파일은 절대 수정하지 않는다.** 되돌릴 땐 새 파일을 추가.
- `spring.jpa.hibernate.ddl-auto: validate` 고정. `update`/`create` 금지.
- 테이블 접두어를 도메인별로 붙이면(`problem_`, `analysis_`) 소유권이 눈에 보인다.

**V1 baseline (배세빈)** 에 들어갈 것:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
-- teacher, student, class, class_student, refresh_token
```

**벡터 테이블 (이하영 + 모수환)**

```sql
CREATE TABLE concept_chunk (
    id          BIGSERIAL PRIMARY KEY,
    concept_id  VARCHAR(64) NOT NULL,   -- curriculum.js의 소단원 id와 동일
    content     TEXT        NOT NULL,
    embedding   vector(1024) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_concept_chunk_embedding
    ON concept_chunk USING hnsw (embedding vector_cosine_ops);
```

JPA 엔티티는 `float[]` + `@JdbcTypeCode(SqlTypes.VECTOR)`로 매핑하고, **유사도 검색은 네이티브 쿼리(`<=>` 연산자)** 로 쓴다. 여기서만 JPA를 우회하는 게 가장 단순하다.

**가드레일 차단 로그 (이동규)** — 3-4절 차단 이벤트 기록. 교사 대시보드 신호로도 재활용한다.

```sql
CREATE TABLE guard_block_log (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT      NOT NULL,
    role        VARCHAR(16) NOT NULL,   -- TEACHER | STUDENT
    stage       VARCHAR(16) NOT NULL,   -- INPUT | OUTPUT
    rule        VARCHAR(64) NOT NULL,   -- ANSWER_LEAK, INJECTION, OUT_OF_SCOPE ...
    agent_type  VARCHAR(32) NOT NULL,
    problem_id  BIGINT,                 -- 정답 누설 차단 시 대상 문항
    detail      TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_guard_block_log_member ON guard_block_log (member_id, created_at DESC);
```

---

## 6. 공통 기반 (배세빈 — 다른 작업의 선행 조건)

다른 사람들이 도메인 작업을 시작하기 전에 이게 먼저 있어야 한다.

### 응답 포맷

```json
{ "success": true, "data": { }, "error": null }
```

```json
{ "success": false, "data": null, "error": { "code": "STUDENT_NOT_FOUND", "message": "..." } }
```

`ApiResponse<T>` + `ErrorCode` enum + `@RestControllerAdvice` 하나로 통일. 각자 예외 처리 방식을 만들지 않는다.

### 인증 (JWT)

- `POST /api/auth/login` → access(30분) + refresh(14일)
- `POST /api/auth/reissue`, `POST /api/auth/logout`
- refresh token은 DB 테이블 저장 (rotation)
- role: `ROLE_TEACHER`, `ROLE_STUDENT`
- `/api/student/**` 는 STUDENT, `/api/teacher/**` 는 TEACHER — URL 접두어로 권한을 나누면 SecurityConfig가 단순해진다
- `@LoginUser` 파라미터 리졸버로 컨트롤러에서 인증 주체를 바로 받는다

### 기타

- **CORS**: `local` 프로파일에서 `http://localhost:5173` 허용 (Vite 기본 포트)
- **Swagger**: `/swagger-ui.html` 를 프론트와의 API 계약서로 쓴다. 이동규가 프론트 작업 중 여기만 보고 연동할 수 있어야 한다

---

## 7. API 경계 (프론트 화면 → 담당자)

프론트 라우트를 그대로 뒤집어 엔드포인트 소유자를 나눈다. 이 표가 있어야 이동규가 어느 화면부터 실연동할지 정할 수 있다.

| 프론트 화면 | 엔드포인트 접두어 | 담당 |
|---|---|---|
| `/` 로그인 | `/api/auth` | 배세빈 |
| `/students`, `/students/classes` | `/api/teacher/students`, `/classes` | 배세빈 |
| `/problems` 문제 만들기 | `/api/teacher/problems` | 이하영 |
| `/problems/comprehensive` 종합평가 | `/api/teacher/assessments` | 이하영 |
| `/problems/library` 문제 보관함 | `/api/teacher/worksheets` | 배세빈 |
| `/problems/custom` 맞춤 문제 생성 | `/api/teacher/custom-problems` | 모수환 + 이하영 |
| `/learning` 학습 현황 | `/api/teacher/learning-status` | 배세빈 |
| `/learning/results` 평가 결과·채점 | `/api/teacher/grading` | 배세빈 |
| `/learning/weaknesses` 취약점 분석 | `/api/teacher/analysis` | 모수환 |
| `/dashboard` | `/api/teacher/dashboard` | 모수환 |
| `/student/**` 학생앱 | `/api/student/**` | 배세빈 |
| 개념 챗봇 (`ConceptChatPanel`) | `/api/chat` (SSE) | 배세빈 |

---

## 8. 협업 규칙

- 브랜치: `main` ← `develop` ← `feat/{도메인}-{작업}` (예: `feat/analysis-weakness-api`)
- `main` / `develop` 직접 push 금지, PR 필수, 승인 1명
- 커밋 메시지는 프론트 컨벤션 유지: `feat : 취약점 분석 - 개념별 성취도 API`
- CI (GitHub Actions, 모수환): PR마다 `./gradlew build` + 테스트. Testcontainers로 pgvector 컨테이너를 띄워 통합 테스트까지 실행
- **ArchUnit 테스트를 CI 필수 항목으로 둔다 (이동규)**: `domain..` 패키지가 `ai.client..`를 직접 참조하면 빌드 실패. 디스패처 우회 경로가 생기는 순간 PR이 막히게 하는 것이 3-4절 "강제 집행" 논리의 실제 구현이다
- 테스트 컨벤션과 최소 커버리지 기준은 QA 담당(이동규)이 정한다
- 백엔드 리포에도 `AGENTS.md`를 두고, 위의 교차 도메인 규칙·Flyway 규칙·응답 포맷을 명시한다 (프론트에서 이미 잘 작동하는 방식)

---

## 9. 착수 순서 (스프린트 0, 약 1주)

### Day 1 — 배세빈 + 모수환

> 다른 두 명은 대기하지 말고 데이터 전처리 병행

1. `backend` 리포 생성, Spring Initializr 프로젝트 커밋
2. `docker/compose.yml` + README 실행 절차
3. Flyway `V1` baseline (vector extension + member/auth 테이블)
4. 패키지 스켈레톤 전체 생성 (빈 패키지 + `package-info.java`)
5. `.github/CODEOWNERS`, PR 템플릿, 브랜치 보호 설정

### Day 2 — 배세빈

6. `ApiResponse` / `ErrorCode` / `GlobalExceptionHandler`
7. JWT 로그인·재발급, SecurityConfig, `@LoginUser`
8. Swagger 설정 + `/api/auth` 문서화
9. 시드 데이터 (`V2` 또는 `data-local.sql`): 프론트 mock의 교사 1명 · 반 1개 · 학생 명단 그대로

### Day 2~3 — 이동규 (에이전트 작업의 선행 조건)

> AgentDispatcher는 모든 에이전트가 통과해야 하는 유일한 진입점이므로, **에이전트 구현보다 먼저 인터페이스가 나와야 한다.** 뒤늦게 만들면 이미 직접 호출한 코드를 되돌려야 한다.

7. `AgentRequest` / `AgentResponse` / `OutputValidator` 인터페이스 확정 → 팀에 공유
8. `AgentDispatcher` 골격 + 입력 가드레일 자리만 뚫어둔 통과 구현 (`no-op` 검증기)
9. ArchUnit 규칙 작성 (`domain..` → `ai.client..` 참조 금지)

### Day 3 — 전원

10. 각 도메인 엔티티 + 마이그레이션 작성, 서로 리뷰 (여기서 스키마 충돌을 다 털어낸다)
11. 이동규: 프론트 API 클라이언트 레이어(`src/api/`) 신설 + 로그인 화면 실연동 → mock 제거의 첫 사례

### Day 4~5 — 병렬 착수

12. 이하영: 문제 은행 전처리 데이터 적재 + `curriculum` 시드
13. 모수환: CI 파이프라인, 임베딩 컨테이너, pgvector 색인 파이프라인
14. 배세빈: `ai/client` 래퍼 + 개념 챗봇 프로토타입 (디스패처 경유)
15. 이동규: 입력 가드레일 실제 구현 + 프롬프트 템플릿 저장 규칙
16. 배세빈 + 이동규: 개념 설명 출력 검증 1·2층 (정답 미제공 + 규칙 기반 매칭). 3층 LLM 판정은 서술형 문항이 붙은 뒤로 미룬다

---

## 10. 지금 결정해야 할 항목

1. **임베딩 모델** (3-2절) — 미루면 재색인 비용 발생. 추천: BGE-m3 self-hosted, `vector(1024)`
2. **Anthropic 워크스페이스/키 발급 방식** — 팀 공용 키 1개 vs 개인별 키. 사용량 추적이 필요하면 개인별 + 워크스페이스 분리
3. **필기 이미지 저장소** — 로컬 파일시스템으로 시작 후 S3 전환 vs 처음부터 MinIO 컨테이너. 추천: MinIO (인터페이스가 S3와 동일해 전환 비용 0)
4. **배포 대상** — 프론트는 Vercel인데 백엔드는 어디로 갈지 (EC2 / Cloudtype / Railway). 모수환의 CI/CD 설계에 영향
5. **프로젝트/패키지 이름** — `com.senny.backend` 확정 여부
6. **가드레일 검증에 넘길 대화 히스토리 범위** (3-4절 한계 2) — 마지막 발화만 보면 멀티턴 우회에 뚫린다. 최근 N턴 / 세션 요약 / 전체 중 선택. 추천: 최근 3턴 + 현재 문항 컨텍스트
7. **정답 누설 3층(LLM 판정) 적용 시점** — 초기엔 1·2층만으로 시작하고, 서술형 문항 비중이 커지면 도입
