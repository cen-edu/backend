package com.cenedu.backend.domain.problem.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 시각 저작과 draft 저장 관련 configuration properties를 등록한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ProblemVisualAuthoringProperties.class, ProblemDraftStorageProperties.class})
public class ProblemVisualAuthoringConfig {
}
