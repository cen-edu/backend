package com.cenedu.backend.ai.analysis.adapter;

import java.util.List;

import com.cenedu.backend.domain.analysis.report.AnalysisReportDraft;
import com.cenedu.backend.domain.analysis.report.AnalysisReportGenerationPort;
import com.cenedu.backend.domain.analysis.report.AnalysisReportRequest;
import org.springframework.stereotype.Component;

/**
 * AI 분석 문장 생성 어댑터.
 *
 * <p>교사가 프롬프트를 직접 입력하지 않는 시스템 트리거 경로라 {@code AgentDispatcher} 를 거치지
 * 않고 도메인 Port 를 구현한다(AGENTS.md 3절 4번).
 *
 * <p><b>아직 LLM 을 호출하지 않는다.</b> 상태 전이·동시성·저장 경로를 LLM 없이 먼저 검증하려고
 * 고정 문장을 돌려준다. 실제 호출은 프롬프트와 출력 계약이 확정된 뒤에 이 클래스 안에서만 바뀐다.
 */
@Component
public class AnalysisReportGenerator implements AnalysisReportGenerationPort {

    private static final String PROMPT_VERSION = "stub-1";
    private static final String MODEL_NAME = "stub";
    private static final short SCHEMA_VERSION = 1;

    @Override
    public AnalysisReportDraft generate(AnalysisReportRequest request) {
        List<AnalysisReportDraft.ItemMessageDraft> itemMessages =
                request.gradedWorksheetItemIds().stream()
                        .map(itemId -> new AnalysisReportDraft.ItemMessageDraft(
                                itemId,
                                "임시 문장입니다. 실제 관찰 내용이 들어갈 자리입니다.",
                                "임시 문장입니다. 학습 포인트가 들어갈 자리입니다.",
                                "임시 문장입니다. 재풀이 안내가 들어갈 자리입니다."))
                        .toList();
        return new AnalysisReportDraft(
                "임시 요약입니다. 전체 성취 수준과 우선 확인 영역이 들어갈 자리입니다.",
                "임시 관찰입니다. 다음 지도에서 확인할 내용이 들어갈 자리입니다.",
                itemMessages,
                PROMPT_VERSION,
                MODEL_NAME,
                SCHEMA_VERSION);
    }
}
