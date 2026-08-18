package com.cenedu.backend.domain.problem.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProblemRagProperties.class)
public class ProblemRagConfig {
}
