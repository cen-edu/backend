package com.cenedu.backend.infra.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageFileValidatorTest {

    private final ImageFileValidator validator = new ImageFileValidator();

    @Test
    @DisplayName("정상 PNG 파일은 원본 바이트와 실제 Content-Type을 반환한다")
    void acceptsPngImage() throws Exception {
        byte[] png = imageBytes("png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "answer.png", "application/octet-stream", png);

        ValidatedImage image = validator.validate(file);

        assertThat(image.content()).isEqualTo(png);
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(1);
        assertThat(image.height()).isEqualTo(1);
    }

    @Test
    @DisplayName("파일 이름이 이미지여도 실제 바이트가 이미지가 아니면 거부한다")
    void rejectsInvalidImageBytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "answer.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_INVALID_FORMAT);
    }

    @Test
    @DisplayName("5MB를 넘는 파일은 이미지 디코딩 전에 거부한다")
    void rejectsOversizedFile() {
        byte[] oversized = new byte[(int) ImageFileValidator.MAX_IMAGE_SIZE + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "answer.png", "image/png", oversized);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
