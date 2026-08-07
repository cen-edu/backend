package com.cenedu.backend.domain.analysis.controller;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.WorksheetDetail;
import com.cenedu.backend.domain.analysis.dto.WorksheetSummary;
import com.cenedu.backend.domain.analysis.service.WeaknessAnalysisQueryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 취약점 분석 화면이 부르는 worksheet 조회.
 *
 * <p>{@link AnalysisApiController} 와 같은 데이터를 다른 모양으로 낸다. 프론트 두 갈래가 서로
 * 다른 계약을 쓰고 있어서인데, 둘 다 고치지 않기로 해서 백엔드가 양쪽을 맞춘다. 한쪽이 정리되면
 * 그때 하나를 걷어낸다.
 *
 * <p>경로와 응답 모양이 AGENTS.md 2절·7절과 다른 이유는 {@link AnalysisApiController} 의 설명과
 * 같다.
 */
@RestController
@RequestMapping("/api/weakness-analysis")
public class WeaknessAnalysisController {

    private final WeaknessAnalysisQueryService query;

    public WeaknessAnalysisController(WeaknessAnalysisQueryService query) {
        this.query = query;
    }

    @GetMapping("/worksheets")
    public List<WorksheetSummary> worksheets() {
        return query.worksheets();
    }

    @GetMapping("/worksheets/{assessmentId}")
    public WorksheetDetail worksheet(@PathVariable String assessmentId) {
        return query.worksheet(assessmentId);
    }
}
