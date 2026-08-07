package com.cenedu.backend.domain.analysis.service;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.StudentReview;
import com.cenedu.backend.domain.analysis.entity.AnalysisAssessment;
import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;
import com.cenedu.backend.domain.analysis.repository.AnalysisAssessmentRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisAttemptRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 회차를 마친 학생에게 자기 답과 정답을 돌려준다. */
@Service
@Transactional(readOnly = true)
public class StudentReviewService {

    private final AnalysisAssessmentRepository assessments;
    private final AnalysisAttemptRepository attempts;
    private final ObjectMapper json;

    public StudentReviewService(AnalysisAssessmentRepository assessments,
                                AnalysisAttemptRepository attempts,
                                ObjectMapper json) {
        this.assessments = assessments;
        this.attempts = attempts;
        this.json = json;
    }

    public StudentReview review(String assessmentId, String studentId) {
        AnalysisAssessment assessment = assessments
                .findByAssessmentIdAndStudentId(assessmentId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND));
        if (!assessment.completed()) {
            throw new BusinessException(ErrorCode.ASSESSMENT_NOT_COMPLETED);
        }

        // 기록되지 않은 문항은 학생에게 보여줄 답이 없다. 그대로 두면 빈 답에 오답 표시가
        // 붙어 학생이 자기가 틀린 것으로 읽는다. 정답 수의 분모에서도 빠져야 한다.
        List<AnalysisAttempt> graded = attempts
                .findByAssessmentIdAndStudentIdOrderByProblemNumberAscOccurredAtAscEventIdAsc(
                        assessmentId, studentId)
                .stream()
                .filter(attempt -> !attempt.isSubmissionFailed())
                .toList();

        List<StudentReview.ReviewItem> problems = graded.stream()
                .map(attempt -> new StudentReview.ReviewItem(
                        attempt.getProblemNumber(), attempt.getProblemTitle(),
                        attempt.getProblemText(),
                        readList(attempt.getChoicesJson(), new TypeReference<List<String>>() { },
                                "보기"),
                        attempt.getResponseType(), attempt.getStudentAnswer(),
                        attempt.getCorrectAnswer(), attempt.isCorrect(), attempt.isHintUsed(),
                        readSteps(attempt.getStepResponsesJson())))
                .toList();

        int correct = (int) graded.stream().filter(AnalysisAttempt::isCorrect).count();

        return new StudentReview(
                assessmentId, assessment.getAssessmentTitle(), assessment.getAssessmentDate(),
                studentId, assessment.getStudentName(),
                graded.size(), correct, problems);
    }

    private List<StudentReview.ReviewStep> readSteps(String value) {
        return readList(value, new TypeReference<List<StoredStep>>() { }, "단계 응답").stream()
                .map(step -> new StudentReview.ReviewStep(
                        step.stepName(), step.studentAnswer(), step.correctAnswer()))
                .toList();
    }

    private <T> List<T> readList(String value, TypeReference<List<T>> type, String label) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException(label + " JSON 읽기 실패", e);
        }
    }

    /**
     * 저장된 단계 응답의 모양.
     *
     * <p>{@code stepId} 와 {@code category} 도 들어 있지만 화면에 돌려주지 않는다. 구간 라벨은
     * 아직 검증되지 않은 값이라 학생에게 문장으로 내보내지 않는다.
     */
    private record StoredStep(String stepId, String stepName, String category,
                              String studentAnswer, String correctAnswer, boolean correct) {
    }
}
