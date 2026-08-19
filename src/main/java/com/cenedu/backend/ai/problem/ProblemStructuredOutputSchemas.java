package com.cenedu.backend.ai.problem;

/** 문제 생성·수정 LLM이 따라야 하는 JSON Schema를 한 곳에서 관리한다. */
public final class ProblemStructuredOutputSchemas {

    private ProblemStructuredOutputSchemas() {
    }

    public static final String SEMANTIC_MODEL = loadSemanticSchema();

    private static String loadSemanticSchema() {
        try (var stream = ProblemStructuredOutputSchemas.class.getResourceAsStream("/ai/problem/problem-semantic-model-v1.schema.json")) {
            if (stream == null) throw new IllegalStateException("semantic model schema resource가 없습니다.");
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException("semantic model schema를 읽을 수 없습니다.", e); }
    }

    /** 생성 후보와 확정 수정 후보가 공유하는 교육 내용 출력 계약이다. */
    public static final String CANDIDATE = """
            {
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "question":{"type":"string"},
                "contentBlocks":{"type":"array","minItems":1,"items":{
                  "type":"object","additionalProperties":false,
                  "properties":{
                    "blockKind":{"type":"string","enum":["TEXT","FIGURE","TABLE"]},
                    "text":{"type":["string","null"]},
                    "assetRef":{"type":["string","null"]},
                    "markup":{"type":["string","null"]}
                  },
                  "required":["blockKind","text","assetRef","markup"]
                }},
                "choices":{"type":"array","items":{
                  "type":"object","additionalProperties":false,
                  "properties":{"content":{"type":"string"}},
                  "required":["content"]
                }},
                "steps":{"type":"array","items":{
                  "type":"object","additionalProperties":false,
                  "properties":{
                    "label":{"type":"string"},
                    "segments":{"type":"array","minItems":1,"items":{
                      "type":"object","additionalProperties":false,
                      "properties":{
                        "type":{"type":"string","enum":["TEXT","BLANK","ANSWER_REF"]},
                        "text":{"type":["string","null"]},
                        "answerUnitIndex":{"type":["integer","null"],"minimum":0}
                      },
                      "required":["type","text","answerUnitIndex"]
                    }}
                  },
                  "required":["label","segments"]
                }},
                "answerUnits":{"type":"array","items":{
                  "type":"object","additionalProperties":false,
                  "properties":{
                    "stepIndex":{"type":["integer","null"],"minimum":0},
                    "answerRaw":{"type":["string","null"]},
                    "compareMethod":{"type":"string","enum":["CHOICE","VALUE","EXACT","SET","SUBST","RUBRIC"]},
                    "diagnosticType":{"type":["string","null"],"enum":["INTERPRET","MODEL","EXECUTE","ANSWER",null]},
                    "displayUnit":{"type":["string","null"]}
                  },
                  "required":["stepIndex","answerRaw","compareMethod","diagnosticType","displayUnit"]
                }},
                "explanation":{"type":"string"},
                "learningGuide":{"type":"object","additionalProperties":false,
                  "properties":{
                    "conceptTitle":{"type":"string"},
                    "summary":{"type":"string"},
                    "keyPoints":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"string"}}
                  },
                  "required":["conceptTitle","summary","keyPoints"]
                },
                "rubricItems":{"type":"array","items":{
                  "type":"object","additionalProperties":false,
                  "properties":{
                    "criterion":{"type":"string"},
                    "weightPercent":{"type":"integer","minimum":1,"maximum":100}
                  },
                  "required":["criterion","weightPercent"]
                }},
                "assets":{"type":"array","maxItems":0,"items":{
                  "type":"object","additionalProperties":false,
                  "properties":{
                    "role":{"type":"string"},
                    "outputFormat":{"type":"string"},
                    "altText":{"type":"string"},
                    "visualDescription":{"type":"string"},
                    "requiredElements":{"type":"array","items":{"type":"string"}},
                    "forbiddenElements":{"type":"array","items":{"type":"string"}},
                    "renderData":{"type":"object","additionalProperties":false,"properties":{}}
                  },
                  "required":["role","outputFormat","altText","visualDescription","requiredElements","forbiddenElements","renderData"]
                }}
              },
              "required":["question","contentBlocks","choices","steps","answerUnits","explanation","learningGuide","rubricItems","assets"]
            }
            """;

    /** 사용자 수정 대화 한 턴의 분류·지시 추출 계약이다. */
    public static final String EDIT_TURN = """
            {
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "schemaVersion":{"type":"integer","enum":[2]},
                "problemEditResult":{"type":"object","additionalProperties":false,
                  "properties":{
                    "action":{"type":"string","enum":["CONTINUE_COLLECTION","REQUEST_CONFIRMATION","CONFIRM_EXECUTION","CANCEL"]},
                    "instructionDeltas":{"type":"array","items":{
                      "type":"object","additionalProperties":false,
                      "properties":{
                        "targetType":{"type":"string","enum":["QUESTION_BODY","CONTENT_BLOCK","CHOICE","STEP","ANSWER_UNIT","EXPLANATION","LEARNING_GUIDE","RUBRIC_ITEM","ASSET","QUESTION_TYPE","DIFFICULTY","WHOLE_QUESTION"]},
                        "targetKey":{"type":["string","null"]},
                        "changeNature":{"type":"string","enum":["PRESENTATIONAL","SEMANTIC","STRUCTURAL"]},
                        "instruction":{"type":"string"}
                      },
                      "required":["targetType","targetKey","changeNature","instruction"]
                    }},
                    "semanticPatch":{"type":["object","null"],"additionalProperties":false,
                      "properties":{
                        "mode":{"type":"string","enum":["PRESENTATIONAL_PATCH","PARAMETRIC_PATCH","STRUCTURAL_REGENERATION","RESTORE","REJECTED"]},
                        "operations":{"type":"array","items":{
                          "type":"object","additionalProperties":false,
                          "properties":{
                            "type":{"type":"string","enum":["SET_PARAMETER_VALUE","SET_PARAMETER_UNIT","SET_TEMPLATE_TEXT","SET_DIAGRAM_STYLE","SET_LABEL_TEXT"]},
                            "path":{"type":"string"},"expectedOldValue":{"type":["string","null"]},"newValue":{"type":"string"}
                          },
                          "required":["type","path","expectedOldValue","newValue"]
                        }},
                        "assistantMessage":{"type":"string"}
                      },
                      "required":["mode","operations","assistantMessage"]
                    },
                    "assistantMessage":{"type":"string"}
                  },
                  "required":["action","instructionDeltas","assistantMessage"]
                }
              },
              "required":["schemaVersion","problemEditResult"]
            }
            """;
}
