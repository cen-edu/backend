package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.grading.entity.GradingRubricResult;
import com.cenedu.backend.domain.grading.port.EssayGradingCommand;
import com.cenedu.backend.domain.grading.port.EssayGradingPort;
import com.cenedu.backend.domain.grading.port.EssayGradingResult;
import com.cenedu.backend.domain.grading.port.EssayGradingStatus;
import com.cenedu.backend.domain.grading.port.RubricCriterion;
import com.cenedu.backend.domain.grading.port.RubricJudgement;
import com.cenedu.backend.domain.grading.port.RubricVerdict;
import com.cenedu.backend.domain.grading.repository.GradingRubricResultRepository;
import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;
import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.domain.submission.repository.SubmissionAnswerRepository;
import com.cenedu.backend.domain.submission.service.SubmissionImageService;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 서술형 칸 하나를 채점한다. {@code RUBRIC} 분기가 여기로 갈라져 나온다.
 *
 * <p><b>트랜잭션을 셋으로 쪼갠다.</b> 규칙 채점처럼 한 칸을 한 트랜잭션으로 두면 LLM 이 답할 때까지
 * 커넥션을 쥔 채로 있게 된다 — 타임아웃 60초에 재시도 2회면 한 칸이 3분까지 간다. 60칸이면 풀이
 * 마른다.
 *
 * <pre>
 * ① 짧은 읽기   루브릭 · 답안 · 이미지 키를 꺼내 값으로 복사한다
 * ② 밖          presigned URL 발급 → LLM 도구 루프
 * ③ 짧은 쓰기   판정 저장 + 점수 기록
 * </pre>
 *
 * <p><b>③ 의 둘은 반드시 한 트랜잭션이다.</b> 나누면 판정 행은 저장됐는데 점수가 없는 중간 상태가
 * 남을 수 있고, 그 칸은 교사 화면에서 "채점 기준은 체크돼 있는데 점수는 비어 있는" 모양이 된다.
 *
 * <p>자기 자신을 불러서는 프록시를 타지 못해 {@code @Transactional} 이 걸리지 않는다. 그래서
 * {@link TransactionTemplate} 로 경계를 코드에 드러낸다 —
 * {@code ProblemCandidateProcessingService} 가 같은 이유로 같은 방식을 쓴다.
 */
@Service
public class EssayGradingService {

    private static final Logger log = LoggerFactory.getLogger(EssayGradingService.class);

    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final GradingRubricResultRepository gradingRubricResultRepository;
    private final ProblemAnswerUnitService problemAnswerUnitService;
    private final ObjectProvider<SubmissionImageService> submissionImageServiceProvider;
    private final EssayGradingPort essayGradingPort;
    private final EssayScoreCalculator essayScoreCalculator;
    private final TransactionTemplate transactionTemplate;

