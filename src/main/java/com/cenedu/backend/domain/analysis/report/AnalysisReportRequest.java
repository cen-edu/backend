package com.cenedu.backend.domain.analysis.report;

import java.util.List;

/**
 * AI 문장 생성에 넘기는 정제된 요청. analysis 도메인이 소유하는 AI 독립 계약이다.
 *
 * <p>학생 답안 원문처럼 학생이 직접 쓴 텍스트가 들어올 자리이므로, 이 객체를 만드는 쪽이
 * 길이 제한과 인젝션 방어를 마친 값만 담는다. 구현체는 이 값을 그대로 신뢰한다.
 *
 * @param assignmentStudentId    학생 학습지 수행 회차
 * @param gradedWorksheetItemIds 채점이 끝나 문장을 만들 문항. 이 집합 밖의 문항은 응답에 와도 버린다
 */
public record AnalysisReportRequest(
        long assignmentStudentId,
        List<Long> gradedWorksheetItemIds
) {
    public AnalysisReportRequest {
        gradedWorksheetItemIds = List.copyOf(gradedWorksheetItemIds);
    }
}
