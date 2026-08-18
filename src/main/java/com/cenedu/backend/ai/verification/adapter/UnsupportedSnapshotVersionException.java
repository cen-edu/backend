package com.cenedu.backend.ai.verification.adapter;

/**
 * 검증 Adapter 가 모르는 스냅샷 스키마 버전을 받았을 때 던진다.
 *
 * <p>{@code BusinessException} 을 쓰지 않는다. 이건 HTTP 응답으로 나가는 사용자 오류가 아니라
 * 검증 판정을 낼 수 없다는 내부 사실이고, Adapter 진입점이 잡아 보고서로 바꾼다.
 */
public class UnsupportedSnapshotVersionException extends RuntimeException {

    public UnsupportedSnapshotVersionException(String message) {
        super(message);
    }
}
