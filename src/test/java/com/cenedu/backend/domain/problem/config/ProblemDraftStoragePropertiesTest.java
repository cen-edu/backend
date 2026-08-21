package com.cenedu.backend.domain.problem.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemDraftStoragePropertiesTest {
    @Test void normalizesRootAndAcceptsPositiveLimit() {
        var properties = new ProblemDraftStorageProperties(Path.of("/tmp/../tmp/drafts"), 100);
        assertThat(properties.root()).isAbsolute();
        assertThat(properties.previewMaxBytes()).isEqualTo(100);
    }
    @Test void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> new ProblemDraftStorageProperties(Path.of("/tmp/drafts"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
