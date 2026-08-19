package com.cenedu.backend.domain.analysis.report.pdf;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisStudentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.ClassAnalysisOverviewResponse;

/**
 * 학급 분석 PDF 에 들어갈 값을 템플릿이 읽기 좋은 모양으로 모은 것.
 *
 * <p>학습지 유형에 따라 구성이 갈린다. 학습평가는 평가 영역과 소분류 행렬을, 종합평가는 문항
 * 유형과 문항 행렬에 점수·시간 분포까지 본다. <b>그 판단을 템플릿에 두면 조건문이 화면 구조를
 * 덮으므로</b> 여기서 같은 모양으로 맞춰 담는다.
 *
 * @param matrix     소분류 × 학생 또는 문항 × 학생 행렬. 두 경우의 열 이름만 다르고 구조는 같다
 * @param scoreTimes 종합평가에만 있다. 산점도는 CSS 로 그릴 수 없어 표로 낸다
 */
public record ClassReportView(
        ClassAnalysisOverviewResponse overview,
        List<AnalysisStudentListResponse.StudentItem> students,
        String comparisonTitle,
        List<StudentReportView.ComparisonBar> comparisonBars,
        List<StudentReportView.ComparisonBar> difficultyBars,
        List<PriorityItem> priorityItems,
        String priorityTypeColumnLabel,
        Matrix matrix,
        List<ScoreTimeRow> scoreTimes,
        BigDecimal scoreMedian,
        Long durationMedian,
        String generatedAt
) {
    public ClassReportView {
        students = List.copyOf(students);
        comparisonBars = List.copyOf(comparisonBars);
        difficultyBars = List.copyOf(difficultyBars);
        priorityItems = List.copyOf(priorityItems);
        scoreTimes = List.copyOf(scoreTimes);
    }

    public boolean hasScoreTimes() {
        return !scoreTimes.isEmpty();
    }

    /** 우선 확인 문항에 분류 열을 보일지. 값이 하나도 없으면 빈 열을 인쇄하지 않는다. */
    public boolean showPriorityType() {
        return priorityItems.stream().anyMatch(item -> item.typeLabel() != null);
    }

    /**
     * 학급 정답률이 낮아 먼저 볼 문항.
     *
     * @param typeLabel 학습평가면 평가 영역, 종합평가면 문항 유형. 종합평가 문항에는 평가
     *                  영역이 없어 분류 축이 유형뿐이다
     */
    public record PriorityItem(
            int itemNumber,
            String questionTitle,
            String typeLabel,
            String difficultyBand,
            int correctStudentCount,
            int gradedStudentCount
    ) {
    }

    /**
     * 학생 × 무엇 행렬.
     *
     * @param columnLabels 소분류 이름 또는 문항 번호
     */
    public record Matrix(
            String title,
            List<String> columnLabels,
            List<Row> rows
    ) {
        public Matrix {
            columnLabels = List.copyOf(columnLabels);
            rows = List.copyOf(rows);
        }

        public boolean isEmpty() {
            return rows.isEmpty() || columnLabels.isEmpty();
        }

        public record Row(String studentName, List<Cell> cells) {
            public Row {
                cells = List.copyOf(cells);
            }
        }

        /**
         * 칸 하나.
         *
         * @param heatClass 명도 단계. 색상 대신 명도로 나눠 흑백 출력에서도 구분된다
         */
        public record Cell(String text, String heatClass) {
        }
    }

    /** 점수와 풀이시간. 산점도 대신 표로 낸다. */
    public record ScoreTimeRow(
            String studentName,
            String analysisStatus,
            BigDecimal scoreRate,
            Long totalSolvingDurationMs
    ) {

        /** 분 단위로 읽기 좋게 바꾼다. 밀리초를 그대로 인쇄하면 교사가 계산해야 한다. */
        public String durationText() {
            if (totalSolvingDurationMs == null) {
                return "-";
            }
            long totalSeconds = totalSolvingDurationMs / 1000;
            return "%d분 %d초".formatted(totalSeconds / 60, totalSeconds % 60);
        }
    }
}
