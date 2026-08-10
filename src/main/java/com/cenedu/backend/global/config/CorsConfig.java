package com.cenedu.backend.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트가 다른 오리진에서 API 를 부를 수 있게 연다.
 *
 * <p>허용 오리진은 {@code app.cors.allowed-origins} 에서 읽는다. 코드에 박으면 배포 환경마다
 * 다시 빌드해야 한다.
 *
 * <p>{@code allowedOrigins} 를 쓰고 {@code *} 를 쓰지 않는다. 와일드카드는 자격 증명을 함께
 * 보내는 요청에서 브라우저가 거부하고, 인증이 붙는 순간 조용히 막힌다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}")
                      List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
