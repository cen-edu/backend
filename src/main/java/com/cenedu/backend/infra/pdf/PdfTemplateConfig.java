package com.cenedu.backend.infra.pdf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * PDF 템플릿 전용 Thymeleaf 엔진.
 *
 * <p>Spring Boot 가 자동 설정하는 엔진을 쓰지 않고 따로 둔다. 이유는 <b>템플릿 모드</b>다.
 * 자동 설정은 HTML 모드라 {@code <meta charset="utf-8">} 처럼 닫히지 않은 태그를 그대로
 * 내보내는데, openhtmltopdf 는 XHTML 을 요구해서 그 순간 렌더링이 통째로 실패한다.
 *
 * <p>XML 모드로 두면 <b>템플릿을 저장하는 시점이 아니라 파싱 시점에</b> 형식 오류가 드러난다.
 * PDF 를 뽑아 보고 나서야 태그가 안 닫혔다는 걸 알게 되는 것보다 낫다.
 *
 * <p>확장자를 {@code .xhtml} 로 둔 것도 같은 이유다. 나중에 화면용 HTML 템플릿이 생겨도
 * 서로 섞이지 않는다.
 */
@Configuration
public class PdfTemplateConfig {

    @Bean
    public SpringTemplateEngine pdfTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".xhtml");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
