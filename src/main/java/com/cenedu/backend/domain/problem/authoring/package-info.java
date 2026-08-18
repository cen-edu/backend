/**
 * 문제 생성·수정·검증 흐름이 공유하는 Problem 도메인의 AI 독립 저작 계약을 담는다.
 *
 * <p>이하영이 소유하며 스냅샷, 명령, 후보, 검증 계약과 Port만 둔다. LLM·Spring AI·OpenAI
 * SDK 구현은 이 패키지에 두지 않고 {@code com.cenedu.backend.ai.problem} 또는
 * {@code com.cenedu.backend.ai.verification}에서 Port를 구현한다.
 */
package com.cenedu.backend.domain.problem.authoring;
