package com.cenedu.backend.domain.analysis.service;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.AssessmentListItem;
import com.cenedu.backend.domain.analysis.dto.ClassDashboard;
import com.cenedu.backend.domain.analysis.dto.StudentDetail;
import com.cenedu.backend.domain.analysis.repository.AnalysisAggregateRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 풀이를 취약점 분석 화면에 필요한 값으로만 집계한다. */
@Service
@Transactional(readOnly = true)
public class ClassDashboardService {

    private static final int STABLE_RATE = 80;
    private static final int REVIEW_RATE = 60;

    private final AnalysisAggregateRepository aggregates;

    public ClassDashboardService(AnalysisAggregateRepository aggregates) {
        this.aggregates = aggregates;
    }

    /** 교사가 지금까지 실시한 회차 목록. */
    public List<AssessmentListItem> assessments() {
        return aggregates.findAssessmentList().stream()
                .map(row -> new AssessmentListItem(
                        row.getAssessmentId(), row.getAssessmentTitle(), row.getAssessmentDate(),
                        row.getAssessmentType(), row.getIsSimulation(),
                        row.getStudentCount(), row.getProblemCount(), row.getAttemptCount(),
                        percent(row.getCorrectCount(), row.getAttemptCount()),
                        row.getLowCount(), row.getMediumCount(), row.getHighCount()))
                .toList();
    }

    public ClassDashboard summary(String assessmentId) {
        List<AnalysisAggregateRepository.AssessmentInfoRow> info =
                aggregates.findAssessmentInfo(assessmentId);
        if (info.isEmpty()) {
            throw new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND,
                    "평가를 찾을 수 없습니다: " + assessmentId);
        }
        if (info.size() != 1) {
            // 같은 회차를 푼 학생들이 서로 다른 제목·날짜를 들고 있다. 어느 쪽이 맞는지
            // 고를 근거가 없으므로 조용히 한쪽을 쓰지 않는다.
            throw new BusinessException(ErrorCode.ASSESSMENT_HEADER_CONFLICT);
        }
        AnalysisAggregateRepository.AssessmentInfoRow header = info.get(0);

        List<ClassDashboard.StudentRow> students = aggregates.findStudentAggregates(assessmentId)
                .stream()
                .map(row -> {
                    int rate = percent(row.getCorrectCount(), row.getTotalCount());
                    return new ClassDashboard.StudentRow(
                            row.getStudentId(), row.getStudentName(), row.getTotalCount(),
                            row.getCorrectCount(), rate, row.getHintCount(), status(rate));
                })
                .toList();

        List<ClassDashboard.ProblemRow> problems = aggregates.findProblemAggregates(assessmentId)
                .stream()
                .map(row -> new ClassDashboard.ProblemRow(
                        row.getProblemNumber(), row.getProblemId(), row.getProblemTitle(),
                        row.getEvaluationArea(), row.getTopic(), row.getSourceDataset(),
                        row.getDifficultyBand(), row.getReferenceSuccessRate(),
                        row.getTotalCount(), row.getCorrectCount(),
                        percent(row.getCorrectCount(), row.getTotalCount())))
                .toList();

        int attempts = students.stream().mapToInt(ClassDashboard.StudentRow::totalCount).sum();
        int correct = students.stream().mapToInt(ClassDashboard.StudentRow::correctCount).sum();
        int hints = students.stream().mapToInt(ClassDashboard.StudentRow::hintCount).sum();
        long attention = students.stream()
                .filter(student -> "priority".equals(student.status())).count();

        return new ClassDashboard(
                assessmentId, header.getAssessmentTitle(), header.getAssessmentDate(),
                header.getAssessmentType(), header.getIsSimulation(),
                new ClassDashboard.Overall(students.size(), problems.size(), attempts, correct,
                        percent(correct, attempts), hints, attention),
                students, problems,
                difficulties(assessmentId, null),
                classAreas(assessmentId));
    }

    public StudentDetail studentDetail(String assessmentId, String studentId) {
        ClassDashboard dashboard = summary(assessmentId);
        ClassDashboard.StudentRow student = dashboard.students().stream()
                .filter(row -> row.studentId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_ATTEMPT_NOT_FOUND));

        List<StudentDetail.AttemptRow> attempts =
                aggregates.findStudentAttempts(assessmentId, studentId).stream()
                        .map(row -> new StudentDetail.AttemptRow(
                                row.getProblemNumber(), row.getProblemId(), row.getProblemTitle(),
                                row.getProblemText(), row.getEvaluationArea(),
                                row.getIsCorrect(), row.getHintUsed(),
                                row.getReferenceSuccessRate(), row.getDifficultyBand(),
                                row.getSourceDataset(), row.getSourceDifficulty(),
                                row.getDifficultyBasis(),
                                percent(row.getClassCorrect(), row.getClassTotal())))
                        .toList();

        List<StudentDetail.AreaRow> areas =
                aggregates.findStudentAreaAggregates(assessmentId, studentId).stream()
                        .map(row -> new StudentDetail.AreaRow(
                                row.getEvaluationArea(), row.getStudentTotal(),
                                row.getStudentCorrect(),
                                percent(row.getStudentCorrect(), row.getStudentTotal()),
                                row.getClassTotal(), row.getClassCorrect(),
                                percent(row.getClassCorrect(), row.getClassTotal())))
                        .toList();

        return new StudentDetail(
                dashboard.assessmentId(), dashboard.assessmentTitle(),
                dashboard.assessmentDate(), dashboard.assessmentType(),
                dashboard.simulation(), dashboard.overall().studentCount(),
                dashboard.overall().correctRatePercent(),
                student, areas, attempts, difficulties(assessmentId, studentId));
    }

    private List<ClassDashboard.DifficultyRow> difficulties(String assessmentId, String studentId) {
        return aggregates.findDifficultyAggregates(assessmentId, studentId).stream()
                .map(row -> new ClassDashboard.DifficultyRow(
                        row.getDifficultyBand(), row.getProblemCount(), row.getTotalCount(),
                        row.getCorrectCount(),
                        percent(row.getCorrectCount(), row.getTotalCount())))
                .toList();
    }

    private List<ClassDashboard.ClassAreaRow> classAreas(String assessmentId) {
        return aggregates.findClassAreaAggregates(assessmentId).stream()
                .map(row -> new ClassDashboard.ClassAreaRow(
                        row.getEvaluationArea(), row.getProblemCount(), row.getTotalCount(),
                        row.getCorrectCount(),
                        percent(row.getCorrectCount(), row.getTotalCount())))
                .toList();
    }

    /** 분모가 0이면 0으로 둔다. 푼 문항이 없는 것과 전부 틀린 것을 화면에서 같게 보이지 않게 한다. */
    private static int percent(int value, int total) {
        return total == 0 ? 0 : (int) Math.round(value * 100.0 / total);
    }

    /**
     * 정답률로 학생 상태를 나눈다. 프론트 라벨과 같은 코드를 쓴다.
     *
     * <p>취약점 판정({@link WeaknessAnalyzer})과는 다른 축이다. 이쪽은 회차 전체의 정답률이고,
     * 저쪽은 한 풀이 단계의 관찰 결과다. 합치지 않는다.
     */
    private static String status(int rate) {
        if (rate >= STABLE_RATE) {
            return "stable";
        }
        if (rate >= REVIEW_RATE) {
            return "review";
        }
        return "priority";
    }
}
