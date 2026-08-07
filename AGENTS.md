# AGENTS.md — 작업 규칙

이 문서는 **어떻게 작업할지**를 정합니다. 실행 방법은 [README.md](README.md), 전체 설계와 착수 순서는 [BACKEND_SETUP.md](BACKEND_SETUP.md)를 보세요.

새 코드를 쓰기 전에 3절(교차 도메인 규칙)과 5절(Flyway 규칙)은 반드시 읽으세요. 이 두 가지가 팀 작업에서 가장 자주 깨집니다.

---

## 1. 기술 기준

| 항목 | 값 |
|---|---|
| 언어/런타임 | Java 21 (Gradle Toolchain) |
| 프레임워크 | Spring Boot 4.1.0 |
| 빌드 | Gradle (Groovy DSL), 단일 모듈 |
| DB | PostgreSQL 17 + pgvector (Docker) |
| 마이그레이션 | Flyway (도입 예정 — 5절) |
| 인증 | Spring Security + JWT (TEACHER / STUDENT) |
| LLM | `com.anthropic:anthropic-java`, 모델 `claude-opus-5` |
| API 문서 | springdoc-openapi (`/swagger-ui.html`) |
| 패키지 루트 | `com.cenedu.backend` |

---

## 2. 패키지 = 소유 경계

단일 모듈 안에서 **패키지가 곧 소유 경계**입니다. 남의 패키지에 파일을 만들지 않습니다.

```
com.cenedu.backend
├── global/                     배세빈 (공통 영역)
│   ├── config/                 Web, Jpa, Swagger, Cors 설정
│   ├── security/               JwtProvider, Filter, SecurityConfig, @LoginUser
│   ├── common/                 ApiResponse, ErrorCode, GlobalExceptionHandler, BaseTimeEntity, enums
│   └── util/
├── ai/
│   ├── dispatcher/             이동규   AgentDispatcher — 모든 에이전트 호출의 유일한 진입점
│   ├── guard/input/            이동규   공통 입력 가드레일 (역할·인젝션·범위·개인정보)
│   ├── guard/output/           이동규   출력 검증 인터페이스 (구현은 각 에이전트 경로)
│   ├── prompt/                 이동규   프롬프트 템플릿 버전 관리
│   ├── agent/                  이동규   AgentRequest/AgentResponse, 에이전트 인터페이스
│   ├── client/                 배세빈   AnthropicClient 래퍼, 재시도, 토큰 사용량 로깅
│   └── embedding/              모수환   임베딩 클라이언트 (Anthropic 아님)
├── domain/
│   ├── auth/                   배세빈   로그인, 토큰 재발급, 비밀번호 변경
│   ├── member/                 배세빈   교사·학생·반(class), 명단 등록
│   ├── curriculum/             이하영   학년>과목>학기>대>중>소 단원 트리, 개념
│   ├── problem/                이하영   문제 은행, 문제 생성·수정 에이전트
│   ├── worksheet/              배세빈   학습지(일반/종합평가/맞춤), 배정
│   ├── submission/             배세빈   풀이 제출, 필기 획 데이터 → 이미지
│   ├── grading/                배세빈   자동 채점, 답안 검증·서술형 채점 에이전트
│   ├── analysis/               모수환   취약점 분석, 맞춤 학습 처방
│   ├── dashboard/              모수환   반×학기 집계
│   └── chat/                   배세빈   개념 챗봇 (RAG)
└── infra/
    ├── vector/                 모수환   pgvector 리포지토리, 유사도 검색
    └── storage/                모수환   필기 이미지 저장 (로컬 → MinIO/S3)
```

각 패키지의 `.gitkeep`은 자리표시자입니다. 해당 패키지에 첫 클래스를 추가할 때 함께 지우세요.

### 엔드포인트 소유

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
| 개념 챗봇 | `/api/chat` | 배세빈 |

`/api/student/**`는 STUDENT, `/api/teacher/**`는 TEACHER 권한입니다. URL 접두어로 권한을 나누므로 접두어를 임의로 바꾸지 마세요.

---

## 3. 교차 도메인 규칙

초기에 못 박아야 나중에 안 터지는 4가지입니다.

**1. 다른 도메인의 엔티티를 직접 참조하지 않는다.**
FK는 두되 JPA 연관관계 대신 ID로 들고, 필요한 데이터는 상대 도메인의 Service public 메서드로 조회합니다.

```java
// X
@ManyToOne private Student student;

// O
private Long studentId;
```

**2. 도메인 간 호출은 service 레이어만 허용한다.**
남의 `repository`를 직접 호출하지 않습니다.

**3. 여러 도메인이 함께 쓰는 상수·라벨은 `global/common/enums`에 한 번만 선언한다.**
프론트 `src/mocks/labels.js`가 단일 기준이므로 값 문자열을 그대로 맞춥니다.

| 구분 | 값 |
|---|---|
| 난이도 | `low` / `mid` / `high` |
| 진행 상태 | `not-started` / `in-progress` / `submitted` |
| 문항 유형 | `choice` / `short` / `essay` |

