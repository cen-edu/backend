# 문제 저작 신뢰성·성능 측정 결과

## 측정 상태

Task7 파일럿 비교 하니스를 추가했다. `OPENAI_API_KEY`가 있는 환경에서만 활성화되며,
`build/measurements/task7-model-comparison-pilot.tsv`에 결과를 기록한다.

## 고정 모델 범위

비용 요구사항에 따라 생성·검증 모델은 `gpt-4o-mini`, `gpt-5.6-luna`만 사용한다. 비교 조합은 2×2 네 가지다.

## 파일럿 표본 및 한계

현재 저장소의 자동화된 비식별 고정 표본은 네 유형뿐이다. 따라서 조합별 4건을 실행하는 파일럿이며,
계획서의 경로별 20문항 본 실험을 대체하지 않는다. 20문항 fixture가 확보되면 동일 하니스에 추가한다.

## 기록 지표

각 호출의 모델, 표본 ID, latency, prompt token, completion token을 TSV로 기록한다. API 키와 문제·정답 원문은 저장하지 않는다.

## 2026-08-21 파일럿 실행 결과

실행 명령은 `bash gradlew test --tests '*ProblemAuthoringModelComparisonLiveTest'`이며, 결과 파일은
`build/measurements/task7-model-comparison-pilot.tsv`이다. `gpt-4o-mini → gpt-4o-mini` 조합의
처음 실행에서는 `gpt-5.6-luna`에 `reasoning-effort=minimal`을 전달해 `BadRequestException`이 발생했다.
실제 `.env` 운영 설정(`OPENAI_REASONING_EFFORT=medium`)과 실험 설정이 달랐던 것이 원인이었다.
하니스를 모델별 운영 설정과 동일하게 수정한 뒤 네 조합 16건 모두 정상 응답과 토큰·latency를 기록했다.

파일럿 평균 latency는 다음과 같다.

| 생성 → 검증 | 생성 평균 | 검증 평균 |
|---|---:|---:|
| 4o-mini → 4o-mini | 1,871ms | 2,792ms |
| 4o-mini → 5.6-luna | 1,597ms | 2,173ms |
| 5.6-luna → 4o-mini | 1,845ms | 2,078ms |
| 5.6-luna → 5.6-luna | 1,809ms | 1,726ms |

이번 실행은 조합별 4건 파일럿으로, 계획서의 경로별 20문항·수정 20건 본 측정 및 전후 통과율 비교를
완료한 것으로 간주하지 않는다. 최종 빌드(`bash gradlew build`)는 실행하지 않았다.

## 비용 확인용 5건 실행

각 경로 5건으로 확장한 실행은 40회 API 호출을 시작했으나, 공급자 응답 지연으로 장시간 실행되어
중단했다. 따라서 이 실행은 완결된 측정값으로 집계하지 않는다. 이전 정상 파일럿 15건의 기록에는
prompt 1,780토큰, completion 4,086토큰(합계 5,866토큰)이 기록되어 있으며, 실제 비용은 계정의
모델별 단가를 적용해 계산해야 한다.

## 경로별 5건 × 4개 모델 조합 실행 결과

Gradle의 `-Dtask7.*` 값이 fork된 테스트 JVM에 전달되지 않아 작은 배치 요청도 160회 전체 호출로
실행되던 원인을 수정했다. Gradle이 필터 속성을 전달하도록 하고, 결과를 문항마다 즉시 TSV에 append한 뒤
각 조합·경로별 5건씩 총 80개 생성·검증 케이스를 실행했다.

| 생성 → 검증 | 성공/전체 | 생성 평균 | 검증 평균 | 성공 호출 토큰 기준 비용 |
|---|---:|---:|---:|---:|
| 4o-mini → 4o-mini | 20/20 | 3,610ms | 6,466ms | $0.01089 |
| 4o-mini → 5.6-luna | 19/20 | 3,399ms | 4,821ms | $0.01710 |
| 5.6-luna → 4o-mini | 16/20 | 5,923ms | 5,110ms | $0.01588 |
| 5.6-luna → 5.6-luna | 17/20 | 5,340ms | 3,087ms | $0.01872 |

실패 8건은 timeout·rate limit이 아니라 모두 `finishReason=LENGTH`였다. `gpt-5.6-luna`가 실험용
`maxCompletionTokens=1200`을 reasoning token 1,200개로 모두 사용해 본문이 비어 실패했다. 실패 응답의
출력 토큰 비용 최소 $0.01152를 포함하면 이번 80건 실험 비용은 약 $0.07412 이상이며, 환율 1달러=1,400원
가정 시 약 104원 이상이다. 실패 호출의 입력 토큰이 TSV에 남지 않아 실제 청구액은 이보다 조금 높다.

이번 5건 실험에서 가장 높은 성공률은 `4o-mini → 4o-mini`(100%)였고 가장 짧은 검증 평균은
`5.6-luna → 5.6-luna`(3,087ms)였다. 다만 현재 하니스는 실제 도메인 생성·검증 Adapter가 아니라
간소화한 일반 텍스트 생성·점검 호출이므로, 이 결과만으로 운영 모델을 확정하지 않는다.
