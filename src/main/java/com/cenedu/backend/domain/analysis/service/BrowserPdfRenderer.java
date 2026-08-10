package com.cenedu.backend.domain.analysis.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로컬 브라우저를 headless 로 띄워 HTML 을 PDF 로 바꾼다.
 *
 * <p><b>배포 환경에 브라우저가 있어야 한다.</b> 개발 기계에서는 이미 깔려 있어 그냥 되지만,
 * 서버 컨테이너에는 보통 없다. 없으면 {@link ErrorCode#REPORT_RENDERER_UNAVAILABLE} 로 답한다.
 * 500 으로 두면 원인을 로그에서 찾아야 하는데, 이 실패는 코드가 아니라 환경 문제다.
 *
 * <p>PDF 라이브러리를 쓰지 않고 브라우저를 쓰는 이유는 보고서가 CSS 로 조판돼 있기 때문이다.
 * 라이브러리로 옮기면 레이아웃을 다시 짜야 한다. 다만 프로세스를 띄우는 방식이라 동시 요청이
 * 늘면 부담이 크다. 교사 한 명이 가끔 누르는 버튼이라는 전제 위에 있다.
 */
@Component
public class BrowserPdfRenderer {

    private final Path outputRoot;
    private final List<String> browserPaths;

    public BrowserPdfRenderer(
            @Value("${app.report.output-dir:output/api-reports}") String outputDir,
            @Value("${app.report.browser-paths:"
                    + "C:/Program Files/Google/Chrome/Application/chrome.exe,"
                    + "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe,"
                    + "/usr/bin/google-chrome,"
                    + "/usr/bin/chromium,"
                    + "/usr/bin/microsoft-edge}") List<String> browserPaths) {
        this.outputRoot = Path.of(outputDir).toAbsolutePath();
        this.browserPaths = browserPaths;
    }

    public RenderedFiles render(String reportId, String html) {
        String browser = findBrowser();
        try {
            Files.createDirectories(outputRoot);
            Path htmlPath = outputRoot.resolve(reportId + ".html");
            Path pdfPath = outputRoot.resolve(reportId + ".pdf");
            Files.writeString(htmlPath, html, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder(
                    browser,
                    "--headless=new",
                    "--disable-gpu",
                    "--no-pdf-header-footer",
                    "--print-to-pdf=" + pdfPath,
                    htmlPath.toUri().toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0 || !Files.exists(pdfPath)) {
                throw new BusinessException(ErrorCode.REPORT_RENDER_FAILED,
                        "PDF 변환에 실패했습니다: " + output);
            }
            return new RenderedFiles(htmlPath, pdfPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.REPORT_RENDER_FAILED, "PDF 변환이 중단됐습니다.");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.REPORT_RENDER_FAILED, "보고서 파일을 만들지 못했습니다.");
        }
    }

    private String findBrowser() {
        return browserPaths.stream()
                .map(String::trim)
                .filter(path -> !path.isBlank())
                .filter(path -> Files.exists(Path.of(path)))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_RENDERER_UNAVAILABLE));
    }

    public record RenderedFiles(Path html, Path pdf) {
    }
}
