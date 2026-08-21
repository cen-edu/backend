package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.config.ProblemDraftStorageProperties;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemDraftPathResolverTest {
    @Test void resolvesRelativeKeyInsideRoot() {
        var resolver = new ProblemDraftPathResolver(new ProblemDraftStorageProperties(Path.of("/tmp/drafts"), 100));
        assertThat(resolver.resolve("1/2/F1.svg")).isEqualTo(Path.of("/tmp/drafts/1/2/F1.svg"));
    }
    @Test void rejectsTraversalAndAbsoluteKey() {
        var resolver = new ProblemDraftPathResolver(new ProblemDraftStorageProperties(Path.of("/tmp/drafts"), 100));
        assertThatThrownBy(() -> resolver.resolve("../secret.svg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
