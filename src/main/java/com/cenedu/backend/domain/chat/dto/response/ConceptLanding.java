package com.cenedu.backend.domain.chat.dto.response;

/**
 * 대역 건너뛰기의 착지 결과. 도착한 개념과 앵커에서의 거리를 함께 담는다.
 *
 * <p>홉을 같이 주는 이유는 <b>얼마나 멀리 뛰었는지가 답변에 필요한 정보</b>이기 때문이다.
 * 1홉 착지와 7홉 착지는 학생에게 같은 말로 설명할 수 없다.
 *
 * @param concept 착지한 초등 개념
 * @param hop     앵커에서의 최단 거리. 1 이상이다
 */
public record ConceptLanding(ConceptView concept, int hop) {
}
