package com.cenedu.backend.infra.storage.service;

/** 검증을 통과한 이미지 원본 바이트와 실제 형식. */
public record ValidatedImage(
        byte[] content,
        String contentType,
        int width,
        int height
) {
}
