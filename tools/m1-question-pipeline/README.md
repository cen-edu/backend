# M1 Question Pipeline

중학교 1학년 수학 원천 데이터(28·30·110·111번)를 데이터베이스 적재 전 형태로 정리하는 파이프라인 설명 문서입니다.

다른 환경에서 수행하여도 같은 결과를 확인할 수 있도록 데이터 처리 과정을 파이프라인으로 구성하였습니다.

해당 파이프라인 구성에는 다음과 같은 작업을 진행합니다.

- 18개 소단원 분류 및 LearningGuide 생성
- `MULTIPLE_CHOICE`, `SHORT_INPUT`, `STEP_FILL`, `ESSAY` 문항 구성을 위한 데이터 정규화
- 110·111번 STEP_FILL 빈칸/정답 단위 처리 ( API 를 사용해야 하므로 생략 될 수 있습니다. - 현재는 파일을 같이 제공하여 해당 부분은 생략 가능합니다.)
- 111번 ESSAY 파생 ( 111번 모두 빈칸형으로 처리하기엔 1만건이 넘는 데이터를 생성에 활용하기엔 시간과 비용을 아끼고자 111번 문항을 논술형으로 정의하였습니다. )
- `db_staging` DB 적재용 JSONL 파일 생성
- 거절 문항·생성 후보·실행 요약 JSONL 파일 생성

## 1. 원천 데이터 배치

다음 네가지 원천 데이터가 필요하며 그 위치는 `data/` 바로 아래에 있어야 합니다.

```text
data/
├── 28.교과단계별 교과 데이터/
├── 30.수학교과풀이과정데이터/
├── 110.수학 과목 자동풀이 데이터/
└── 111.수학 과목 문제 생성 데이터/
```

사전에 정의한 빈칸용 문제 데이터를 사용하는 경우 다음 파일도 `data/` 바로 아래에 위치해야합니다. ( 현재 이 파일은 API 비용 발생으로 사전에 제공합니다. )

```text
data/
├── step_fill_accepted.jsonl
├── step_fill_rejected.jsonl
└── essay_accepted.jsonl
```

`step_fill_accepted.jsonl`은 110·111 STEP_FILL 캐시이고, `essay_accepted.jsonl`은 ESSAY 캐시입니다. 캐시 파일은 실행 중 수정하지 않습니다.

## 2. 설치와 기본 실행

```bash
cd tools/m1-question-pipeline
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e .
```

API를 사용하지 않고 캐시파일을 이용한 실행방법 :

```bash
mkdir -p run-candidates
PYTHONPATH=src python -m m1_curriculum_mapper.build \
  --data-root data \
  --output run-candidates \
  2>&1 | tee run-candidates/build.log
```

캐시파일을 이용한 실행방법은 API를 사용하지 않습니다.
실행 중에는 단계별 로그를 남기도록 하였고, 가장 오래 걸릴 수 있는 단계는 110·111 fingerprint 중복 비교입니다.

## 2-A. STEP_FILL 결과가 있는 경우

`data/step_fill_accepted.jsonl`이 있으면 다음 명령만 실행 하면 됩니다.

```bash
mkdir -p run-cached
PYTHONPATH=src python -m m1_curriculum_mapper.build \
  --data-root data \
  --output run-cached \
  2>&1 | tee run-cached/build.log
```

파이프라인은 다음을 수행합니다.

- 검증 가능한 110·111 STEP_FILL 캐시파일 재사용
- 캐시가 없는 문항을 생성 후보로 분리
- 기존 캐시 파일은 변경하지 않음

## 2-B. STEP_FILL 결과가 없는 경우

캐시가 없어도 기본 실행은 가능합니다.

```bash
mkdir -p run-no-cache
PYTHONPATH=src python -m m1_curriculum_mapper.build \
  --data-root data \
  --output run-no-cache \
  2>&1 | tee run-no-cache/build.log
```

이 경우 API를 호출하지 않고 `generation/step_fill_candidates.jsonl`에 생성 후보를 기록합니다.

API 생성을 승인한 경우에만 다음 옵션을 추가합니다.

```bash
export OPENAI_API_KEY="..."
PYTHONPATH=src python -m m1_curriculum_mapper.build \
  --data-root data \
  --output run-generated \
  --allow-api-generation \
  2>&1 | tee run-generated/build.log
```

## 3. 실행 후 확인할 항목

먼저 manifest를 확인합니다.

```bash
jq '{status, apiCalls, curriculumUnitCount, accepted, rejected, pendingGeneration, generationCandidates, countsByType}' \
  run-candidates/manifest.json
```

정상 확인 기준:

- `curriculumUnitCount`가 `18`
- 문항 유형이 네 가지 이내
- API를 허용하지 않은 실행의 `apiCalls`가 `0`
- `generationCandidates`가 있으면 추가 생성 대기 문항으로 이해
- `status`가 `READY_WITH_GENERATION_CANDIDATES`이면 구조 오류가 아니라 미생성 후보가 남은 상태

주요 결과 위치:

```text
run-*/
├── manifest.json
├── canonical/
├── final_datashape/
├── db_staging/
├── generation/
└── reports/
```

DB 적재에는 `db_staging/`을 사용합니다.

## 4. 팀 공통 저장소 공유 구성

원천 데이터와 API 실행 중간 산출물은 용량 때문에 공통 저장소에 포함하지 않습니다. DB 적재만 필요한 경우에는 `delivery/` 아래의 최종 산출물을 사용합니다.

```text
delivery/
├── final_datashape/   # 화면·API 응답용 최종 문항
├── canonical/         # 정규화된 문항·단원·LearningGuide
├── db_staging/        # DB 테이블별 적재용 JSONL
├── load/              # 적재 순서별 JSONL
├── reports/           # 품질·거절 리포트
└── *.json             # 빌드·적재 manifest
```

`data/step_fill_accepted.jsonl`, `data/step_fill_rejected.jsonl`, `data/essay_accepted.jsonl`은 API를 다시 실행하지 않고 동일 결과를 재구성하기 위한 최소 캐시입니다. 원천 데이터가 없는 환경에서는 `delivery/`를 이용해 바로 DB 적재할 수 있습니다.

## 4. 주의사항
- API 생성은 비용이 발생하므로 후보 검토 후 `--allow-api-generation`을 사용합니다.
