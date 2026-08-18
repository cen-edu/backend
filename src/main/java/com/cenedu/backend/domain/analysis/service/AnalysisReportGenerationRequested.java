package com.cenedu.backend.domain.analysis.service;

/**
 * 보고서 생성 작업을 맡았음을 알리는 내부 이벤트.
 *
 * <p>생성 상태를 바꾼 트랜잭션이 <b>커밋된 뒤에</b> 작업을 시작해야 한다. 커밋 전에 시작하면 작업
 * 스레드가 아직 보이지 않는 행을 읽거나, 요청이 롤백됐는데 LLM 호출만 나가는 일이 생긴다.
 */
public record AnalysisReportGenerationRequested(
        long assignmentId,
        long assignmentStudentId
) {
}
