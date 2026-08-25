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
            return sanitizeForJsonb(objectMapper.writeValueAsString(value));
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
        }
    }

    /**
     * PostgreSQL jsonb는 널 문자(U+0000)를 담을 수 없어, 생성 콘텐츠에 섞여 들어오면
     * "unsupported Unicode escape sequence"로 insert가 실패한다. Jackson이 널 문자를
     * {@code \\u0000} 이스케이프로 직렬화하므로 그 표기와 혹시 남은 실제 널 문자를 함께 제거한다.
     * 널 문자는 문항 콘텐츠에 정당한 쓰임이 없어 제거해도 안전하다.
     */
    private static String sanitizeForJsonb(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        return json.replaceAll("(?i)\\\\u0000", "");
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
