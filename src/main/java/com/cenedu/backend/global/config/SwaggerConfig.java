package com.cenedu.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서 설정.
 *
 * <p>AGENTS.md 7절이 Swagger 를 프론트와의 API 계약서로 정했다. 프론트 담당자가 여기만 보고
 * 연동할 수 있어야 하므로, 규칙과 다르게 만든 부분을 문서 첫머리에 적어 둔다. 코드 주석에만
 * 두면 문서를 보는 사람은 규칙 위반으로 읽고 되돌리려 한다.
 */
@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .info(new Info()
                        .title("CEN EDU API")
                        .version("v0")
                        .description("센의 정석 백엔드 API."));
    }
}
