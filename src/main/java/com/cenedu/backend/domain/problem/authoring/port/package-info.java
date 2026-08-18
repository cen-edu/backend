/**
 * Problem 도메인이 생성·검증·자산 실행 구현에 의존하는 역할 기반 Port를 담는다.
 *
 * <p>Port와 입출력 계약은 Problem 도메인(이하영)이 소유한다. 생성·자산 Adapter는
 * 이하영, 검증 Adapter는 배세빈이 구현한다. Adapter는 이 패키지에서 정한 스키마를
 * 소비할 뿐 상태 전이나 영속화 정책을 가져가지 않는다.
 */
package com.cenedu.backend.domain.problem.authoring.port;
