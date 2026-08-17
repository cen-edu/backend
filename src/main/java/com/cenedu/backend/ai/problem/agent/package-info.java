/**
 * 교사의 문제 수정 프롬프트를 처리하는 Agent 구현. 이하영이 담당한다.
 *
 * <p>{@code AgentDispatcher}만 이 경로에 진입한다. Agent는 인증·DB 조회·대화 저장을 하지 않고,
 * 도메인 서비스가 검증한 사용자와 문제 컨텍스트를 요청으로 받는다.
 *
 * <p><b>이동규 팀원과 맞출 부분:</b> 이동규 팀원은 이 Agent의 문제 수정 로직을
 * 구현하지 않고, {@code AgentKind.PROBLEM_EDIT}·입출력 Guard·traceId를 적용하는
 * Dispatcher 공통 경로를 소유한다. S3 연동 전에 {@code AgentResponse.data}에
 * {@code ProblemEditConversationResult}를 담는 방식만 두 담당자가 합의한다.
 */
package com.cenedu.backend.ai.problem.agent;
