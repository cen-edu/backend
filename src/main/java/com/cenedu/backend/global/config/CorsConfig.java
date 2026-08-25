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
 * <p>{@code allowedOriginPatterns} 를 쓴다. {@code allowedOrigins} 는 문자열이 정확히 같아야
 * 통과하는데, Vercel 은 push 마다 {@code https://frontend-<해시>-<팀>.vercel.app} 형태의 새
 * 미리보기 주소를 만든다. 그 주소를 미리 적어 둘 수 없으므로 정확 일치로는 미리보기 배포가
 * 전부 막힌다.
 *
 * <p>{@code *} 하나만 두지는 않는다. 자격 증명을 함께 보내는 요청에서 브라우저가 거부하고,
 * 인증이 붙는 순간 조용히 막힌다. 패턴은 {@code https://*.vercel.app} 처럼 호스트를 좁혀
 * 적는다 — 그래야 아무 사이트나 이 API 를 부르지 못한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}")
                      List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
