package com.cenedu.backend.ai.verification.adapter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 검증 Adapter 의 설정 바인딩.
 *
 * <p>{@code BackendApplication} 은 이 패키지 밖의 파일이라 고치지 않는다. {@code ai/client} 가
 * {@code OpenAiClientConfig} 에서 같은 방식으로 자기 프로퍼티를 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ContentCheckProperties.class)
public class VerificationAdapterConfig {
}
