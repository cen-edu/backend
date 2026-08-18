package com.cenedu.backend.domain.problem.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.entity.ProblemAssetStorageTask;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAssetStorageTaskRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 만료·취소 Session과 영구 실패 Storage Task의 로컬 draft 파일을 정리한다. */
@Service
public class ProblemDraftAssetCleanupService {
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final ProblemAssetStorageTaskRepository storageTaskRepository;
    private final ObjectMapper objectMapper;
    private final Path draftRoot;

    public ProblemDraftAssetCleanupService(ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository,
            ProblemAssetStorageTaskRepository storageTaskRepository, ObjectMapper objectMapper,
            @Value("${app.problem-authoring.draft-root:/tmp/cen-edu-problem-drafts}") String draftRoot) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.storageTaskRepository = storageTaskRepository;
        this.objectMapper = objectMapper;
        this.draftRoot = Path.of(draftRoot).toAbsolutePath().normalize();
    }

    /** TTL이 지난 DRAFT Session의 파일을 지우고 EXPIRED로 닫는다. */
    @Transactional
    public int expireDrafts(LocalDateTime cutoff) {
        List<ProblemAuthoringSession> sessions = sessionRepository
                .findByLifecycleStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        AuthoringLifecycleStatus.DRAFT, cutoff);
        sessions.forEach(session -> {
            deleteSessionDrafts(session.getId());
            session.expireDraft();
        });
        return sessions.size();
    }

    /** 소유한 DRAFT Session을 취소하고 연결된 임시 파일을 즉시 지운다. */
    @Transactional
    public void cancelDraft(long ownerTeacherId, long sessionId) {
        ProblemAuthoringSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, ownerTeacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        deleteSessionDrafts(sessionId);
        session.cancelDraft();
    }

    /** 보존기간이 지난 FAILED Task 원본을 지우고 중복 정리를 막도록 시각을 기록한다. */
    @Transactional
    public int cleanupFailedTaskSources(LocalDateTime cutoff) {
        List<ProblemAssetStorageTask> tasks = storageTaskRepository.findFailedForSourceCleanup(cutoff);
        LocalDateTime deletedAt = LocalDateTime.now();
        tasks.forEach(task -> {
            deleteRelative(task.getSourceLocalPath());
            task.markSourceDeleted(deletedAt);
        });
        return tasks.size();
    }

    private void deleteSessionDrafts(Long sessionId) {
        versionRepository.findAllBySessionIdOrderByVersionNo(sessionId).stream()
                .flatMap(version -> readManifest(version.getAssetManifest()).artifacts().stream())
                .filter(artifact -> artifact.draftStorageKey() != null)
                .map(artifact -> artifact.draftStorageKey())
                .distinct()
                .forEach(this::deleteRelative);
    }

    private DraftAssetManifest readManifest(String json) {
        try {
            return objectMapper.readValue(json, DraftAssetManifest.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
        }
    }

    private void deleteRelative(String relativePath) {
        try {
            Path target = draftRoot.resolve(relativePath).normalize();
            if (!target.startsWith(draftRoot)) {
                throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
            }
            Files.deleteIfExists(target);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("DRAFT_ASSET_CLEANUP_FAILED", exception);
        }
    }
}
