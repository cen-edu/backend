package com.cenedu.backend.ai.agent;

import java.util.Objects;

/**
 * 요청을 일으킨 사용자. 에이전트는 토큰을 직접 파싱하지 않고 이 값을 인자로 받는다.
 *
 * <p>인증은 시큐리티 필터가, 소유권 검증은 도메인 서비스가 이미 끝낸 뒤에 채워진다.
 * 에이전트가 여기서 다시 권한을 판단하지 않는다.
 *
 * @param userId 교사 또는 학생의 식별자
 * @param role   요청자의 역할
 */
public record Actor(Long userId, Role role) {

    /**
     * 요청자의 역할.
     *
     * <p>{@code global/common/enums} 에 공용 Role 이 생기면 이 중첩 enum 을 지우고 그 타입으로 바꾼다.
     * 남의 패키지에 파일을 만들지 않으려고 지금은 여기에 둔다.
     *
     * <p>문자열이 아니라 enum 인 이유: 역할 검증이 입력 가드레일의 첫 판정 대상인데,
     * 문자열 비교는 {@code "STUDENT"} / {@code "ROLE_STUDENT"} / {@code "student"} 사이에서
     * 조용히 통과한다. 막아야 할 요청을 통과시키는 실패는 눈에 띄지 않는다.
     */
    public enum Role {
        TEACHER,
        STUDENT,
    }

    public Actor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }
}
