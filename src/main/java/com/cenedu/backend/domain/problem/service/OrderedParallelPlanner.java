package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.MDC;

/**
 * 계획 단계의 서로 독립적인 RAG 검색·스냅샷 조회를 <b>입력 순서를 보존</b>하며 병렬 실행하는 헬퍼다.
 *
 * <p>executor가 {@code null}이면 호출 스레드에서 순차 실행한다 — 기존 동작과 순서 검증 테스트를 그대로
 * 보존하기 위해서다. 태스크가 던진 {@link RuntimeException}(예: {@code BusinessException})은 원형 그대로
 * 전파해 계약을 유지한다.
 *
 * <p>fan-out에는 RAG 검색 전용 2-스레드 풀을 재사용하지 않는다. 그 풀은 {@code retrieve()} 내부에서
 * 타임아웃 강제를 위해 다시 submit·block하므로, 같은 풀로 fan-out하면 재진입 데드락이 난다.
 */
final class OrderedParallelPlanner {

    private OrderedParallelPlanner() {
    }

    /** 태스크들을 병렬 실행하고 입력 순서대로 결과를 반환한다. */
    static <T> List<T> map(ExecutorService executor, List<Callable<T>> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        if (executor == null || tasks.size() == 1) {
            List<T> results = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                results.add(callSequential(task));
            }
            return results;
        }
        Map<String, String> parentContext = MDC.getCopyOfContextMap();
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> callWithContext(parentContext, task)));
        }
        List<T> results = new ArrayList<>(tasks.size());
        try {
            for (Future<T> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            futures.forEach(future -> future.cancel(true));
            throw new IllegalStateException("계획 단계 병렬 실행이 중단되었습니다.", exception);
        } catch (ExecutionException exception) {
            futures.forEach(future -> future.cancel(true));
            throw asRuntime(exception.getCause());
        }
        return results;
    }

    private static <T> T callSequential(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception exception) {
            throw asRuntime(exception);
        }
    }

    /** 제출 스레드의 MDC 추적값을 작업 스레드로 전달하고 작업 후 원래 값으로 복원한다. */
    private static <T> T callWithContext(Map<String, String> parentContext, Callable<T> task) throws Exception {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (parentContext == null || parentContext.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(parentContext);
            }
            return task.call();
        } finally {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    private static RuntimeException asRuntime(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("계획 단계 병렬 실행에 실패했습니다.", cause);
    }
}