**4. `ai/client`의 Anthropic 클라이언트를 도메인 패키지에서 직접 호출하지 않는다.**
LLM 호출은 예외 없이 `AgentDispatcher`를 거칩니다. 시스템 트리거 요청(답안 검증·서술형 채점)도 예외가 아닙니다.

> 왜: 챗봇이 N개로 늘 때 가드레일을 N번 구현하면 N개의 서로 다른 안전 수준이 생깁니다. 디스패처는 공통 정책을 단일 지점에서 집행해 이 편차를 없앱니다. 예외 경로를 하나 열어두면 그 경로만 무방비가 되고, 나중에 누가 거기에 사용자 입력을 얹으면 원칙이 무너집니다.
>
> 이 규칙은 ArchUnit 테스트로 CI에서 강제합니다. `domain..`이 `ai.client..`를 참조하면 빌드가 실패합니다.

---

## 4. 도메인 패키지 내부 구조

각 도메인 패키지 내부는 다음으로 통일합니다.

```
domain/problem/
├── controller/
├── service/
├── repository/
├── entity/
└── dto/
```

디렉터리는 해당 클래스를 처음 만들 때 생성합니다. 미리 빈 폴더를 만들지 않습니다.

---

## 5. Flyway 규칙

> Flyway는 의존성만 추가된 상태이고 아직 비활성입니다(`spring.flyway.enabled: false`). `V1` baseline 작성 시 활성화하며, 그때부터 아래 규칙이 적용됩니다.

**버전은 타임스탬프로 붙입니다.** 순번(`V1`, `V2`)을 쓰면 두 사람이 동시에 `V5`를 만들어 머지가 깨집니다.

```
V{yyyyMMdd_HHmm}__{도메인}_{설명}.sql

V20260810_1430__problem_create_tables.sql        (이하영)
V20260810_1615__analysis_add_weakness_score.sql  (모수환)
```

- 위치: `src/main/resources/db/migration/`
- **적용된 마이그레이션 파일은 절대 수정하지 않습니다.** 되돌릴 땐 새 파일을 추가합니다. 이미 적용된 파일을 고치면 체크섬이 달라져 다른 팀원의 앱이 기동에 실패합니다.
- `spring.jpa.hibernate.ddl-auto: validate` 고정. `update` / `create` 금지.
- 테이블 접두어를 도메인별로 붙입니다 (`problem_`, `analysis_`).
- 벡터 컬럼은 `vector(1024)`로 통일합니다. 임베딩 모델을 바꿔도 스키마는 유지됩니다.
- 유사도 검색은 네이티브 쿼리(`<=>` 연산자)로 씁니다. 여기서만 JPA를 우회합니다.

---

## 6. 응답 포맷

모든 API 응답은 `ApiResponse<T>`로 감쌉니다. 각자 예외 처리 방식을 만들지 않습니다.

```json
{ "success": true, "data": { }, "error": null }
```

```json
{ "success": false, "data": null, "error": { "code": "STUDENT_NOT_FOUND", "message": "..." } }
```

- `ApiResponse<T>` + `ErrorCode` enum + `@RestControllerAdvice` 하나로 통일합니다.
- 새 에러는 `global/common`의 `ErrorCode`에 추가합니다. 컨트롤러에서 직접 상태 코드를 만들지 않습니다.
- Swagger(`/swagger-ui.html`)를 프론트와의 API 계약서로 씁니다. 프론트 담당자가 여기만 보고 연동할 수 있어야 합니다.

---

## 7. 브랜치·커밋

```
main ← develop ← feat/{도메인}-{작업}
```

- 예: `feat/analysis-weakness-api`
- `main` / `develop` 직접 push 금지. PR 필수, 승인 1명.
- 커밋 메시지는 프론트 컨벤션을 유지합니다.

```
feat : 취약점 분석 - 개념별 성취도 API
chore : gitignore 파일 추가
```

- PR마다 `./gradlew build` + 테스트가 통과해야 합니다.
- ArchUnit 테스트는 CI 필수 항목입니다. 디스패처 우회 경로가 생기는 순간 PR이 막힙니다.

---

## 8. 환경 변수·비밀값

- **비밀값을 `application.yaml`이나 리포지토리에 커밋하지 않습니다.** `ANTHROPIC_API_KEY`, `JWT_SECRET`이 해당합니다.
- 로컬에서는 `.env.example`을 `.env`로 복사해 값을 채웁니다. `.env`는 gitignore 대상입니다.
- 새 환경 변수를 추가하면 **반드시 `.env.example`에 키를 추가**하고 PR 설명에 적습니다. 그러지 않으면 다른 팀원이 원인 모를 기동 실패를 겪습니다.
- 우선순위: OS 환경 변수 > `.env` > `application.yaml` 기본값.
- 프로파일은 지정하지 않으면 `local`이 적용됩니다. 배포 환경은 `SPRING_PROFILES_ACTIVE`로 지정합니다.

---

## 9. 로컬 실행

[README.md](README.md)를 참고하세요. 요약하면:

```powershell
docker compose up -d
.\gradlew.bat bootRun
```
