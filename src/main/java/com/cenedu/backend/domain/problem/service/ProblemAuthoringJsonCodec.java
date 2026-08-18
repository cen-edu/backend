package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Session·Version JSONB 컬럼을 공통 계약 타입과 안전하게 변환한다. */
@Component
public class ProblemAuthoringJsonCodec {

    private final ObjectMapper objectMapper;

    public ProblemAuthoringJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 구조화된 작성 데이터를 JSONB 저장 문자열로 변환한다. */
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
        }
    }

    /** JSONB 문자열을 명시한 S2 계약 타입으로 복원한다. */
    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
        }
    }
}
