package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

/**
 * 복습 화면의 개념 정리. {@code problem_question.learning_guide}에서 세 키만 골라 담는다.
 *
 * <p>{@code questionSourceRef}·{@code source}·{@code status}는 <b>이 record에 자리가 없다</b>.
 * {@code source.datasets}에는 내부 출처가, {@code status}에는 내부 품질 등급이 들어 있다.
 * {@code null}로 채우는 필드를 두면 나중에 누가 값을 넣는 순간 그대로 새어 나가고, jsonb를
 * 통째로 직렬화해도 마찬가지다.
 *
 * <p>명세의 {@code example}도 두지 않는다 — 원천에 대응하는 키가 없다(실측).
 */
public record StudentResultConceptResponse(String title, String summary, List<String> points) {
}
