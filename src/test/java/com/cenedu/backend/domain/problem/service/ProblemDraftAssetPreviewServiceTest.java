package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetStatus;
import com.cenedu.backend.domain.problem.config.ProblemDraftStorageProperties;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ProblemDraftAssetPreviewServiceTest {
    @TempDir Path root;

    @Test
    void 정상_READY_SVG는_data_url과_무결성_메타데이터를_반환한다() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"></svg>";
        Path file = root.resolve("7/2/F1.svg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, svg);
        String checksum = sha256(svg.getBytes());
        ProblemAuthoringVersion version = version(AuthoringVerificationStatus.PASSED,
                new DraftAssetArtifact("F1", DraftAssetStatus.READY, "7/2/F1.svg", "image/svg+xml", 10, 10, checksum, 1, null));
        var service = service(version);

        var result = service.preview(7, 2, 3, "F1");

        assertThat(result.dataUrl()).startsWith("data:image/svg+xml;base64,");
        assertThat(result.checksum()).isEqualTo(checksum);
        assertThat(result.widthPx()).isEqualTo(10);
    }

    @Test
    void 소유권_검증과_READY_검증을_통과하지_못하면_노출하지_않는다() {
        var sessions = mock(ProblemAuthoringSessionRepository.class);
        when(sessions.findByIdAndOwnerTeacherId(2L, 99L)).thenReturn(Optional.empty());
        var service = new ProblemDraftAssetPreviewService(sessions, mock(ProblemAuthoringVersionRepository.class),
                new ProblemDraftPathResolver(new ProblemDraftStorageProperties(root, 1000)),
                new ProblemDraftStorageProperties(root, 1000), new ObjectMapper());

        assertThatThrownBy(() -> service.preview(99, 2, 3, "F1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROBLEM_DRAFT_NOT_FOUND);
    }

    @Test
    void checksum이_다르거나_SVG가_아니면_무결성_오류로_거절한다() throws Exception {
        Path file = root.resolve("7/2/F1.svg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "<svg>tampered</svg>");
        var service = service(version(AuthoringVerificationStatus.PASSED,
                new DraftAssetArtifact("F1", DraftAssetStatus.READY, "7/2/F1.svg", "image/svg+xml", 10, 10, "wrong", 1, null)));

        assertThatThrownBy(() -> service.preview(7, 2, 3, "F1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROBLEM_DRAFT_PREVIEW_INVALID);
    }

    private ProblemDraftAssetPreviewService service(ProblemAuthoringVersion version) throws Exception {
        var sessions = mock(ProblemAuthoringSessionRepository.class);
        when(sessions.findByIdAndOwnerTeacherId(2L, 7L)).thenReturn(Optional.of(mock(ProblemAuthoringSession.class)));
        var versions = mock(ProblemAuthoringVersionRepository.class);
        when(versions.findByIdAndSessionId(3L, 2L)).thenReturn(Optional.of(version));
        var properties = new ProblemDraftStorageProperties(root, 1000);
        return new ProblemDraftAssetPreviewService(sessions, versions, new ProblemDraftPathResolver(properties), properties, new ObjectMapper());
    }

    private ProblemAuthoringVersion version(AuthoringVerificationStatus status, DraftAssetArtifact artifact) throws Exception {
        var version = mock(ProblemAuthoringVersion.class);
        when(version.getVerificationStatus()).thenReturn(status);
        when(version.getAssetManifest()).thenReturn(new ObjectMapper().writeValueAsString(new DraftAssetManifest(1, List.of(), List.of(artifact))));
        return version;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
