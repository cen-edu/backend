package com.cenedu.backend.domain.worksheet.repository.row;

/**
 * 요약 카드의 배정-학생 집계 세 값. 전 행을 읽어 자바에서 세지 않고 집계 쿼리 한 번으로 낸다.
 *
 * <p>연인원이다. 한 학생이 학습지 셋에 배정되어 둘을 냈으면 {@code submitted}에 2로 센다.
 */
public record LearningStatusSummaryRow(long submitted, long inProgress, long unsubmitted) {
}
