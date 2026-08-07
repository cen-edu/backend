package com.cenedu.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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

    private static final String DESCRIPTION = """
            센의 정석 백엔드 API.

            ## 취약점 분석(analysis) 경로에 대한 안내

            `/api/assessments/**` 와 `/api/weakness-analysis/**` 는 AGENTS.md 2절의
            `/api/teacher/analysis` 접두어를 따르지 않고, 성공 응답도 7절의 `ApiResponse<T>` 로
            감싸지 않습니다. **실수가 아니라 프론트 계약에 맞춘 것입니다.**

            프론트 연동 계층이 이미 이 모양으로 작성되어 있고 프론트를 고치지 않기로 정했습니다.
            되돌리려면 프론트 `src/api` 를 함께 고쳐야 합니다.

            딸려오는 제약이 둘 있습니다.

            - **권한**: 권한이 URL 접두어로 갈리므로 이 경로들은 인증이 붙을 때
              `/api/teacher/**` 규칙에 걸리지 않습니다. SecurityConfig 에 명시적 규칙이 따로
              필요합니다.
            - **오류 응답**: 성공 응답만 벗겨져 있고, 오류는 `ApiResponse` 형태로 나갑니다.
              GlobalExceptionHandler 가 단일 지점이라 여기서 벗기면 다른 도메인까지 바뀝니다.

            ## 같은 데이터의 두 가지 모양

            `/api/assessments/**` 와 `/api/weakness-analysis/**` 는 같은 자료를 다른 구조로
            돌려줍니다. 프론트 두 갈래가 서로 다른 계약을 쓰고 있어서이고, 한쪽이 정리되면
            나머지를 걷어냅니다. 새로 붙이는 화면은 `/api/weakness-analysis` 쪽을 쓰세요 —
            한 번의 호출로 화면 한 벌을 받습니다.

            ## PDF 보고서

            PDF 는 서버에 설치된 브라우저를 headless 로 띄워 만듭니다. 브라우저가 없으면
            `503 REPORT_RENDERER_UNAVAILABLE` 로 답합니다. 코드 문제가 아니라 환경 문제입니다.
            """;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("CEN EDU API")
                .version("v0")
                .description(DESCRIPTION));
    }
}
