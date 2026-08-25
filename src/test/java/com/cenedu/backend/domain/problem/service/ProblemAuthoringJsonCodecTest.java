package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class ProblemAuthoringJsonCodecTest {

    private final ProblemAuthoringJsonCodec codec = new ProblemAuthoringJsonCodec(new ObjectMapper());

    @Test
    void writeStripsNullCharacterSoJsonbInsertDoesNotFail() {
        // PostgreSQL jsonb는 U+0000을 담을 수 없어, 생성 콘텐츠에 널 문자가 섞이면
        // "unsupported Unicode escape sequence"로 insert가 실패한다. write가 이를 제거해야 한다.
        String withNull = "a" + ((char) 0) + "b";
        String json = codec.write(Map.of("text", withNull));

        assertThat(json).doesNotContain("\\u0000");
        assertThat(json).doesNotContain(String.valueOf((char) 0));
        assertThat(json).contains("\"ab\"");
    }

    @Test
    void writePreservesNormalContentIncludingSpaces() {
        String json = codec.write(Map.of("text", "정수의 합 12"));

        assertThat(json).contains("정수의 합 12");
    }
}
