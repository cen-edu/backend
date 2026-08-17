/**
 * 문제 검증 Port의 시스템 호출 구현. 배세빈이 담당한다.
 *
 * <p>문제 후보가 만들어진 뒤 도메인 Coordinator가 Port를 통해 호출한다. 사용자 입력 문자열을
 * 인자로 받지 않으며 Blind 변환·독립 풀이·정답 비교의 내부 구현을 소유한다.
 *
 * <p><b>배세빈 팀원 구현 범위:</b>
 * <ul>
 *   <li>{@code ProblemVerificationPort}를 구현한다.</li>
 *   <li>{@code CONTENT}는 정답·해설·난이도·교육과정·수정 보호 범위를 판정한다.</li>
 *   <li>{@code ASSET}은 manifest의 준비 상태·altText·본문 정합성을 판정한다.</li>
 *   <li>입력 {@code verificationRequestId}를 그대로 반환해 멱등성을 유지한다.</li>
 * </ul>
 *
 * <p>이 Adapter는 Problem·Worksheet·Grading Repository를 직접 조회하지 않는다.
 * Version 저장, 재시도, current 승격, 최종화는 이하영 담당
 * {@code ProblemCandidateProcessingService}의 책임이다.
 */
package com.cenedu.backend.ai.verification.adapter;
