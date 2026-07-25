# CEN EDU Backend

중학교 수학 문제 생성, 학습 이력, 취약점 분석 및 RAG 기능을 제공하기 위한 백엔드 프로젝트입니다.

현재 단계에서는 Spring Boot 애플리케이션과 Docker PostgreSQL을 실행할 수 있는 개발 환경만 구성되어 있습니다. 도메인 모델과 API는 아직 구현하지 않았습니다.

## 기술 기준

- Java 21
- Spring Boot 4.1.0
- Gradle Wrapper 9.5.1
- PostgreSQL 17
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

## 2. PostgreSQL 시작

Docker Desktop을 실행한 뒤 저장소 루트에서 PostgreSQL 컨테이너를 시작합니다.

```powershell
docker compose up -d
docker compose ps
```

Compose 설정은 다음 로컬 개발용 데이터베이스를 자동으로 생성합니다.

- 컨테이너: `cen-edu-postgres`
- 데이터베이스: `cen_edu`
- 사용자: `cen`
- 비밀번호: `cen_local_password`
- 포트: `5432`

데이터는 `postgres-data` Docker 볼륨에 보존됩니다. 컨테이너를 중지해도 볼륨은 삭제되지 않습니다.

애플리케이션의 기본 접속 설정은 다음과 같습니다.

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 백엔드 HTTP 포트 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cen_edu` | JDBC 접속 주소 |
| `DB_USERNAME` | `cen` | 데이터베이스 사용자 |
| `DB_PASSWORD` | `cen_local_password` | 로컬 개발 비밀번호 |

기본값과 다른 접속 정보를 사용한다면 애플리케이션을 실행하는 PowerShell 세션이나 IDE 실행 구성에 환경 변수를 지정합니다.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/cen_edu"
$env:DB_USERNAME = "cen"
$env:DB_PASSWORD = "cen_local_password"
```

## 3. 애플리케이션 시작

Windows PowerShell에서는 Gradle Wrapper로 실행합니다.

```powershell
.\gradlew.bat bootRun
```

JPA 엔티티가 추가되면 Hibernate의 `ddl-auto: update` 설정이 Docker PostgreSQL의 테이블을 생성하고 변경합니다.

## 4. 테스트 실행

H2 테스트 데이터베이스를 사용하지 않습니다. 테스트도 애플리케이션과 동일한 Docker PostgreSQL을 사용하므로, 테스트 전에 PostgreSQL 컨테이너가 실행 중이어야 합니다.

```powershell
.\gradlew.bat test
```

개발을 마친 뒤 컨테이너를 중지하려면 다음 명령을 사용합니다.

```powershell
docker compose stop
```

## 설정 원칙

- 개발과 테스트는 동일한 Docker PostgreSQL을 사용합니다.
- 개발 단계의 데이터베이스 스키마는 JPA `ddl-auto: update`로 관리합니다.
- Compose 파일의 계정 정보는 로컬 개발 전용입니다.
- API, 인증, AI/RAG 관련 환경 변수는 해당 기능을 구현할 때 필요한 항목만 추가합니다.
