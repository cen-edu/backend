package com.cenedu.backend.infra.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PdfRendererTest {

    private PdfRenderer renderer;

    @BeforeEach
    void setUp() throws IOException {
        renderer = new PdfRenderer();
        renderer.extractFonts();
    }

    @AfterEach
    void tearDown() {
        renderer.cleanUpFonts();
    }

    @Test
    @DisplayName("한글이 깨지지 않고 PDF 로 들어간다")
    void rendersKoreanText() throws IOException {
        byte[] pdf = renderer.render(html("정답률은 33.3%로 학급 평균보다 높습니다."), null);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("정답률은 33.3%로 학급 평균보다 높습니다.");
        }
    }

    @Test
    @DisplayName("한글 폰트가 파일에 임베딩된다")
    void embedsKoreanFont() throws IOException {
        byte[] pdf = renderer.render(html("소인수분해"), null);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<PDFont> fonts = fontsOf(document);
            assertThat(fonts).isNotEmpty();
            assertThat(fonts).allSatisfy(font ->
                    assertThat(font.isEmbedded())
                            .describedAs("폰트 %s 가 임베딩되지 않으면 다른 PC 에서 한글이 깨진다",
                                    font.getName())
                            .isTrue());
            assertThat(fonts).anySatisfy(font ->
                    assertThat(font.getName()).contains("Pretendard"));
        }
    }

    @Test
    @DisplayName("굵기를 두 가지로 쓰면 폰트가 두 벌 들어간다")
    void embedsBothWeights() throws IOException {
        String body = "<p>보통 굵기</p><p style=\"font-weight: 700\">굵은 글씨</p>";
        byte[] pdf = renderer.render(html(body), null);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(fontsOf(document)).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("닫히지 않은 태그가 있으면 렌더링에 실패한다")
    void rejectsMalformedHtml() {
        String broken = """
                <html><head><meta charset="utf-8"/></head>
                <body><p>닫지 않은 문단</body></html>
                """;

        assertThatThrownBy(() -> renderer.render(broken, null))
                .isInstanceOf(PdfRenderException.class);
    }

    private List<PDFont> fontsOf(PDDocument document) throws IOException {
        List<PDFont> fonts = new ArrayList<>();
        for (PDPage page : document.getPages()) {
            for (COSName name : page.getResources().getFontNames()) {
                fonts.add(page.getResources().getFont(name));
            }
        }
        return fonts;
    }

    /** openhtmltopdf 는 XHTML 을 요구한다. 브라우저처럼 관대하지 않아 태그를 모두 닫아야 한다. */
    private String html(String body) {
        return """
                <html>
                <head>
                    <meta charset="utf-8"/>
                    <style>
                        @page { size: A4; margin: 20mm; }
                        body { font-family: "%s"; font-size: 11pt; }
                    </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(PdfRenderer.FONT_FAMILY, body);
    }
}
