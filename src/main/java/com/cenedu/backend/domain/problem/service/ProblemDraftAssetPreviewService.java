package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.asset.*;
import com.cenedu.backend.domain.problem.dto.response.DraftAssetPreviewResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.domain.problem.config.ProblemDraftStorageProperties;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;

/** 교사 소유의 READY draft SVG를 S3 업로드 전 data URL로 제공한다. */
@Service
public class ProblemDraftAssetPreviewService {
    private final ProblemAuthoringSessionRepository sessions;
    private final ProblemAuthoringVersionRepository versions;
    private final ProblemDraftPathResolver paths;
    private final ProblemDraftStorageProperties properties;
    private final ObjectMapper objectMapper;

    public ProblemDraftAssetPreviewService(ProblemAuthoringSessionRepository sessions,
                                           ProblemAuthoringVersionRepository versions,
                                           ProblemDraftPathResolver paths,
                                           ProblemDraftStorageProperties properties,
                                           ObjectMapper objectMapper) {
        this.sessions = sessions; this.versions = versions; this.paths = paths;
        this.properties = properties; this.objectMapper = objectMapper;
    }

    /** 교사 소유 session/version의 특정 asset을 무결성 검증 후 preview한다. */
    public DraftAssetPreviewResponse preview(long teacherId, long sessionId, long versionId, String assetKey) {
        sessions.findByIdAndOwnerTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_DRAFT_NOT_FOUND));
        ProblemAuthoringVersion version = versions.findByIdAndSessionId(versionId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_DRAFT_NOT_FOUND));
        if (version.getVerificationStatus() != AuthoringVerificationStatus.PASSED)
            throw new BusinessException(ErrorCode.PROBLEM_DRAFT_PREVIEW_NOT_READY);
        DraftAssetManifest manifest = readManifest(version);
        DraftAssetArtifact artifact = manifest.artifacts().stream()
                .filter(a -> assetKey.equals(a.assetKey())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_DRAFT_NOT_FOUND));
        if (artifact.status() != DraftAssetStatus.READY || artifact.contentType() == null
                || !"image/svg+xml".equals(artifact.contentType())) {
            throw new BusinessException(ErrorCode.PROBLEM_DRAFT_PREVIEW_NOT_READY);
        }
        try {
            var path = paths.resolveRegularFile(artifact.draftStorageKey());
            long size = Files.size(path);
            if (size <= 0 || size > properties.previewMaxBytes())
                throw new BusinessException(ErrorCode.PROBLEM_DRAFT_PREVIEW_INVALID);
            byte[] bytes = Files.readAllBytes(path);
            String checksum = sha256(bytes);
            if (!checksum.equals(artifact.checksum()) || artifact.widthPx() == null || artifact.heightPx() == null
                    || !new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains("<svg"))
                throw new BusinessException(ErrorCode.PROBLEM_DRAFT_PREVIEW_INVALID);
            return new DraftAssetPreviewResponse(sessionId, versionId, assetKey, artifact.contentType(),
                    artifact.widthPx(), artifact.heightPx(), checksum,
                    "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(bytes));
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw new BusinessException(ErrorCode.PROBLEM_DRAFT_PREVIEW_NOT_READY); }
    }

    private DraftAssetManifest readManifest(ProblemAuthoringVersion version) {
        try { return objectMapper.readValue(version.getAssetManifest(), DraftAssetManifest.class); }
        catch (Exception e) { throw new BusinessException(ErrorCode.PROBLEM_DRAFT_PREVIEW_NOT_READY); }
    }

    private String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
