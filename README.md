# CEN EDU Backend

중학교 수학 문제 생성, 학습 이력, 취약점 분석 및 RAG 기능을 제공하기 위한 백엔드 프로젝트입니다.

현재 단계에서는 Spring Boot 애플리케이션과 Docker PostgreSQL(pgvector)을 실행할 수 있는 개발 환경만 구성되어 있습니다. 도메인 모델과 API는 아직 구현하지 않았습니다. 전체 설계와 단계별 착수 순서는 [BACKEND_SETUP.md](BACKEND_SETUP.md)를 참고하세요.

## 기술 기준

- Java 21 (Gradle Toolchain)
- Spring Boot 4.1.0 — Web MVC, Data JPA, Validation, Security, Actuator
- Gradle Wrapper 9.5.1
- PostgreSQL 17 + pgvector (`pgvector/pgvector:pg17`)
- Flyway (의존성만 추가, 아직 비활성)
- springdoc-openapi 3.1.0 (Swagger UI)
- Anthropic Java SDK 2.52.0
- Docker Compose

## 1. 사전 준비

다음 프로그램이 필요합니다.

- JDK 21
- Docker Desktop
- IntelliJ IDEA 또는 Java를 지원하는 IDE

프로젝트는 Java 21 Gradle Toolchain을 사용합니다. IntelliJ IDEA에서는 다음 두 항목을 모두 JDK 21로 지정합니다.

1. `File > Project Structure > Project SDK`
2. `Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM`

PowerShell에서 Java 설정을 확인합니다.

```powershell
java -version
$env:JAVA_HOME
```

`java` 명령을 찾지 못하면 JDK 21 설치 경로를 Windows의 `JAVA_HOME` 사용자 환경 변수에 지정하고, `%JAVA_HOME%\bin`을 `Path`에 추가한 뒤 터미널을 다시 엽니다.

> JDK 21이 없고 다른 버전만 설치돼 있어도, Gradle Toolchain이 빌드 시 JDK 21을 자동으로 내려받아 사용합니다. 다만 IDE 인덱싱을 위해서는 위 설정을 맞추는 편이 낫습니다.

## 2. PostgreSQL 시작

Docker Desktop을 실행한 뒤 저장소 루트에서 PostgreSQL 컨테이너를 시작합니다.

```powershell
docker compose up -d
docker compose ps
```

Compose 설정은 다음 로컬 개발용 데이터베이스를 자동으로 생성합니다.

- 컨테이너: `cen-edu-postgres`
- 이미지: `pgvector/pgvector:pg17`
- 데이터베이스: `cen_edu`
- 사용자: `cen`
- 비밀번호: `cen_local_password`
- 포트: `5432`

데이터는 `postgres-data` Docker 볼륨에 보존됩니다. 컨테이너를 중지해도 볼륨은 삭제되지 않습니다.

`pgvector` 확장 자체는 이미지에 포함돼 있지만, `CREATE EXTENSION vector` 실행은 Flyway 첫 마이그레이션에서 수행합니다. 컨테이너를 지웠다 다시 띄워도 항상 재현되고, 운영 데이터베이스에도 같은 경로로 적용하기 위해서입니다.

> **기존에 `postgres:17-alpine`으로 컨테이너를 띄운 적이 있다면 볼륨을 새로 만들어야 합니다.** 이전 이미지는 Alpine(musl), 현재 이미지는 Debian(glibc) 베이스라 데이터 디렉터리 호환이 보장되지 않습니다.
>
> ```powershell
> docker compose down -v
> docker compose up -d
> ```

## 3. 환경 변수

애플리케이션의 기본 접속 설정은 다음과 같습니다. 기본값이 모두 채워져 있어 현재 단계에서는 별도 설정 없이 실행할 수 있습니다.

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 백엔드 HTTP 포트 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cen_edu` | JDBC 접속 주소 |
| `DB_USERNAME` | `cen` | 데이터베이스 사용자 |
| `DB_PASSWORD` | `cen_local_password` | 로컬 개발 비밀번호 |
| `JWT_SECRET` | 없음 | JWT 서명 키 (인증 구현 시 필요) |
| `ANTHROPIC_API_KEY` | 없음 | LLM 호출 키 (AI 연동 시 필요) |

필요한 키 목록은 [.env.example](.env.example)에 정리돼 있습니다. 이 파일을 `.env`로 복사한 뒤 값을 채우면 됩니다. IDE 실행 구성을 따로 손댈 필요가 없습니다.

```powershell
Copy-Item .env.example .env
```

`application.yaml`의 `spring.config.import` 설정이 프로젝트 루트의 `.env`를 읽습니다.

- 파일이 없어도 앱은 기본값으로 정상 기동합니다(`optional:`).
- **OS 환경 변수가 `.env`보다 우선합니다.** 일시적으로 값을 바꿔 실행하려면 `$env:SERVER_PORT = "8090"`처럼 지정하면 `.env` 값을 덮어씁니다. 배포 환경에서는 `.env` 대신 실제 환경 변수를 사용합니다.
- `.env`는 properties 포맷으로 해석됩니다. 값에 따옴표를 쓰지 말고, 백슬래시는 `\\`로 두 번 씁니다.

**비밀값은 리포지토리에 커밋하지 않습니다.** `.gitignore`가 `.env`를 제외하고 `.env.example`만 허용합니다.

## 4. 애플리케이션 시작

Windows PowerShell에서는 Gradle Wrapper로 실행합니다.

```powershell
.\gradlew.bat bootRun
```

프로파일을 지정하지 않으면 `local` 프로파일이 적용됩니다(`application-local.yaml`). SQL 로깅이 켜져 있습니다.

기동 후 다음을 확인할 수 있습니다.

| 주소 | 내용 |
| --- | --- |
| http://localhost:8080/actuator/health | 헬스체크 (`{"status":"UP"}`) |
| http://localhost:8080/swagger-ui.html | Swagger UI — 프론트와의 API 계약서 |
| http://localhost:8080/v3/api-docs | OpenAPI 문서 (JSON) |

> 현재 `SecurityConfig`는 모든 요청을 허용하는 임시 설정입니다. JWT 인증 구현 시 전면 교체되며, 이 상태로 배포하지 않습니다.

## 5. 테스트 실행

H2 테스트 데이터베이스를 사용하지 않습니다. 테스트도 애플리케이션과 동일한 Docker PostgreSQL을 사용하므로, 테스트 전에 PostgreSQL 컨테이너가 실행 중이어야 합니다.

```powershell
.\gradlew.bat test
```

개발을 마친 뒤 컨테이너를 중지하려면 다음 명령을 사용합니다.

```powershell
docker compose stop
```

## 6. 배포

AWS EC2 배포(이미지 빌드 → Docker Hub → EC2 compose)는
[docs/deploy/aws-architecture.md](docs/deploy/aws-architecture.md)를 따릅니다. 구성 파일은
`Dockerfile`과 `deploy/` 아래에 있습니다.

## 설정 원칙

- 개발과 테스트는 동일한 Docker PostgreSQL을 사용합니다.
- 데이터베이스 스키마는 현재 JPA `ddl-auto: update`로 관리하지만, **Flyway 마이그레이션 도입 시 `validate`로 고정 전환합니다.** 그 이후로 스키마 변경은 전부 마이그레이션 파일로만 합니다.
- Compose 파일의 계정 정보는 로컬 개발 전용입니다.
- 비밀값은 `application.yaml`이 아니라 환경 변수로만 주입합니다.
- API, 인증, AI/RAG 관련 환경 변수는 해당 기능을 구현할 때 필요한 항목만 추가합니다.
