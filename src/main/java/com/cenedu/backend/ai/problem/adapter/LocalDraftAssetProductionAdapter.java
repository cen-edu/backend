package com.cenedu.backend.ai.problem.adapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.cenedu.backend.domain.problem.authoring.asset.*;
import com.cenedu.backend.domain.problem.authoring.port.ProblemAssetProductionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 구조화 자산을 sanitize한 뒤 승인 전 로컬 임시 경로에 원자적으로 저장한다. */
@Component
public class LocalDraftAssetProductionAdapter implements ProblemAssetProductionPort {
    private final Path draftRoot;
    private final SafeSvgSanitizer sanitizer;

    public LocalDraftAssetProductionAdapter(
            @Value("${app.problem-authoring.draft-root:/tmp/cen-edu-problem-drafts}") String draftRoot,
            SafeSvgSanitizer sanitizer) {
        this.draftRoot = Path.of(draftRoot).toAbsolutePath().normalize();
        this.sanitizer = sanitizer;
    }

    /** STRUCTURED_RENDER SVG를 안전한 임시 파일로 만든다. */
    @Override
    public DraftAssetArtifact produce(GeneratedAssetPlan plan, AssetProductionContext context) {
        if (plan.productionMode() != AssetProductionMode.STRUCTURED_RENDER
                || plan.outputFormat() != AssetOutputFormat.SVG) {
            throw new IllegalArgumentException("현재 임시 자산 Adapter는 STRUCTURED_RENDER SVG만 지원합니다.");
        }
        String svg = sanitizer.sanitize(renderSvg(plan));
        byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
        Path directory = draftRoot.resolve(String.valueOf(context.sessionId()))
                .resolve(String.valueOf(context.versionNo())).normalize();
        Path target = directory.resolve(plan.assetKey() + ".svg").normalize();
        if (!target.startsWith(draftRoot)) throw new IllegalArgumentException("임시 자산 경로가 올바르지 않습니다.");
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, plan.assetKey(), ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new DraftAssetArtifact(plan.assetKey(), DraftAssetStatus.READY,
                    draftRoot.relativize(target).toString(), "image/svg+xml", null, null,
                    sha256(bytes), 1, null);
        } catch (IOException exception) {
            throw new IllegalStateException("임시 자산 저장에 실패했습니다.", exception);
        }
    }

    private String renderSvg(GeneratedAssetPlan plan) {
        String description = escape(plan.specification().visualDescription());
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 640 120\">"
                + "<rect width=\"640\" height=\"120\" fill=\"white\"/>"
                + "<text x=\"20\" y=\"65\" font-size=\"18\">" + description + "</text></svg>";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
