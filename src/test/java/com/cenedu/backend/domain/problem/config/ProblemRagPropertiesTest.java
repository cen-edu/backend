package com.cenedu.backend.domain.problem.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProblemRagPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProblemRagConfig.class);

    @Test
    void bindsServerOnlyAStageDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ProblemRagProperties properties = context.getBean(ProblemRagProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.policyVersion()).isEqualTo("A_DENSE_MMR_V1");
            assertThat(properties.candidateLimit()).isEqualTo(40);
            assertThat(properties.standardSelectionLimit()).isEqualTo(3);
            assertThat(properties.personalizedSelectionLimit()).isEqualTo(4);
            assertThat(properties.defaultLambda()).isEqualTo(0.70);
            assertThat(properties.applicationLambda()).isEqualTo(0.55);
            assertThat(properties.searchTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.indexing().enabled()).isFalse();
            assertThat(properties.indexing().batchSize()).isEqualTo(20);
            assertThat(properties.indexing().maxAttempts()).isEqualTo(3);
            assertThat(properties.indexing().retryDelay()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.indexing().workerDelay()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.indexing().backfillDelay()).isEqualTo(Duration.ofSeconds(60));
        });
    }
}
