package com.cenedu.backend.ai.chat.agent;

import java.util.List;

import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;

/**
 * 파이프라인 한 번의 결과. {@code SolveChatAgent} 는 이 중 {@link #text()} 만 쓴다.
 *
 * <p>중간값까지 같이 돌려주는 이유는 <b>실패 지점을 분리해 측정하기 위해서</b>다. 키워드 추출이
 * 나쁜 것과 검색이 못 닿은 것과 생성이 근거를 무시한 것은 고칠 곳이 다른데, 텍스트만 돌려주면
 * 로그를 파싱하지 않고는 셋을 구분할 수 없다.
 *
 * @param keywords   1차 LLM 이 뽑은 키워드. 파싱에 실패했으면 빈 리스트
 * @param context    조회한 근거
 * @param generation 2차 LLM 응답. 근거가 전무해 호출하지 않았으면 {@code null}
 */
public record ConceptChatResult(
        String text,
        List<String> keywords,
        KeywordParse keywordParse,
        ConceptContext context,
        LlmResponse generation
) {

    /** 1차 응답을 JSON 배열로 읽어낸 방식. 모델이 형식을 어기는 빈도를 재려고 남긴다. */
    public enum KeywordParse {
        /** 지시대로 JSON 배열만 왔다. */
        PARSED,
        /** 코드펜스가 붙어 와서 벗겨낸 뒤 읽었다. */
        PARSED_AFTER_FENCE_STRIP,
        /** 어느 쪽으로도 읽지 못해 키워드 없이 진행했다. */
        FAILED,
    }

    /** 근거가 전무해 2차 LLM 을 부르지 않은 경우. */
    public static ConceptChatResult noEvidence(
            String text, List<String> keywords, KeywordParse keywordParse, ConceptContext context) {
        return new ConceptChatResult(text, keywords, keywordParse, context, null);
    }
}
