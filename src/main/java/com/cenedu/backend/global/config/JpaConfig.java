package com.cenedu.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 설정.
 *
 * <p>{@code @EnableJpaAuditing} 이 있어야
 * {@code BaseTimeEntity} 의 {@code @CreatedDate} / {@code @LastModifiedDate} 가 채워진다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
