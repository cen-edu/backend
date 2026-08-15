/**
 * 채팅 도메인의 시스템 트리거 Port를 구현하는 AI Adapter. 배세빈이 담당한다.
 *
 * <p>사용자 프롬프트 경로가 아니므로 Dispatcher를 거치지 않는다. 사용자 입력 문자열을 인자로
 * 받지 않고, 도메인이 조립한 시스템 요청만 받아 공통 AI Client를 호출한다.
 */
package com.cenedu.backend.ai.chat.adapter;
