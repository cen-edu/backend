package com.cenedu.backend.domain.analysis.report;

/**
 * AI 분석 문장 생성 실행 지점. 구현은 {@code ai/analysis/adapter} 가 맡는다.
 *
 * <p>교사가 프롬프트를 직접 입력하지 않는 시스템 트리거 경로라 {@code AgentDispatcher} 를 거치지
 * 않는다(AGENTS.md 3절 4번). 대신 <b>학생이 답안란에 쓴 텍스트가 프롬프트에 들어가므로 인젝션
 * 방어는 analysis 도메인의 책임</b>이다. 도메인이 검증·조립한 요청만 이 Port 로 넘긴다.
 *
 * <p>이 인터페이스가 도메인에 있는 이유는 도메인이 AI 구현을 몰라야 하기 때문이다. 여기서는
 * {@code ai/client}, OpenAI SDK, Spring AI 를 참조하지 않는다.
 */
public interface AnalysisReportGenerationPort {

    /**
     * 정제된 요청으로 문장을 생성한다.
     *
     * @throws RuntimeException 호출에 실패하거나 응답을 해석할 수 없을 때. 호출부가 잡아
     *                          보고서를 생성 실패 상태로 되돌린다
     */
    AnalysisReportDraft generate(AnalysisReportRequest request);
}