    public EssayGradingService(SubmissionAnswerRepository submissionAnswerRepository,
                               GradingRubricResultRepository gradingRubricResultRepository,
                               ProblemAnswerUnitService problemAnswerUnitService,
                               ObjectProvider<SubmissionImageService> submissionImageServiceProvider,
                               EssayGradingPort essayGradingPort,
                               EssayScoreCalculator essayScoreCalculator,
                               PlatformTransactionManager transactionManager) {
        this.submissionAnswerRepository = submissionAnswerRepository;
        this.gradingRubricResultRepository = gradingRubricResultRepository;
        this.problemAnswerUnitService = problemAnswerUnitService;
        this.submissionImageServiceProvider = submissionImageServiceProvider;
        this.essayGradingPort = essayGradingPort;
        this.essayScoreCalculator = essayScoreCalculator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 서술형 칸 하나를 채점하고 결과를 기록한다.
     *
     * @param maxScore 문항 배점. {@code null}이면 일반·맞춤 학습이라 점수 없이 판정만 남긴다(D25)
     */
    public AnswerGradingService.Outcome grade(long submissionAnswerId, BigDecimal maxScore) {
        Loaded loaded = transactionTemplate.execute(status -> load(submissionAnswerId));
        if (loaded == null) {
            // 채점 시작 후 지워진 칸. 기록할 행이 없다.
            return AnswerGradingService.Outcome.FAILED;
        }
        if (loaded.failure() != null) {
            return fail(submissionAnswerId, loaded.failure(), null, null);
        }

        String imageUrl;
        try {
            imageUrl = createImageUrl(loaded);
        } catch (BusinessException exception) {
            EssayGradingFailure failure = exception.getErrorCode() == ErrorCode.IMAGE_NOT_FOUND
                    ? EssayGradingFailure.NO_IMAGE
                    : EssayGradingFailure.IMAGE_STORE_UNAVAILABLE;
            return fail(submissionAnswerId, failure, null, null);
        }
        if (imageUrl == null) {
            return fail(submissionAnswerId, EssayGradingFailure.IMAGE_STORE_UNAVAILABLE, null, null);
        }

        EssayGradingResult result;
        try {
            result = essayGradingPort.grade(new EssayGradingCommand(imageUrl, loaded.criteria()));
        } catch (RuntimeException exception) {
            // 예외 본문을 남기지 않는다 — 모델 응답 조각이 실려 오면 그것이 곧 학생 답안이다.
            log.warn("[서술형] 채점 호출 실패 submissionAnswerId={} 예외={}",
                    submissionAnswerId, exception.getClass().getSimpleName());
            return fail(submissionAnswerId, EssayGradingFailure.LLM_ERROR, null, null);
        }

        return transactionTemplate.execute(status -> save(loaded, result, maxScore));
    }

    // ===== ① 짧은 읽기 =====

    /**
     * 채점에 필요한 것만 값으로 복사한다. <b>엔티티를 들고 나가지 않는다</b> — 트랜잭션이 닫힌 뒤에
     * 지연 로딩을 건드리면 그 자리에서 터진다.
     *
     * @return 칸이 없으면 {@code null}. 채점할 수 없는 칸이면 {@code failure} 가 채워진다
     */
    private Loaded load(long submissionAnswerId) {
        SubmissionAnswer answer = submissionAnswerRepository.findById(submissionAnswerId).orElse(null);
        if (answer == null) {
            return null;
        }
        boolean hasText = answer.getRawLatex() != null && !answer.getRawLatex().isBlank();
        boolean hasImage = answer.getAnswerImageRef() != null;
        if (!hasText && !hasImage) {
            return Loaded.unusable(EssayGradingFailure.NO_ANSWER);
        }
        if (!hasImage) {
            // 채점 입력이 이미지다(D1). 텍스트만 있는 칸은 VLM 에 넣을 것이 없다.
            return Loaded.unusable(EssayGradingFailure.NO_IMAGE);
        }

        long questionId = problemAnswerUnitService.getQuestionId(answer.getAnswerUnitId());
        List<ProblemRubricItem> rubricItems =
                gradingRubricResultRepository.findRubricItemsByQuestionIdIn(List.of(questionId));
        if (rubricItems.isEmpty()) {
            // 문제은행 ESSAY 303건이 여기로 온다. 판정할 기준이 없으면 채점이 성립하지 않는다.
            return Loaded.unusable(EssayGradingFailure.NO_RUBRIC);
        }

        // 표시 순서로 세운다. 프롬프트에 실리는 순서가 실행마다 달라지면 측정이 흔들린다.
        List<ProblemRubricItem> ordered = rubricItems.stream()
                .sorted(Comparator.comparingInt(ProblemRubricItem::getDisplayOrder)
                        .thenComparing(ProblemRubricItem::getId))
                .toList();
        List<RubricCriterion> criteria = ordered.stream()
                .map(item -> new RubricCriterion(item.getId(), item.getLabel()))
                .toList();
        Map<Long, Short> weights = new LinkedHashMap<>();
        ordered.forEach(item -> weights.put(item.getId(), item.getWeight()));

        return new Loaded(answer.getId(), answer.getAssignmentStudentId(), answer.getAnswerUnitId(),
                criteria, Map.copyOf(weights), null);
    }

    // ===== ② 트랜잭션 밖 =====

    /** 채점 직전에 칸마다 발급한다(D4). 저장소가 꺼져 있으면 {@code null} 이다. */
    private String createImageUrl(Loaded loaded) {
        SubmissionImageService imageService = submissionImageServiceProvider.getIfAvailable();
        if (imageService == null) {
            return null;
        }
        return imageService.createGradingPipelineUrl(
                loaded.assignmentStudentId(), loaded.answerUnitId());
    }

    // ===== ③ 짧은 쓰기 =====

    /** 판정 저장과 점수 기록을 한 트랜잭션에 묶는다. */
    private AnswerGradingService.Outcome save(Loaded loaded, EssayGradingResult result,
                                              BigDecimal maxScore) {
        SubmissionAnswer answer = submissionAnswerRepository.findById(loaded.answerId()).orElse(null);
        if (answer == null) {
            return AnswerGradingService.Outcome.FAILED;
        }
        // 전사문은 채점이 소유하고 아무도 읽지 않는 칸에 넣는다(D19). 마이그레이션이 없는 이유다.
        String transcription = result.transcription();

        // 판정이 붙은 항목은 실패한 시도에서도 남긴다 — 교사가 다시 볼 때 처음부터 하지 않아도 된다.
        upsertJudgements(loaded.answerId(), result.judgements());

        if (!result.isJudged()) {
            EssayGradingFailure failure = result.status() == EssayGradingStatus.MALFORMED_OUTPUT
                    ? EssayGradingFailure.MALFORMED
                    : EssayGradingFailure.TURN_LIMIT;
            answer.recordGradingFailure(transcription, failure.reason(null));
            return AnswerGradingService.Outcome.FAILED;
        }

        EssayScoreCalculator.Outcome scored =
                essayScoreCalculator.calculate(maxScore, loaded.weights(), result.judgements());
        return switch (scored.reason()) {
            case SCORED -> {
                answer.recordAutoScore(transcription, scored.score());
                yield AnswerGradingService.Outcome.GRADED;
            }
            // D25 — 배점이 없는 것은 실패가 아니라 정상 종점이다. FAILED 로 두면 그 학습지는
            // 영원히 채점 완료가 되지 않아 확정이 열리지 않는다.
            case NO_MAX_SCORE -> {
                answer.recordAutoScore(transcription, null);
                yield AnswerGradingService.Outcome.GRADED;
            }
            case UNJUDGEABLE_PRESENT -> {
                answer.recordGradingFailure(transcription, EssayGradingFailure.UNJUDGEABLE
                        .reason(unjudgeableIds(result.judgements())));
                yield AnswerGradingService.Outcome.FAILED;
            }
            case NO_WEIGHT -> {
                answer.recordGradingFailure(transcription,
                        EssayGradingFailure.NO_RUBRIC.reason(null));
                yield AnswerGradingService.Outcome.FAILED;
            }
        };
    }

    /**
     * 판정을 저장한다. <b>{@link RubricVerdict#UNJUDGEABLE} 은 행을 만들지 않는다</b>(D13) —
     * "판정하지 않았다" 와 "판정했더니 미충족" 을 boolean 한 칸에 넣으면 그 구분이 사라진다.
     *
     * <p>{@code uk_grading_rubric_result} 가 (답안, 기준 항목) 을 묶으므로 이미 있는 행은
     * 갈아 끼운다. 실패한 칸이 다시 대상이 되는 경로가 이것을 필요로 한다.
     */
    private void upsertJudgements(long answerId, List<RubricJudgement> judgements) {
        Map<Long, GradingRubricResult> existing = new LinkedHashMap<>();
        gradingRubricResultRepository.findByStudentAnswerIdIn(List.of(answerId))
                .forEach(row -> existing.put(row.getRubricItemId(), row));

        List<GradingRubricResult> created = new ArrayList<>();
        for (RubricJudgement judgement : judgements) {
            if (judgement.verdict() == RubricVerdict.UNJUDGEABLE) {
                continue;
            }
            boolean satisfied = judgement.verdict() == RubricVerdict.SATISFIED;
            GradingRubricResult row = existing.get(judgement.rubricItemId());
            if (row != null) {
                row.updateJudgement(satisfied, judgement.evidence());
            } else {
                created.add(GradingRubricResult.create(
                        answerId, judgement.rubricItemId(), satisfied, judgement.evidence()));
            }
        }
        if (!created.isEmpty()) {
            gradingRubricResultRepository.saveAll(created);
        }
    }

    /** 어느 기준을 못 가렸는지. 교사가 그 항목부터 보게 하는 값이라 id 만 남긴다. */
    private static String unjudgeableIds(List<RubricJudgement> judgements) {
        return "rubricItemId=" + judgements.stream()
                .filter(judgement -> judgement.verdict() == RubricVerdict.UNJUDGEABLE)
                .map(judgement -> String.valueOf(judgement.rubricItemId()))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    /** 점수를 내지 못한 칸을 기록한다. {@code auto_score} 는 건드리지 않아 다시 대상이 된다. */
    private AnswerGradingService.Outcome fail(long submissionAnswerId, EssayGradingFailure failure,
                                              String detail, String transcription) {
        transactionTemplate.executeWithoutResult(status ->
                submissionAnswerRepository.findById(submissionAnswerId).ifPresent(answer ->
                        answer.recordGradingFailure(transcription, failure.reason(detail))));
        return AnswerGradingService.Outcome.FAILED;
    }

    /**
     * ① 이 꺼내 온 값. 트랜잭션 밖으로 나가므로 엔티티가 아니라 값만 담는다.
     *
     * @param failure 채점을 시작할 수 없는 칸이면 그 사유. 채점 가능하면 {@code null}
     */
    private record Loaded(long answerId, long assignmentStudentId, long answerUnitId,
                          List<RubricCriterion> criteria, Map<Long, Short> weights,
                          EssayGradingFailure failure) {

        static Loaded unusable(EssayGradingFailure failure) {
            return new Loaded(0, 0, 0, List.of(), Map.of(), failure);
        }
    }
}
