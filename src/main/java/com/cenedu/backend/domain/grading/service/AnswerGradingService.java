package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.grading.repository.GradingRubricResultRepository;
import com.cenedu.backend.domain.problem.entity.ProblemChoice;
import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService;
import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService.AnswerKey;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.submission.repository.SubmissionAnswerRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.global.common.enums.CompareMethod;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칸 하나를 채점해 기록한다.
 *
 * <p><b>칸 하나가 트랜잭션 하나다</b>(명세 7절). 중간에 끊겨도 반쯤 쓰인 행이 남지 않고, 한 칸이
 * 실패해도 다음 칸으로 계속 갈 수 있다. 그래서 {@link Propagation#REQUIRES_NEW}로 매번 새 트랜잭션을
 * 연다 — 바깥에 트랜잭션이 있으면 한 칸의 실패가 전체를 되돌린다.
 *
 * <p>실행 대상 산정·진행률은 이 클래스의 일이 아니다(단계 4).
 */
@Service
@RequiredArgsConstructor
public class AnswerGradingService {

    private static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.ONE;

    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final GradingRubricResultRepository gradingRubricResultRepository;
    private final ProblemAnswerUnitService problemAnswerUnitService;
    private final AnswerNormalizer answerNormalizer;
    private final RuleGrader ruleGrader;

    /** 채점 한 칸의 결과. 진행률 집계가 이 값을 센다. */
    public enum Outcome { GRADED, FAILED }

    /**
     * 칸 하나를 채점하고 결과를 기록한다.
     *
     * @param maxScore 문항 배점. {@code null}이면 일반·맞춤 학습이라 만점을 {@code 1.00}으로 본다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome gradeOne(long submissionAnswerId, BigDecimal maxScore) {
        SubmissionAnswer answer = submissionAnswerRepository.findById(submissionAnswerId).orElse(null);
        if (answer == null) {
            // 채점 시작 후 지워진 칸. 다음 칸으로 계속 간다.
            return Outcome.FAILED;
        }

        CompareMethod compareMethod = answer.getCompareMethod();
        if (compareMethod == CompareMethod.RUBRIC) {
            // auto_score 를 비워 둔다 — 0을 넣으면 "최초 기록 후 불변" 때문에 영원히 0으로 굳어
            // task_06b 가 진짜 자동채점값을 기록할 수 없다(명세 7절).
            answer.recordGradingFailure(null, "서술형 자동채점 미구현");
            return Outcome.FAILED;
        }

        AnswerKey answerKey = problemAnswerUnitService
                .getAnswerKeysByIds(List.of(answer.getAnswerUnitId()))
                .get(answer.getAnswerUnitId());
        if (answerKey == null) {
            answer.recordGradingFailure(null, "채점 칸의 정답을 찾을 수 없음");
            return Outcome.FAILED;
        }

        if (compareMethod == CompareMethod.CHOICE) {
            return recordChoice(answer, answerKey, maxScore);
        }
        return recordComparison(answer, answerKey, compareMethod, maxScore);
    }

    /** 객관식. {@code answer_raw}가 1-based 표시 순번이라 보기 ID 로 풀어서 비교한다. */
    private Outcome recordChoice(SubmissionAnswer answer, AnswerKey answerKey, BigDecimal maxScore) {
        Long correctChoiceId = findCorrectChoiceId(answer.getAnswerUnitId(), answerKey.answerRaw());
        RuleGrader.Verdict verdict = ruleGrader.gradeChoice(answer.getSelectedChoiceId(), correctChoiceId);
        // 객관식은 정규화할 문자열이 없다 — 판정은 보기 ID 로 끝났다.
        return record(answer, verdict, null, maxScore);
    }

    private Outcome recordComparison(SubmissionAnswer answer, AnswerKey answerKey,
                                     CompareMethod compareMethod, BigDecimal maxScore) {
        String studentAnswer = answerNormalizer.normalize(answer.getRawLatex(), answerKey.displayUnit());
        String correctAnswer = answerNormalizer.normalize(
                answerKey.answerNormalized() != null ? answerKey.answerNormalized() : answerKey.answerRaw(),
                answerKey.displayUnit());
        RuleGrader.Verdict verdict = ruleGrader.grade(compareMethod, studentAnswer, correctAnswer);
        return record(answer, verdict, studentAnswer, maxScore);
    }

    private Outcome record(SubmissionAnswer answer, RuleGrader.Verdict verdict,
                           String normalized, BigDecimal maxScore) {
        if (verdict.isFailure()) {
            answer.recordGradingFailure(normalized, truncate(verdict.failureReason()));
            return Outcome.FAILED;
        }
        BigDecimal fullScore = maxScore != null ? maxScore : DEFAULT_MAX_SCORE;
        answer.recordAutoScore(normalized, verdict.correct() ? fullScore : BigDecimal.ZERO);
        return Outcome.GRADED;
    }

    /**
     * 정답 보기의 ID. {@code answer_raw}는 1-based 표시 순번이고 {@code display_order}는 0-based 라
     * 한 칸 밀어서 찾는다.
     */
    private Long findCorrectChoiceId(long answerUnitId, String answerRaw) {
        if (answerRaw == null || !answerRaw.trim().matches("\\d+")) {
            return null;
        }
        long questionId = problemAnswerUnitService.getQuestionId(answerUnitId);
        int oneBasedOrder = Integer.parseInt(answerRaw.trim());
        Map<Integer, Long> idsByOrder = gradingRubricResultRepository
                .findChoicesByQuestionIdIn(List.of(questionId)).stream()
                .collect(Collectors.toMap(choice -> (int) choice.getDisplayOrder() + 1,
                        ProblemChoice::getId, (first, second) -> first));
        return idsByOrder.get(oneBasedOrder);
    }

    /** {@code failure_reason}은 100자 컬럼이다. 넘치면 DB 제약 위반으로 500이 난다. */
    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 100 ? reason : reason.substring(0, 100);
    }

    /**
     * 학생의 전 칸이 {@code GRADED}면 채점 완료로 표시한다(명세 7절 6번).
     *
     * <p>서술형이 든 학습지는 여기 도달하지 못한다 — {@code RUBRIC} 칸이 {@code FAILED}로 남기
     * 때문이다. 교사가 그 칸을 손으로 채워야 완료되고, 그래야 확정이 열린다. 정상 동작이다.
     *
     * @param assessment 종합평가면 총점을 다시 계산한다. 일반·맞춤 학습은 {@code total_score}가
     *                   "종합평가 전용" 컬럼이라 채우지 않는다
     * @return 채점 완료로 바뀌었으면 {@code true}
     */
    @Transactional
    public boolean finalizeStudent(long assignmentStudentId, boolean assessment) {
        List<SubmissionAnswer> answers =
                submissionAnswerRepository.findByAssignmentStudentId(assignmentStudentId);
        if (answers.isEmpty()
                || !answers.stream().allMatch(a -> a.getGradingStatus() == GradingStatus.GRADED)) {
            return false;
        }
        BigDecimal totalScore = assessment
                ? answers.stream()
                        .map(SubmissionAnswer::getFinalScore)
                        .filter(score -> score != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        return worksheetAssignmentStudentRepository.findById(assignmentStudentId)
                .map(student -> {
                    student.markGraded(OffsetDateTime.now(), totalScore);
                    return true;
                })
                .orElse(false);
    }
}
