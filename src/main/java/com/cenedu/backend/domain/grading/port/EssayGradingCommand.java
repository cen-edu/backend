package com.cenedu.backend.domain.grading.port;

import java.util.List;

/**
 * 서술형 채점 한 칸의 입력.
 *
 * @param imageUrl 학생 필기 이미지 URL. 채점 직전 칸마다 발급한 S3 presigned URL 이다(D4).
 *                 <b>이미지 바이트를 넘기지 않는다</b> — 모델 쪽이 직접 받아 가므로 우리가 파일을
 *                 들고 있을 이유가 없다
 * @param criteria 판정할 채점 기준 항목. 결과는 이 목록의 {@code rubricItemId} 로만 돌아온다
 */
public record EssayGradingCommand(String imageUrl, List<RubricCriterion> criteria) {
}
