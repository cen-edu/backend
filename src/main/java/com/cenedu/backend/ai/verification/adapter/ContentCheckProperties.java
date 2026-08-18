package com.cenedu.backend.ai.verification.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 원본 검사 토글. {@code app.ai.verification.content-check} 를 받는다.
 *
 * <p>끄면 문항당 LLM 호출이 절반으로 줄어든다. 골든셋 측정과 개발 반복에서 쓴다.
 *
 * <p>꺼져 있을 때 해당 Finding 을 <b>만들지 않는다</b>. {@code PASS} 로 내지 않는다 —
 * 검사하지 않은 것을 통과로 기록하면 이후 아무도 그 문항이 안 봐진 것을 모른다.
 *
 * <p>{@code @DefaultValue("true")} 를 붙인 이유: boolean 은 설정이 없으면 {@code false} 로
 * 바인딩된다. 그대로 두면 설정 한 줄이 빠진 프로필에서 검사가 조용히 사라진다.
 *
 * @param enabled 기본 {@code true}
 */
@ConfigurationProperties(prefix = "app.ai.verification.content-check")
public record ContentCheckProperties(@DefaultValue("true") boolean enabled) {
}
