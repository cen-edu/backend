# 채점 API 성능 측정용 시드

`task_06`의 측정 요구(학생 25명 × 문항 20개 점수표, 학생 상세 수십 칸)를 실제 HTTP 호출로 재려면
데이터가 있어야 한다. 이 디렉터리가 그 데이터를 만든다.

**마이그레이션이 아니다.** Flyway 이력에 남지 않으며 필요할 때 손으로 돌린다.

## 깔리는 것

| | |
|---|---|
| 학생 | 25명 (`gradeperf_S01` ~ `S25`) |
| `[채점측정] 종합평가 20문항` | 문항 20 · 학생당 **20칸** · `max_score` 5.00 |
| `[채점측정] 일반학습 단계형` | 문항 12 · 학생당 **42칸** · `max_score` **NULL** |
| 답안 | **1,550칸** 전부 `NOT_GRADED` |
| 풀이 시간 | 800행 |

`compare_method` 6종을 전부 덮는다 — CHOICE 300 / VALUE 425 / SUBST 375 / EXACT 350 / SET 50 / RUBRIC 50.
정답률은 약 70%이고 `random()`을 쓰지 않아(해시 `(배정ID×7 + 칸ID×3) % 10 < 7`) 몇 번을 돌려도 같은 답안이 나온다.

시드 학생은 **로그인할 수 없다** — `password_hash`가 bcrypt 형식이 아니다.

## 실행

### 1. 측정용 교사를 만든다 (앱이 떠 있어야 함)

시드가 소유자로 쓸 교사다. SQL 로는 bcrypt 해시를 만들 수 없어 회원가입 API 를 쓴다.

```bash
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"gradeperf.teacher@cenedu.test","name":"채점측정교사","password":"gradeperf1234"}'
```

이미 있으면 건너뛴다. 이 계정이 없으면 시드는 `stage2test@cenedu.local`(task_04 생성)로 떨어지는데,
그 계정은 비밀번호를 모르므로 HTTP 측정에 쓸 수 없다.

### 2. 시드를 넣는다

```bash
docker cp tools/grading-perf-seed/seed.sql cen-edu-postgres:/tmp/seed.sql
docker exec cen-edu-postgres psql -U cen -d cen_edu -v ON_ERROR_STOP=1 -f /tmp/seed.sql
```

이미 깔려 있으면 예외를 던지고 멈춘다. 다시 깔려면 먼저 지운다.

### 3. 토큰을 받는다

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"gradeperf.teacher@cenedu.test","password":"gradeperf1234"}'
```

## 지우기

```bash
docker cp tools/grading-perf-seed/cleanup.sql cen-edu-postgres:/tmp/cleanup.sql
docker exec cen-edu-postgres psql -U cen -d cen_edu -v ON_ERROR_STOP=1 -f /tmp/cleanup.sql
```

삭제 기준은 marker 세 개뿐이라 그 밖의 데이터는 건드리지 않는다.

| 대상 | marker |
|---|---|
| 반 | `member_school_class.name = '채점측정 1반'` |
| 학생 | `member_account.login_id LIKE 'gradeperf_S%'` |
| 학습지 | `worksheet.title LIKE '[채점측정]%'` |

**교사 계정은 지우지 않는다** — 시드가 만든 것이 아니라 회원가입 API 로 만든 것이라서다.

## 쿼리 수를 재는 법

`SPRING_JPA_SHOW_SQL=true` 로 앱을 띄우고 호출 전후의 `Hibernate:` 줄 수 차이를 본다.

```bash
SPRING_JPA_SHOW_SQL=true ./gradlew bootRun > run.log 2>&1
```

⚠️ **로그 기록이 응답보다 늦다.** 호출 직후 바로 세면 실제보다 적게 나온다(상세 조회를 10개가 아니라
3개로 잘못 잰 적이 있다). 세기 전에 2~3초 기다릴 것.
