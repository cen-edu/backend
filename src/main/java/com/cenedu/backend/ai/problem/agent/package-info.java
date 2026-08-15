/**
 * 교사의 문제 수정 프롬프트를 처리하는 Agent 구현. 이하영이 담당한다.
 *
 * <p>{@code AgentDispatcher}만 이 경로에 진입한다. Agent는 인증·DB 조회·대화 저장을 하지 않고,
 * 도메인 서비스가 검증한 사용자와 문제 컨텍스트를 요청으로 받는다.
 */
package com.cenedu.backend.ai.problem.agent;
