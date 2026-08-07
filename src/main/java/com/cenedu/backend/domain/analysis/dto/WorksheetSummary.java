package com.cenedu.backend.domain.analysis.dto;

import java.time.LocalDate;

/**
 * 취약점 분석 화면의 학습지 선택 목록 한 줄.
 *
 * <p>{@code gradeId} / {@code classId} / {@code className} 은 지금 고정값이다. 백엔드에
 * 학년·반 개념이 아직 없다. member 도메인이 생기면 실제 값으로 바꾼다. 화면이 이 자리를
 * 라벨로 쓰고 있어 비워 두면 선택 UI 가 빈칸으로 보인다.
 */
public record WorksheetSummary(
        String id,
        String title,
        LocalDate date,
        String type,
        String gradeId,
        String classId,
        String term,
        String className
) {
}
