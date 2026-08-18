package com.cenedu.backend.domain.problem.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemAssetStorageTask;
import com.cenedu.backend.domain.problem.repository.ProblemAssetStorageTaskRepository;
import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최종화 transaction 이후 draft 파일을 S3로 옮기는 작업자다. */
@Service
public class ProblemAssetStorageWorker {
    private static final int MAX_ATTEMPTS = 3;
    private final ProblemAssetStorageTaskRepository taskRepository;
    private final ImageStorageService imageStorageService;
    private final S3Properties s3Properties;
    private final Path draftRoot;

    public ProblemAssetStorageWorker(ProblemAssetStorageTaskRepository taskRepository,
            ImageStorageService imageStorageService, S3Properties s3Properties,
            @Value("${app.problem-authoring.draft-root:/tmp/cen-edu-problem-drafts}") String draftRoot) {
        this.taskRepository = taskRepository; this.imageStorageService = imageStorageService;
        this.s3Properties = s3Properties; this.draftRoot = Path.of(draftRoot).toAbsolutePath().normalize();
    }

    /** 실행 가능한 작업을 순서대로 처리하고 개별 실패가 다른 작업을 막지 않게 한다. */
    public void runPending() {
        List<ProblemAssetStorageTask> tasks = taskRepository.findRunnable(LocalDateTime.now());
        tasks.forEach(this::runOne);
    }

    /** 하나의 draft를 동일한 최종 key로 업로드한다. */
    @Transactional
    public void runOne(ProblemAssetStorageTask task) {
        if (task.getStatus() == com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus.READY) return;
        task.start();
        try {
            Path source = draftRoot.resolve(task.getSourceLocalPath()).normalize();
            if (!source.startsWith(draftRoot) || !Files.isRegularFile(source)) throw new IllegalStateException("DRAFT_ASSET_NOT_FOUND");
            byte[] content = Files.readAllBytes(source);
            String contentType = Files.probeContentType(source);
            imageStorageService.upload(s3Properties.requiredProblemBucket(), task.getTargetStorageKey(), content,
                    contentType == null ? "application/octet-stream" : contentType);
            task.complete();
            Files.deleteIfExists(source);
        } catch (Exception exception) {
            if (task.getAttemptCount() < MAX_ATTEMPTS) task.retry(exception.getClass().getSimpleName(), LocalDateTime.now().plusMinutes(1));
            else task.fail(exception.getClass().getSimpleName());
        }
    }
}
