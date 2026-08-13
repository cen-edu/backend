package com.cenedu.backend.infra.storage.service;

import java.time.Duration;

/** 이미지 바이트 저장과 제한된 시간의 조회 URL 생성을 제공한다. */
public interface ImageStorageService {

    /** 지정한 버킷과 객체 키에 이미지 바이트를 저장한다. */
    void upload(String bucket, String key, byte[] content, String contentType);

    /** 지정한 객체를 조회할 수 있는 만료 URL을 생성한다. */
    String createGetUrl(String bucket, String key, Duration expiration);
}
