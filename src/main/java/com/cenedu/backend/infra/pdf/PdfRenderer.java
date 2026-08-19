package com.cenedu.backend.infra.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * HTML 을 PDF 바이트로 바꾼다.
 *
 * <p><b>자바스크립트를 실행하지 않는다.</b> 브라우저가 아니라 HTML·CSS 렌더러라서, 화면의 차트
 * 라이브러리가 그린 결과는 가져올 수 없다. 그래프가 필요하면 CSS 로 그리거나 이미지로 넘겨야 한다.
 *
 * <p>한글 폰트를 직접 등록한다. PDF 는 폰트를 파일에 임베딩하는데 기본 폰트에는 한글 글리프가
 * 없어서, 등록하지 않으면 <b>한글이 통째로 빈칸으로 나온다</b>. 렌더링은 예외 없이 성공하므로
 * 눈으로 보기 전까지 알아채기 어렵다.
 */
@Component
public class PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderer.class);

    /** CSS 의 {@code font-family} 에 쓸 이름. 템플릿과 이 값이 어긋나면 한글이 깨진다. */
    public static final String FONT_FAMILY = "Pretendard";

    private static final String FONT_DIR = "fonts/";
    private static final List<FontFace> FONTS = List.of(
            new FontFace("Pretendard-Regular.ttf", 400),
            new FontFace("Pretendard-Bold.ttf", 700));

    /**
     * 폰트를 임시 파일로 풀어 둔 경로.
     *
     * <p>openhtmltopdf 는 폰트를 {@code File} 로 받는다. jar 안의 리소스는 파일 경로가 없어서
     * 한 번 꺼내 두고 재사용한다. 요청마다 꺼내면 20MB 짜리 보고서를 여러 명이 동시에 뽑을 때
     * 디스크와 GC 를 함께 낭비한다.
     */
    private Path fontDirectory;

    @PostConstruct
    void extractFonts() throws IOException {
        fontDirectory = Files.createTempDirectory("cen-edu-pdf-fonts");
        for (FontFace font : FONTS) {
            Path target = fontDirectory.resolve(font.fileName());
            try (InputStream in = new ClassPathResource(FONT_DIR + font.fileName())
                    .getInputStream()) {
                Files.copy(in, target);
            }
        }
        log.info("PDF 폰트 준비 완료 — {}개, 경로={}", FONTS.size(), fontDirectory);
    }

    @PreDestroy
    void cleanUpFonts() {
        if (fontDirectory == null) {
            return;
        }
        try (var paths = Files.walk(fontDirectory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("PDF 폰트 임시 파일 삭제 실패 — {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("PDF 폰트 임시 디렉터리 정리 실패 — {}", fontDirectory);
        }
    }

    /**
     * 완성된 HTML 문서를 PDF 로 만든다.
     *
     * @param html    {@code <html>} 부터 닫는 태그까지 갖춘 XHTML. <b>닫히지 않은 태그가 있으면
     *                파싱 단계에서 실패한다</b> — 브라우저처럼 관대하지 않다
     * @param baseUri 상대 경로 리소스를 찾을 기준. 없으면 {@code null}
     * @throws PdfRenderException 렌더링에 실패했을 때
     */
    public byte[] render(String html, String baseUri) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, baseUri)
                    .toStream(out);
            for (FontFace font : FONTS) {
                Path path = fontDirectory.resolve(font.fileName());
                builder.useFont(path.toFile(), FONT_FAMILY, font.weight(),
                        PdfRendererBuilder.FontStyle.NORMAL, true);
            }
            builder.run();
        } catch (Exception e) {
            throw new PdfRenderException("PDF 를 만들지 못했습니다.", e);
        }
        return out.toByteArray();
    }

    private record FontFace(String fileName, int weight) {
    }
}
