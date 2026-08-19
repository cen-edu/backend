package com.cenedu.backend.ai.grading.adapter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * 학생 답안 텍스트를 <b>활자</b> 이미지로 렌더한다. 2a 전용 재료다.
 *
 * <p>필기 인식은 단계 4 의 측정 대상이지 단계 2 의 확인 대상이 아니다. 여기서 봐야 할 것은
 * "이미지가 실제로 모델에 들어가고 판정 JSON 이 돌아오는가" 하나라, 읽기 어려움을 변수로
 * 넣지 않는다. 실제 필기(2b)는 이 자리에 그대로 갈아 끼운다.
 */
final class TypesetAnswerImage {

    private static final int WIDTH = 960;
    private static final int PADDING = 48;
    private static final int LINE_HEIGHT = 60;
    private static final float FONT_SIZE = 34f;

    private TypesetAnswerImage() {
    }

    static byte[] renderPng(List<String> lines, Path fontFile) throws IOException {
        Font font;
        try {
            font = Font.createFont(Font.TRUETYPE_FONT, fontFile.toFile()).deriveFont(Font.PLAIN, FONT_SIZE);
        } catch (java.awt.FontFormatException exception) {
            throw new IOException("폰트를 읽지 못했다: " + fontFile, exception);
        }

        int height = PADDING * 2 + LINE_HEIGHT * lines.size();
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, WIDTH, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(font);

        int y = PADDING + LINE_HEIGHT;
        for (String line : lines) {
            graphics.drawString(line, PADDING, y);
            y += LINE_HEIGHT;
        }
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * data URI 로 감싼다. OpenAI 는 {@code image_url} 에 data URI 를 받고, Spring AI 는 문자열
     * URL 을 그대로 실어 보낸다.
     *
     * <p>운영 경로는 S3 presigned URL 이다(D4). <b>같은 자리에 문자열만 다른 것</b>이라 어댑터
     * 코드는 두 경우에 같다 — 2a 를 위해 팀 버킷에 시험용 객체를 올리지 않으려고 이쪽을 쓴다.
     */
    static String toDataUri(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
