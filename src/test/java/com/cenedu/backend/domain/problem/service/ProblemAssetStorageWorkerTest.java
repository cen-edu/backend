package com.cenedu.backend.domain.problem.service;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemAssetStorageTask;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAssetStorageTaskRepository;
import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProblemAssetStorageWorkerTest {
    @TempDir Path draftRoot;

    @Test
    void checksum이_일치하면_동일한_최종키로_업로드하고_READY로_전환한다() throws Exception {
        byte[] content = "<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path source = draftRoot.resolve("1/1/F1.svg");
        Files.createDirectories(source.getParent());
        Files.write(source, content);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        ProblemAsset asset = ProblemAsset.createPending(null, "F1", AssetRole.FIGURE, (short) 0,
                "questions/generated/short-input/10/F1-" + checksum + ".svg", 0, 0, "도형");
        ProblemAssetStorageTask task = ProblemAssetStorageTask.create(asset, "1/1/F1.svg",
                asset.getStorageKey(), checksum, "image/svg+xml");
        ProblemAssetStorageTaskRepository repository = mock(ProblemAssetStorageTaskRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        ProblemAssetStorageWorker worker = new ProblemAssetStorageWorker(repository, storage,
                new S3Properties("ap-northeast-2", "problem", "answer", "key", "secret", Duration.ofMinutes(15)),
                draftRoot.toString());

        worker.runOne(1L);

        verify(storage).upload(eq("problem"), eq(asset.getStorageKey()),
                argThat(bytes -> java.util.Arrays.equals(bytes, content)), eq("image/svg+xml"));
        assertThat(task.getStatus()).isEqualTo(ProblemAssetStorageStatus.READY);
        assertThat(asset.getStorageStatus()).isEqualTo(ProblemAssetStorageStatus.READY);
        assertThat(source).doesNotExist();
    }

    @Test
    void checksum이_다르면_S3에_업로드하지_않고_재시도_대기한다() throws Exception {
        Path source = draftRoot.resolve("2/1/F1.svg");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "changed");
        ProblemAsset asset = ProblemAsset.createPending(null, "F1", AssetRole.FIGURE, (short) 0,
                "questions/generated/short-input/11/F1-original.svg", 0, 0, "도형");
        ProblemAssetStorageTask task = ProblemAssetStorageTask.create(asset, "2/1/F1.svg",
                asset.getStorageKey(), "original", "image/svg+xml");
        ProblemAssetStorageTaskRepository repository = mock(ProblemAssetStorageTaskRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(task));
        ProblemAssetStorageWorker worker = new ProblemAssetStorageWorker(repository, storage,
                new S3Properties("ap-northeast-2", "problem", "answer", "key", "secret", Duration.ofMinutes(15)),
                draftRoot.toString());

        worker.runOne(2L);

        verifyNoInteractions(storage);
        assertThat(task.getStatus()).isEqualTo(ProblemAssetStorageStatus.RETRY_WAIT);
        assertThat(source).exists();
    }
}
