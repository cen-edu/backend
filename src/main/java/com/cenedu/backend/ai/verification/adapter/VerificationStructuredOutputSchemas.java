package com.cenedu.backend.ai.verification.adapter;

/** 검증 호출별로 LLM이 반환해야 하는 JSON 구조를 중앙 관리한다. */
public final class VerificationStructuredOutputSchemas {
    private VerificationStructuredOutputSchemas() {}

    public static final String SOLVER = """
            {"type":"object","additionalProperties":false,"required":["solved","answers","reason"],"properties":{
              "solved":{"type":"boolean"},"reason":{"type":"string"},"answers":{"type":"array","items":{
                "type":"object","additionalProperties":false,"required":["unitKey","answer"],"properties":{
                  "unitKey":{"type":"string"},"answer":{"type":"string"}}}}}}
            """;

    public static final String ORIGINAL = """
            {"type":"object","additionalProperties":false,"required":["findings"],"properties":{
              "findings":{"type":"array","items":{"type":"object","additionalProperties":false,
                "required":["type","kind","location","detail"],"properties":{
                  "type":{"type":"string"},"kind":{"type":"string"},"location":{"type":"string"},"detail":{"type":"string"}}}}}}
            """;

    public static final String RUBRIC = """
            {"type":"object","additionalProperties":false,"required":["axis","detail"],"properties":{
              "axis":{"type":"string"},"detail":{"type":"string"}}}
            """;

    public static final String ASSET = """
            {"type":"object","additionalProperties":false,"required":["issue","detail"],"properties":{
              "issue":{"type":"string"},"detail":{"type":"string"}}}
            """;
}
