package com.cenedu.backend.infra.storage.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.infra.storage.service.ImageStorageService;
import com.cenedu.backend.infra.storage.service.S3ImageStorageService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("S3 기능이 비활성화되면 AWS 클라이언트와 저장 서비스를 만들지 않는다")
    void disablesS3BeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(S3Client.class);
            assertThat(context).doesNotHaveBean(S3Presigner.class);
            assertThat(context).doesNotHaveBean(ImageStorageService.class);
        });
    }

    @Test
    @DisplayName("S3 기능과 필수 설정을 지정하면 AWS 클라이언트와 저장 서비스를 만든다")
    void enablesS3BeansWithRequiredProperties() {
        contextRunner
                .withPropertyValues(
                        "app.storage.s3.enabled=true",
                        "app.storage.s3.region=ap-northeast-2",
                        "app.storage.s3.problem-bucket=problem-bucket",
                        "app.storage.s3.answer-bucket=answer-bucket",
                        "app.storage.s3.access-key-id=test-access-key",
                        "app.storage.s3.secret-access-key=test-secret-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    assertThat(context).hasSingleBean(ImageStorageService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({S3Config.class, S3ImageStorageService.class})
    static class TestConfig {
    }
}
