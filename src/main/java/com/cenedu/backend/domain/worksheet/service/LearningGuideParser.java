package com.cenedu.backend.domain.worksheet.service;

import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.worksheet.dto.response.StudentResultConceptResponse;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 개념 정리(명세 8.5)를 읽는 규칙. 복습 화면({@link StudentResultQueryService})과 풀이
 * 화면({@link StudentWorksheetQueryService})이 같은 규칙을 써야 한다 — 한쪽에 복사하면
 * 아래 키 제한이 한쪽에서만 지켜진다.
 */
final class LearningGuideParser {

    private LearningGuideParser() {
    }

    /**
     * {@code learning_guide} jsonb에서 <b>세 키만 골라</b> 읽는다 — 통째로 역직렬화하면
     * 내부 출처({@code source.datasets})와 품질 등급({@code status})이 함께 실린다.
     */
    static StudentResultConceptResponse parse(ObjectMapper objectMapper, ProblemQuestion question) {
        String learningGuide = question.getLearningGuide();
        if (learningGuide == null || learningGuide.isBlank()) {
            return null;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(learningGuide);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID);
        }
        List<String> points = new ArrayList<>();
        for (JsonNode point : node.path("keyPoints")) {
            points.add(point.asString());
        }
        return new StudentResultConceptResponse(
                node.path("conceptTitle").asString(null),
                node.path("summary").asString(null),
                List.copyOf(points));
    }
}
