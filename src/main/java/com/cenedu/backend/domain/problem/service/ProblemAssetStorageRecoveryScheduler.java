package com.cenedu.backend.domain.problem.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 서버 재시작·일시 장애 뒤 대기 중인 자산 작업을 복구한다. */
@Component
public class ProblemAssetStorageRecoveryScheduler {
    private final ProblemAssetStorageWorker worker;
    public ProblemAssetStorageRecoveryScheduler(ProblemAssetStorageWorker worker) { this.worker = worker; }

    /** 매 분 만료된 PENDING/RETRY_WAIT 작업을 재실행한다. */
    @Scheduled(fixedDelayString = "${app.problem-authoring.asset-recovery-delay-ms:60000}")
    public void recover() { worker.runPending(); }
}
