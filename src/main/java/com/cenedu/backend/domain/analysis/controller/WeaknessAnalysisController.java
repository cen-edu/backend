package com.cenedu.backend.domain.analysis.controller;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.WorksheetDetail;
import com.cenedu.backend.domain.analysis.dto.WorksheetSummary;
import com.cenedu.backend.domain.analysis.service.WeaknessAnalysisQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "취약점 분석 (worksheet)",
        description = "취약점 분석 화면이 쓰는 조회. 화면 한 벌을 한 번에 받는 구조라 새 화면은 "
                + "이쪽을 쓰는 것이 좋다.")
@RestController
@RequestMapping("/api/weakness-analysis")
public class WeaknessAnalysisController {

    private final WeaknessAnalysisQueryService query;

    public WeaknessAnalysisController(WeaknessAnalysisQueryService query) {
        this.query = query;
    }

    @Operation(summary = "학습지 목록",
            description = """
                    학습지 선택 드롭다운에 쓰는 목록. 최근 회차가 먼저 온다.

                    `gradeId` / `classId` / `className` 은 현재 고정값이다. 백엔드에 학년·반
                    개념이 아직 없어서이며, member 도메인이 생기면 실제 값으로 바뀐다. 화면이
                    이 자리를 라벨로 쓰고 있어 비워 두지 않는다.

                    `type` 은 `assessment`(종합평가) 또는 `practice`(일반 학습)다.
                    """)
    @GetMapping("/worksheets")
    public List<WorksheetSummary> worksheets() {
        return query.worksheets();
    }

    @Operation(summary = "학습지 한 벌",
            description = """
                    화면이 통째로 읽는 구조. 문항·학생·응답·구간이 모두 들어 있어 이 호출 하나로
                    취약점 분석 화면을 그릴 수 있다.

                    `students[].responses[].steps[].attempted` 를 주의해서 쓸 것. 학생이 그
                    구간까지 도달했는지를 뜻하며, **`input` 이 비었는지로 대신 판단하면 안 된다.**
                    빈칸을 안 푼 것으로 보고 모수에서 빼면 분모가 "얼마나 멀리 갔는가"가 되어,
                    앞에서 막힌 학생일수록 달성률이 높게 나온다.

                    `students[].status` 는 `stable` / `review` / `priority` / `insufficient` 다.
                    `insufficient` 는 낸 문항을 다 풀지 않은 학생이며, 학급 평균과 취약 판정에서
                    제외해야 한다.

                    데이터가 없으면 `404 ASSESSMENT_NOT_FOUND`.
                    """)
    @GetMapping("/worksheets/{assessmentId}")
    public WorksheetDetail worksheet(
            @Parameter(description = "학습지(평가) 식별자", example = "SIM-M1-PRIME-SUMMATIVE10-V1")
            @PathVariable String assessmentId) {
        return query.worksheet(assessmentId);
    }
}
