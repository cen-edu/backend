package com.cenedu.backend.ai.agent;

/**
 * 요청을 일으킨 사용자. 에이전트는 토큰을 직접 파싱하지 않고 이 값을 인자로 받는다.
 *
 * <p>인증은 시큐리티 필터가, 소유권 검증은 도메인 서비스가 이미 끝낸 뒤에 채워진다.
 * 에이전트가 여기서 다시 권한을 판단하지 않는다.
 *
 * @param userId 교사 또는 학생의 식별자
 * @param role   {@code TEACHER} 또는 {@code STUDENT}. {@code global/common/enums} 에 공용 Role 이
 *               생기면 그 타입으로 바꾼다. 남의 패키지에 파일을 만들지 않으려고 지금은 문자열로 둔다.
 */
public record Actor(Long userId, String role) {
}
