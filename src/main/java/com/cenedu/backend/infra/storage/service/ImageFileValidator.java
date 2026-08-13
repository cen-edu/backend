package com.cenedu.backend.infra.storage.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 업로드 파일이 크기 제한 안의 정상 PNG 또는 JPEG 이미지인지 검사한다. */
@Component
public class ImageFileValidator {

    static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 파일의 원본 바이트와 매직 바이트, 실제 디코딩 가능 여부를 검사한다. */
    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_FILE_REQUIRED);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }

        try {
            byte[] content = file.getBytes();
            String contentType = detectContentType(content);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
            if (contentType == null || decoded == null) {
                throw new BusinessException(ErrorCode.IMAGE_INVALID_FORMAT);
            }
            return new ValidatedImage(
                    content, contentType, decoded.getWidth(), decoded.getHeight());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID_FORMAT);
        }
    }

    private String detectContentType(byte[] content) {
        if (isPng(content)) {
            return "image/png";
        }
        if (isJpeg(content)) {
            return "image/jpeg";
        }
        return null;
    }

    private boolean isPng(byte[] content) {
        byte[] signature = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF;
    }
}
