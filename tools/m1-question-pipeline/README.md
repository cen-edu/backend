# M1 Question Pipeline

중학교 1학년 수학 30·110·111번 원천 데이터를 문제은행 및 DB 적재 형태로 변환하는 팀 공통 파이프라인입니다.

파이프라인은 18개 소단원 분류, LearningGuide 생성, `MULTIPLE_CHOICE`·`SHORT_INPUT`·`STEP_FILL`·`ESSAY` 정규화, 문항 품질 변환, 메타데이터 연결, DB 적재용 JSONL 생성을 수행합니다.

## 1. 입력 데이터와 사전 생성 문제 파일

`data/` 아래에 다음 세 원천 폴더를 배치합니다. 28번 원천은 필수가 아닙니다.

```text
data/
├── 30.수학교과풀이과정데이터/
├── 110.수학 과목 자동풀이 데이터/
└── 111.수학 과목 문제 생성 데이터/
```

API 비용 없이 사전에 생성해 둔 STEP_FILL·ESSAY 결과를 사용하려면 다음 파일도 `data/` 아래에 둡니다.

```text
data/
├── step_fill_accepted.jsonl
├── step_fill_rejected.jsonl
└── essay_accepted.jsonl
```

- `step_fill_accepted.jsonl`: 사전에 생성하고 검증한 110·111 STEP_FILL 문항과 정답
- `step_fill_rejected.jsonl`: 사전 생성 과정에서 제외된 문항 기록. 품질 추적용이며 최종 문항으로 적재하지 않음
- `essay_accepted.jsonl`: 사전에 생성하고 검증한 111 ESSAY 문항

## 2-A. 사전 생성 STEP_FILL 파일이 있는 경우

위 사전 생성 파일을 배치한 뒤 아래 명령만 실행합니다. API를 호출하지 않고 사전 생성 파일의 결과를 우선 사용합니다.

```bash
cd tools/m1-question-pipeline
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e .

mkdir -p run-prebuilt
PYTHONPATH=src python -m m1_curriculum_mapper.build \
  --data-root data \
  --output run-prebuilt \
  2>&1 | tee run-prebuilt/build.log
```

## 2-B. 사전 생성 STEP_FILL 파일이 없는 경우

동일한 명령을 실행하면 API를 호출하지 않고, 아직 생성되지 않은 문항을 `generation/step_fill_candidates.jsonl`에 기록합니다. 이 상태에서는 해당 STEP_FILL 문항이 최종 산출물에 자동 추가되지 않습니다.

API 생성을 승인한 경우에만 `openai`를 설치하고 옵션을 추가합니다.

```bash
python -m pip install -e '.[step-fill]'
PYTHONPATH=src python -m m1_curriculum_mapper.build \
  --data-root data \
  --output run-generated \
  --allow-api-generation \
  2>&1 | tee run-generated/build.log
```

### OpenAI API Key 설정

방법 A: 환경변수로 등록합니다.

```bash
export OPENAI_API_KEY="your-api-key"
```

방법 B: `.env` 파일을 만들고 실행 전에 셸에서 읽습니다.

```dotenv
OPENAI_API_KEY=your-api-key
```

```bash
set -a
source .env
set +a
```

파이프라인이 `.env`를 자동으로 읽는 것은 아니므로 `source .env` 단계가 필요합니다. `.env`는 비밀키를 포함하므로 개인적으로 준비해야 합니다.

## 3. 생성 결과 확인

```bash
jq '{status, apiCalls, curriculumUnitCount, accepted, rejected, pendingGeneration, countsByType, countsByDataset}' \
  run-prebuilt/manifest.json
```

주요 생성 결과:

- `canonical/`: 파이프라인 정본 문항
- `final_datashape/`: 화면 및 API 응답용 최종 문항 형태 - 이는 최종적으로 화면에 뿌리기 위해 사용한 참고용 데이터 입니다.
- `db_staging/`: ERD 테이블별 DB 적재용 JSONL
- `load/`: DB 적재 순서별 JSONL
- `generation/step_fill_candidates.jsonl`: 생성이 필요한 STEP_FILL 후보
- `generation/step_fill_accepted.jsonl`: API로 새로 생성되어 승인된 STEP_FILL
- `generation/step_fill_rejected.jsonl`: API 생성 과정에서 제외된 STEP_FILL
- `reports/`: 분류·중복·품질 진단 결과
- `manifest.json`: 실행 상태, 문항 수, API 호출 수, 문항 유형별 집계

## 4. 결과물 사용 기준

`data/`의 사전 생성 파일은 API 재생성을 막고 모든 팀원이 같은 STEP_FILL·ESSAY 결과를 사용하도록 하기 위한 입력 파일입니다.

DB 적재에는 `delivery/db_staging/` 또는 적재 순서가 필요한 경우 `delivery/load/`의 JSONL을 사용합니다. 실제 사용할 형태의 데이터셋을 활용하여 검증하려면 `delivery/final_datashape/`, 파이프라인 정합성 검증에는 `delivery/canonical/`을 사용할 수 있습니다.

## source_ref와 30번 소문제 처리

30번 원천 데이터는 하나의 원천 문제 안에 여러 소문제가 포함될 수 있습니다. 현재 산출물에서 확인된 현황은 다음과 같습니다.

- 소문제가 포함된 부모 원천 문제: 82개
- 분리된 30번 소문제 문항: 216개
- 부모 `source_ref`를 공유하던 추가 문항: 134개

DB의 `problem_question.source_ref`가 UNIQUE이므로, 소문제를 부모 `source_ref` 그대로 적재하면 충돌이 발생합니다. 따라서 `recordId`가 `source:30:...:part:N` 형태인 30번 문항은 적재용 `source_ref`를 다음처럼 변환합니다.

```text
30:S3_중등_1_000001:part:1
30:S3_중등_1_000001:part:2
```

원천 문제와 소문제의 관계는 `recordId` 및 원천 데이터의 부모 식별자를 통해 추적할 수 있습니다. 소문제별 정답을 하나의 답으로 합칠지, 소문제별 독립 답안으로 저장할지는 채점 담당 팀과 별도 협의가 필요합니다. 현재 파이프라인은 소문제를 독립 문항·독립 정답 단위로 유지합니다.

111번은 동일 원천에서 STEP_FILL과 ESSAY가 동시에 생성되는 경우 STEP_FILL을 우선하고 ESSAY를 제외합니다. 동일 원천 문항의 중복 출제를 방지하기 위한 정책입니다.

난이도는 모든 원천에서 숫자 코드로 통일합니다: `1=하`, `2=중`, `3=상`. 30번 원천의 `하/중/상` 값도 파이프라인과 DB 적재 SQL에서 동일하게 변환합니다.
