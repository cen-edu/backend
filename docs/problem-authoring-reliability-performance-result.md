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
