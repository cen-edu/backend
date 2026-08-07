package com.cenedu.backend.ai.dispatcher;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * 한 번의 디스패치에 걸린 로그를 한 줄로 꿸 수 있게 MDC 에 상관관계 ID 를 채운다.
 *
 * <p><b>이미 값이 있으면 건드리지 않고, 없을 때만 만든다.</b> 나중에 {@code global} 에
 * {@code X-Request-Id} 헤더를 MDC 로 옮기는 서블릿 필터가 생기면, 디스패처 코드를 고치지 않아도
 * 그 값을 그대로 이어받는다. 필터가 없는 지금은 디스패치 단위로라도 묶이게 해 둔다.
 *
 * <p>{@link #close()} 에서 <b>자기가 만든 경우에만</b> 지우는 게 핵심이다. 무조건 지우면 남이 넣은
 * 값을 뺏고, 아예 안 지우면 톰캣이 스레드를 재사용할 때 앞 요청의 ID 가 다음 요청 로그에 붙는다.
 * 그렇게 되면 로그가 없는 것보다 나쁘다 — 틀린 ID 로 묶인 로그를 믿고 원인을 찾게 된다.
 *
 * <p>에이전트가 내부에서 비동기로 갈라져야 한다면 MDC 는 스레드를 따라가지 않으므로,
 * 호출 스레드에서 {@code MDC.get("traceId")} 로 읽어 직접 넘긴다.
 */
final class TraceId implements AutoCloseable {

    static final String MDC_KEY = "traceId";

    /** 사람이 로그에서 눈으로 따라가고 복사할 값이라 UUID 전체 대신 앞 8자만 쓴다. */
    private static final int LENGTH = 8;

    private final boolean created;

    private TraceId(boolean created) {
        this.created = created;
    }

    static TraceId ensure() {
        if (MDC.get(MDC_KEY) != null) {
            return new TraceId(false);
        }
        MDC.put(MDC_KEY, UUID.randomUUID().toString().substring(0, LENGTH));
        return new TraceId(true);
    }

    @Override
    public void close() {
        if (created) {
            MDC.remove(MDC_KEY);
        }
    }
}
