package com.cenedu.backend.domain.problem.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** DRAFT TTL과 FAILED Task 보존기간을 주기적으로 집행한다. */
@Component
@EnableScheduling
public class ProblemDraftAssetCleanupScheduler {
    private final ProblemDraftAssetCleanupService cleanupService;
    private final long draftTtlHours;
    private final long failedRetentionHours;

    public ProblemDraftAssetCleanupScheduler(ProblemDraftAssetCleanupService cleanupService,
            @Value("${app.problem-authoring.draft-ttl-hours:24}") long draftTtlHours,
            @Value("${app.problem-authoring.failed-asset-retention-hours:72}") long failedRetentionHours) {
        this.cleanupService = cleanupService;
        this.draftTtlHours = draftTtlHours;
        this.failedRetentionHours = failedRetentionHours;
    }

    /** 설정된 주기마다 만료 DRAFT와 보존기간이 지난 실패 원본을 정리한다. */
    @Scheduled(fixedDelayString = "${app.problem-authoring.draft-cleanup-delay-ms:3600000}")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        cleanupService.expireDrafts(now.minusHours(draftTtlHours));
        cleanupService.cleanupFailedTaskSources(now.minusHours(failedRetentionHours));
    }
}
