# CEN EDU Backend

중학교 수학 문제 생성, 학습 이력, 취약점 분석 및 RAG 기능을 제공하기 위한 백엔드 프로젝트입니다.

현재 단계에서는 Spring Boot 애플리케이션과 로컬 PostgreSQL을 연결할 수 있는 개발 환경만 구성되어 있습니다. 도메인 모델과 API는 아직 구현하지 않았습니다.

## 기술 기준

- Java 21
- Spring Boot 4.1.0
- Gradle Wrapper 9.5.1
- PostgreSQL 17
- Flyway

## 1. 사전 준비

다음 프로그램이 필요합니다.

- JDK 21
- PostgreSQL 17
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

## 2. 로컬 데이터베이스 준비

로컬 PostgreSQL에 개발용 사용자와 데이터베이스를 생성합니다.

```sql
CREATE USER cen WITH PASSWORD 'cen_local_password';
CREATE DATABASE cen_edu OWNER cen;
```

애플리케이션의 로컬 기본 접속 정보는 다음과 같습니다.

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 백엔드 HTTP 포트 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cen_edu` | JDBC 접속 주소 |
| `DB_NAME` | `cen_edu` | 로컬 PostgreSQL 데이터베이스 이름 |
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

현재는 첫 도메인 스키마가 정해지지 않았으므로 `src/main/resources/db/migration`에 SQL 마이그레이션이 없습니다. 엔티티 설계가 확정되면 첫 `V1__*.sql` 파일부터 추가합니다.

## 4. 테스트 실행

테스트는 외부 PostgreSQL과 분리된 인메모리 H2 설정을 사용합니다.

```powershell
.\gradlew.bat test
```

## 설정 원칙

- 데이터베이스 스키마는 Flyway 마이그레이션으로만 변경합니다.
- JPA의 `ddl-auto`는 애플리케이션 실행 시 `validate`로 유지합니다.
- 실제 비밀값은 소스 코드에 작성하지 않고 실행 환경 변수로 주입합니다.
- API, 인증, AI/RAG 관련 환경 변수는 해당 기능을 구현할 때 필요한 항목만 추가합니다.
