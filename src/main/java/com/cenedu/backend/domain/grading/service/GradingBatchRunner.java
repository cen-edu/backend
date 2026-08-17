package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 자동채점을 백그라운드에서 돌린다.
 *
 * <p><b>별도 빈이어야 한다.</b> {@code @Async}는 프록시로 동작하므로 같은 빈 안에서 부르면 프록시를
 * 우회해 그냥 동기 실행된다. 대상을 고르는 쪽({@link GradingExecutionService})과 도는 쪽을 나눈
 * 이유가 이것이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradingBatchRunner {

    private final AnswerGradingService answerGradingService;
    private final GradingJobRegistry gradingJobRegistry;

    /** 채점할 칸 하나. 배점은 문항에서 온다(일반학습은 {@code null}). */
    public record Target(long submissionAnswerId, BigDecimal maxScore) {
    }

    /**
     * 대상 칸을 하나씩 채점하고, 전 칸이 끝난 학생을 채점 완료로 표시한다.
     *
     * @param assessment 종합평가면 {@code total_score}를 다시 계산한다
     */
    @Async("gradingTaskExecutor")
    public void run(long assignmentId, boolean assessment, List<Target> targets,
                    Set<Long> assignmentStudentIds) {
        long startedAt = System.nanoTime();
        int graded = 0;
        int failed = 0;
        try {
            for (Target target : targets) {
                try {
                    if (answerGradingService.gradeOne(target.submissionAnswerId(), target.maxScore())
                            == AnswerGradingService.Outcome.GRADED) {
                        graded++;
                    } else {
                        failed++;
                    }
                } catch (RuntimeException e) {
                    // 한 칸이 터져도 배치를 멈추지 않는다(명세 7절). 칸의 상태는 NOT_GRADED 로 남아
                    // 다음 실행 대상이 된다.
                    failed++;
                    log.warn("칸 채점 실패 submissionAnswerId={}", target.submissionAnswerId(), e);
                }
            }
            for (Long assignmentStudentId : assignmentStudentIds) {
                try {
                    answerGradingService.finalizeStudent(assignmentStudentId, assessment);
                } catch (RuntimeException e) {
                    log.warn("학생 채점 완료 처리 실패 assignmentStudentId={}", assignmentStudentId, e);
                }
            }
        } finally {
            gradingJobRegistry.finish(assignmentId);
            log.info("자동채점 종료 assignmentId={} 대상={} 채점={} 실패={} 소요={}ms",
                    assignmentId, targets.size(), graded, failed,
                    (System.nanoTime() - startedAt) / 1_000_000);
        }
    }
}
