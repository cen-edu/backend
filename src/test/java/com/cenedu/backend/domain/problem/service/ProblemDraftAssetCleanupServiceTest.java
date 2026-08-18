package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetStatus;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemAssetStorageTask;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAssetStorageTaskRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ProblemDraftAssetCleanupServiceTest {
    @TempDir Path draftRoot;
    private final ProblemAuthoringSessionRepository sessionRepository = mock(ProblemAuthoringSessionRepository.class);
    private final ProblemAuthoringVersionRepository versionRepository = mock(ProblemAuthoringVersionRepository.class);
    private final ProblemAssetStorageTaskRepository taskRepository = mock(ProblemAssetStorageTaskRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProblemDraftAssetCleanupService service;

    @BeforeEach
    void setUp() {
        service = new ProblemDraftAssetCleanupService(sessionRepository, versionRepository,
                taskRepository, objectMapper, draftRoot.toString());
    }

    @Test
    void TTL이_지난_DRAFT를_지우고_EXPIRED로_닫는다() throws Exception {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(7L);
        Path source = createDraft("7/1/F1.svg");
        ProblemAuthoringVersion version = version("7/1/F1.svg");
        when(sessionRepository.findByLifecycleStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                AuthoringLifecycleStatus.DRAFT, LocalDateTime.MIN)).thenReturn(List.of(session));
        when(versionRepository.findAllBySessionIdOrderByVersionNo(null)).thenReturn(List.of(version));

        assertThat(service.expireDrafts(LocalDateTime.MIN)).isEqualTo(1);

        assertThat(source).doesNotExist();
        assertThat(session.getLifecycleStatus()).isEqualTo(AuthoringLifecycleStatus.EXPIRED);
    }

    @Test
    void 취소한_DRAFT는_즉시_정리하고_CANCELLED로_닫는다() throws Exception {
        ProblemAuthoringSession session = ProblemAuthoringSession.createIdle(7L);
        Path source = createDraft("7/2/F1.svg");
        ProblemAuthoringVersion version = version("7/2/F1.svg");
        when(sessionRepository.findOwnedByIdForUpdate(2L, 7L)).thenReturn(Optional.of(session));
        when(versionRepository.findAllBySessionIdOrderByVersionNo(2L)).thenReturn(List.of(version));

        service.cancelDraft(7L, 2L);

        assertThat(source).doesNotExist();
        assertThat(session.getLifecycleStatus()).isEqualTo(AuthoringLifecycleStatus.CANCELLED);
    }

    @Test
    void FAILED_task는_보존기간_이후_원본만_정리한다() throws Exception {
        Path source = createDraft("8/1/F1.svg");
        ProblemAsset asset = ProblemAsset.createPending(null, "F1", AssetRole.FIGURE, (short) 0,
                "questions/generated/short-input/8/F1-x.svg", 0, 0, "도형");
        ProblemAssetStorageTask task = ProblemAssetStorageTask.create(asset, "8/1/F1.svg",
                asset.getStorageKey(), "checksum", "image/svg+xml");
        task.fail("UPLOAD_FAILED");
        when(taskRepository.findFailedForSourceCleanup(LocalDateTime.MIN)).thenReturn(List.of(task));

        assertThat(service.cleanupFailedTaskSources(LocalDateTime.MIN)).isEqualTo(1);

        assertThat(source).doesNotExist();
        assertThat(task.getSourceDeletedAt()).isNotNull();
    }

    private Path createDraft(String relative) throws Exception {
        Path source = draftRoot.resolve(relative);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "<svg/>");
        return source;
    }

    private ProblemAuthoringVersion version(String relative) throws Exception {
        ProblemAuthoringVersion version = mock(ProblemAuthoringVersion.class);
        DraftAssetManifest manifest = new DraftAssetManifest(1, List.of(), List.of(
                new DraftAssetArtifact("F1", DraftAssetStatus.READY, relative, "image/svg+xml",
                        1, 1, "checksum", 1, null)));
        when(version.getAssetManifest()).thenReturn(objectMapper.writeValueAsString(manifest));
        return version;
    }
}
