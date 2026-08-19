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

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 칸 하나를 채점해 기록한다. 채점 경로를 고르는 곳이기도 하다.
 *
 * <p><b>칸 하나가 트랜잭션 하나다</b>(명세 7절). 중간에 끊겨도 반쯤 쓰인 행이 남지 않고, 한 칸이
 * 실패해도 다음 칸으로 계속 갈 수 있다. 그래서 {@link Propagation#REQUIRES_NEW}로 매번 새 트랜잭션을
 * 연다 — 바깥에 트랜잭션이 있으면 한 칸의 실패가 전체를 되돌린다.
 *
 * <p><b>{@link #gradeOne} 자체에는 트랜잭션이 없다.</b> 서술형은 LLM 을 부르는 동안 커넥션을 쥐면
 * 안 되는데(타임아웃 60초 × 재시도 2회), 메서드에 트랜잭션을 걸어 두면 어느 분기로 가든 이미 열린
 * 뒤다. 그래서 <b>경로를 고른 다음</b>에 트랜잭션을 연다 — 규칙 채점은 예전처럼 한 칸이 한
 * 트랜잭션이고, 서술형은 {@link EssayGradingService} 가 읽기·호출·쓰기를 따로 연다.
 *
 * <p>실행 대상 산정·진행률은 이 클래스의 일이 아니다(단계 4).
 */
@Service
public class AnswerGradingService {

    private static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.ONE;

    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    private final GradingRubricResultRepository gradingRubricResultRepository;
    private final ProblemAnswerUnitService problemAnswerUnitService;
    private final AnswerNormalizer answerNormalizer;
    private final RuleGrader ruleGrader;
    private final EssayGradingService essayGradingService;
    private final TransactionTemplate transactionTemplate;

    public AnswerGradingService(SubmissionAnswerRepository submissionAnswerRepository,
                                WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository,
                                GradingRubricResultRepository gradingRubricResultRepository,
                                ProblemAnswerUnitService problemAnswerUnitService,
                                AnswerNormalizer answerNormalizer,
                                RuleGrader ruleGrader,
                                EssayGradingService essayGradingService,
                                PlatformTransactionManager transactionManager) {
        this.submissionAnswerRepository = submissionAnswerRepository;
        this.worksheetAssignmentStudentRepository = worksheetAssignmentStudentRepository;
        this.gradingRubricResultRepository = gradingRubricResultRepository;
        this.problemAnswerUnitService = problemAnswerUnitService;
        this.answerNormalizer = answerNormalizer;
        this.ruleGrader = ruleGrader;
        this.essayGradingService = essayGradingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // 규칙 채점은 예전 그대로 "칸 하나 = 새 트랜잭션" 이다.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 채점 한 칸의 결과. 진행률 집계가 이 값을 센다. */
    public enum Outcome { GRADED, FAILED }

    /**
     * 칸 하나를 채점하고 결과를 기록한다.
     *
     * @param maxScore 문항 배점. {@code null}이면 일반·맞춤 학습이라 만점을 {@code 1.00}으로 본다
     */
    public Outcome gradeOne(long submissionAnswerId, BigDecimal maxScore) {
        // 트랜잭션을 열기 전에 경로만 고른다. 행 전체는 각 경로가 자기 트랜잭션 안에서 다시 읽는다.
        CompareMethod compareMethod =
                submissionAnswerRepository.findCompareMethodById(submissionAnswerId).orElse(null);
        if (compareMethod == null) {
            // 채점 시작 후 지워진 칸. 다음 칸으로 계속 간다.
            return Outcome.FAILED;
        }
        if (compareMethod == CompareMethod.RUBRIC) {
            // 서술형은 LLM 이 판정한다. RuleGrader 는 결정론 전용으로 남는다.
            return essayGradingService.grade(submissionAnswerId, maxScore);
        }
        return transactionTemplate.execute(status -> gradeByRule(submissionAnswerId, compareMethod, maxScore));
    }

    /** 규칙 채점 5종. 예전 {@code gradeOne} 그대로이고, 트랜잭션 경계만 호출부로 올라갔다. */
    private Outcome gradeByRule(long submissionAnswerId, CompareMethod compareMethod,
                                BigDecimal maxScore) {
        SubmissionAnswer answer = submissionAnswerRepository.findById(submissionAnswerId).orElse(null);
        if (answer == null) {
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
     * <p><b>서술형이 든 학습지도 여기 도달한다.</b> 예전에는 {@code RUBRIC} 칸이 무조건
     * {@code FAILED}로 남아 그 학습지가 영영 완료되지 않았으나, 지금은 LLM 판정이 붙으면
     * {@code GRADED}가 된다. 배점이 없는 학습지는 점수 없이 판정만 남기고도 완료된다(D25).
     *
     * <p>다만 판정 불가({@code UNJUDGEABLE})가 하나라도 있거나 루브릭이 없는 칸은 여전히
     * {@code FAILED}다. 그 칸은 교사가 손으로 채워야 완료되고, 그래야 확정이 열린다.
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
