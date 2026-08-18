package com.cenedu.backend.domain.problem.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 문항 하나를 Problem 전용 실행기에서 독립적으로 생성·검증한다. */
@Component
public class ProblemGenerationAsyncRunner {
    private final ProblemGenerationWorker worker;

    public ProblemGenerationAsyncRunner(ProblemGenerationWorker worker) {
        this.worker = worker;
    }

    /** 호출 문항 하나의 전체 생성·검증 흐름을 비동기로 실행한다. */
    @Async("problemGenerationTaskExecutor")
    public void execute(Long itemId) {
        worker.execute(itemId);
    }
}
