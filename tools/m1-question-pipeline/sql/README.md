# 문제은행 DB 적재

이 디렉토리는 Flyway가 생성한 ERD 테이블에 `delivery` 산출물을 한 번 적재하기 위한 SQL입니다.

Flyway migration은 테이블 구조를 만들고 검증합니다. 이 SQL은 문제 데이터 자체를 적재합니다. 따라서 서버를 실행해 Flyway를 적용한 뒤, 사용자가 별도로 이 SQL을 실행합니다.

JSONL 파일을 바로 적재하지 않고 쿼리 파일을 실행시키는 이유는 Unique Key, FK 등 조건을 jsonl 파일만을 사용하여 insert 할 경우 제약조건이 지켜지지 않을 수 있어 쿼리 파일을 이용하였습니다.

## 실행

backend 디렉토리에서 실행합니다.

```bash
docker compose up -d postgres
./gradlew bootRun
```

서버가 Flyway를 적용한 뒤 별도 터미널에서 실행합니다.
팀에서 지정한 PostgresSQL 에 적재를 진행합니다.

```bash
PGPASSWORD=cen_local_password \
psql -h localhost -U cen -d cen_edu \
  -v ON_ERROR_STOP=1 \
  -f tools/m1-question-pipeline/sql/load_problem_bank.sql
```

SQL은 `delivery/canonical/curriculum_units.jsonl`과 `delivery/db_staging/*.jsonl`을 읽습니다. 따라서 명령어는 반드시 backend 디렉토리에서 실행하고, delivery 파일이 먼저 생성되어 있어야 합니다.

## 적재 대상

다음 테이블을 FK 순서에 맞춰 적재합니다.

1. `curriculum_unit`
2. `problem_question`
3. `problem_step`
4. `problem_answer_unit`
5. `problem_choice`
6. `problem_asset`

현재 `problem_topic`, `problem_rubric_item`은 적재하지 않습니다.

`problem_topic` 의 경우 현재 30번 데이터에 topic 지정조건을 명확하게 정의하지 않아 우선 보류 하였으며
`problem_rubric_item` 의 경우 제가 이해한 바로는 HITL 에 따라 조건이 UPSERT / INSERT 될 것 같아서 제가 정의하지 않았습니다 # 세빈님 확인해주세요 !

SQL은 `source_ref`, 단계·답안·자산 키의 UNIQUE 제약을 고려해 재실행 가능한 upsert 방식으로 동작합니다. 단, 운영 데이터가 이미 있는 DB에서 실행하기 전에는 백업과 대상 범위를 확인해야 합니다.

## 정상 여부 확인

SQL 마지막에 각 테이블의 적재 건수와 다음 검증 결과가 출력됩니다.

- `orphan_question_curriculum = 0`
- `non_step_diagnostic_type = 0`

추가 확인:

```sql
SELECT source_ref, COUNT(*)
FROM problem_question
WHERE source_ref IS NOT NULL
GROUP BY source_ref
HAVING COUNT(*) > 1;

SELECT q.id, a.asset_key
FROM problem_question q
JOIN problem_asset a ON a.question_id = q.id
WHERE a.storage_key IS NULL OR a.storage_key = '';
```

첫 번째 쿼리는 결과가 없어야 하며, 두 번째 쿼리도 결과가 없어야 합니다.

## 비정상 적재 시 확인할 것

- `relation does not exist`: 서버를 먼저 실행해 Flyway migration이 적용됐는지 확인합니다.
- `No such file`: backend 디렉토리에서 실행했는지, `delivery` 산출물이 존재하는지 확인합니다.
- `duplicate key`: 기존 DB 데이터와 충돌하는지 확인하고 중복 삭제 없이 먼저 백업합니다.
- FK 오류: `curriculum_units.jsonl`과 문항의 `sub_unit_id`가 일치하는지 확인합니다.
- 이미지가 표시되지 않음: `problem_asset.storage_key`와 로컬/S3 저장소 경로가 일치하는지 확인합니다.

서버 재시작만으로 PostgreSQL 데이터는 삭제되지 않습니다. `docker compose down -v`는 데이터 볼륨까지 삭제하므로 테스트 DB를 초기화할 때만 사용해야 합니다.
