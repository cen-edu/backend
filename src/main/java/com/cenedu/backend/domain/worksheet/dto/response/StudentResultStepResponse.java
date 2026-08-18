package com.cenedu.backend.domain.worksheet.dto.response;

/**
 * 복습 화면의 모범 풀이 한 단계. {@code formula}는 {@code problem_step.segments}의 빈칸을
 * <b>정답</b>으로 채워 완성한 수식이다.
 *
 * <p>명세 예시엔 {@code instruction} 필드가 있지만 {@code problem_step}에 그런 컬럼이 없다
 * ({@link StudentStepResponse}에서 확인 완료). 없는 데이터를 지어내지 않는다.
 */
public record StudentResultStepResponse(String label, String formula) {
}
