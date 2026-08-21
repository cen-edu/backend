package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.config.ProblemDraftStorageProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** draft storage key를 공통 typed root 아래의 안전한 파일 경로로 해석한다. */
@Component
public class ProblemDraftPathResolver {
    private final ProblemDraftStorageProperties properties;

    public ProblemDraftPathResolver(ProblemDraftStorageProperties properties) {
        this.properties = properties;
    }

    /** manifest의 상대 draft key가 root를 탈출하지 않는지 확인하고 파일 경로를 반환한다. */
    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || Path.of(storageKey).isAbsolute()) {
            throw new IllegalArgumentException("draft storage key가 올바르지 않습니다.");
        }
        Path resolved = properties.root().resolve(storageKey).normalize();
        if (!resolved.startsWith(properties.root())) {
            throw new IllegalArgumentException("draft storage key가 root를 벗어났습니다.");
        }
        return resolved;
    }

    /** 심볼릭 링크를 따라가지 않는 실제 regular file만 반환한다. */
    public Path resolveRegularFile(String storageKey) {
        Path resolved = resolve(storageKey);
        try {
            if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(resolved)) throw new IllegalArgumentException("draft file이 아닙니다.");
            Path realRoot = properties.root().toRealPath();
            if (!resolved.toRealPath().startsWith(realRoot)) throw new IllegalArgumentException("draft file root 오류");
            return resolved;
        } catch (Exception e) { throw new IllegalArgumentException("draft file을 해석할 수 없습니다.", e); }
    }
}
