/**
 * 시스템이 요청하는 문제 생성 Port의 AI 구현. 이하영이 담당한다.
 *
 * <p>사용자 프롬프트 경로가 아니므로 Dispatcher를 거치지 않는다. 사용자 입력 문자열을 인자로
 * 받지 않고, 문제 도메인이 조립한 생성 조건만 받아 공통 AI Client를 호출한다.
 */
package com.cenedu.backend.ai.problem.adapter;
