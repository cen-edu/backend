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
4건은 모두 응답과 토큰·latency가 기록됐다. `gpt-5.6-luna`가 포함된 세 조합은 현재 공급자 호출에서
`BusinessException(AI_CLIENT_CALL_FAILED)`가 발생해 정상 표본으로 집계하지 않았다. 따라서 이 파일럿은
모델 우열이나 최종 설정을 결정할 수 없으며, 해당 모델의 실제 배포 식별자·접근 권한을 확인한 뒤 재실행해야 한다.

이번 실행은 조합별 4건 파일럿으로, 계획서의 경로별 20문항·수정 20건 본 측정 및 전후 통과율 비교를
완료한 것으로 간주하지 않는다. 최종 빌드(`bash gradlew build`)는 실행하지 않았다.
