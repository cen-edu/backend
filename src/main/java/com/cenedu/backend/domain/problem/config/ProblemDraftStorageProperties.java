package com.cenedu.backend.domain.problem.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 생성·미리보기·정리·S3 업로드가 공유하는 local draft 저장 설정이다. */
@ConfigurationProperties(prefix = "app.problem-authoring.draft")
public record ProblemDraftStorageProperties(
        @DefaultValue("/tmp/cen-edu-problem-drafts") Path root,
        @DefaultValue("1048576") long previewMaxBytes
) {
    public ProblemDraftStorageProperties {
        if (root == null) throw new IllegalArgumentException("draft root가 필요합니다.");
        root = root.toAbsolutePath().normalize();
        if (previewMaxBytes < 1) throw new IllegalArgumentException("draft preview 크기는 1 byte 이상이어야 합니다.");
    }
}
