package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

@Component
public final class ProblemSemanticOutputParser {
    private final ObjectMapper mapper;

    public ProblemSemanticOutputParser(ObjectProvider<ObjectMapper> mapper) {
        this.mapper = mapper.getIfAvailable(ObjectMapper::new);
    }

    public ProblemSemanticModelV1 parse(String json) {
        try {
            return mapper.readValue(json, ProblemSemanticModelV1.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("semantic model JSON을 해석할 수 없습니다.", e);
        }
    }
}
