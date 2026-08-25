package com.cenedu.backend.ai.problem;

/** 문제 생성·수정 LLM이 따라야 하는 JSON Schema를 한 곳에서 관리한다. */
public final class ProblemStructuredOutputSchemas {

    private ProblemStructuredOutputSchemas() {
    }

    public static final String SEMANTIC_MODEL = loadSemanticSchema();

    private static String loadSemanticSchema() {
        try (var stream = ProblemStructuredOutputSchemas.class.getResourceAsStream("/ai/problem/problem-semantic-model-v1.schema.json")) {
            if (stream == null) throw new IllegalStateException("semantic model schema resource가 없습니다.");
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(stream);
            flattenAllOf(root, root);
            addTypesForConst(root);
            normalizeOneOf(root);
            constrainEvaluationArea(root, mapper);
            constrainSemanticRequiredFields(root);
            constrainSemanticParameterKeys(root);
            validateOpenAiSubset(root);
            return mapper.writeValueAsString(root);
        } catch (java.io.IOException e) { throw new IllegalStateException("semantic model schema를 읽을 수 없습니다.", e); }
    }

    /** Java enum으로 역직렬화되는 평가 영역을 자유 문자열이 아닌 공통 코드로 제한한다. */
    private static void constrainEvaluationArea(com.fasterxml.jackson.databind.JsonNode root,
                                                com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var intent = root.path("$defs").path("intent").path("properties");
        if (intent.isObject() && intent.has("evaluationArea")) {
            var schema = (com.fasterxml.jackson.databind.node.ObjectNode) intent.get("evaluationArea");
            schema.remove("type");
            var anyOf = mapper.createArrayNode();
            anyOf.addObject().put("type", "string").set("enum", mapper.createArrayNode()
                    .add("UNDERSTANDING").add("CALCULATION").add("REASONING").add("PROBLEM_SOLVING"));
            anyOf.addObject().put("type", "null");
            schema.set("anyOf", anyOf);
        }
    }

    /** snapshot 검증에서 반드시 필요한 guide와 asset 논리 키를 출력 스키마에 반영한다. */
    private static void constrainSemanticRequiredFields(com.fasterxml.jackson.databind.JsonNode root) {
        var defs = root.path("$defs");
        if (defs.isObject()) {
            var presentation = (com.fasterxml.jackson.databind.node.ObjectNode) defs.path("presentation");
            var presentationProperties = (com.fasterxml.jackson.databind.node.ObjectNode) presentation.path("properties");
            presentationProperties.set("learningGuide", defs.path("guide").deepCopy());
            var base = (com.fasterxml.jackson.databind.node.ObjectNode) defs.path("base");
            var baseProperties = (com.fasterxml.jackson.databind.node.ObjectNode) base.path("properties");
            var assetKey = (com.fasterxml.jackson.databind.node.ObjectNode) baseProperties.path("assetKey");
            assetKey.put("pattern", "^F[1-9][0-9]*$");
            var choiceProperties = (com.fasterxml.jackson.databind.node.ObjectNode) defs.path("choice").path("properties");
            var valueKey = (com.fasterxml.jackson.databind.node.ObjectNode) choiceProperties.path("valueKey");
            valueKey.remove("type");
            valueKey.put("type", "string");
            valueKey.put("pattern", "^[A-Z][A-Z0-9_]{0,63}$");
        }
    }

    /** 후단 domain validator와 같은 대문자 논리 키 규칙을 provider 출력 단계에서 강제한다. */
    private static void constrainSemanticParameterKeys(com.fasterxml.jackson.databind.JsonNode root) {
        var key = root.path("$defs").path("parameter").path("properties").path("key");
        if (key instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.put("pattern", "^[A-Z][A-Z0-9_]{0,63}$");
        }
    }

    /** OpenAI strict JSON Schema가 지원하지 않는 allOf를 참조 스키마의 object로 병합한다. */
    private static void flattenAllOf(com.fasterxml.jackson.databind.JsonNode node,
                                     com.fasterxml.jackson.databind.JsonNode root) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            var allOf = object.get("allOf");
            if (allOf != null && allOf.isArray()) {
                var factory = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
                var mergedProperties = factory.objectNode();
                var mergedRequired = factory.arrayNode();
                allOf.forEach(part -> {
                    var resolved = part;
                    if (part.isObject() && part.has("$ref")) {
                        resolved = resolveRef(root, part.get("$ref").asText());
                    }
                    flattenAllOf(resolved, root);
                    if (resolved.isObject()) {
                        resolved.fields().forEachRemaining(entry -> {
                            if (!"allOf".equals(entry.getKey()) && !"$ref".equals(entry.getKey())
                                    && !object.has(entry.getKey())) {
                                object.set(entry.getKey(), entry.getValue().deepCopy());
                            }
                        });
                    }
                    if (resolved.has("properties")) mergedProperties.setAll((com.fasterxml.jackson.databind.node.ObjectNode) resolved.get("properties"));
                    if (resolved.has("required")) {
                        resolved.get("required").forEach(required -> {
                            if (!containsRequired(mergedRequired, required.asText())) mergedRequired.add(required);
                        });
                    }
                });
                object.remove("allOf");
                if (!mergedProperties.isEmpty()) object.set("properties", mergedProperties);
                if (!mergedRequired.isEmpty()) object.set("required", mergedRequired);
            }
            object.fields().forEachRemaining(entry -> flattenAllOf(entry.getValue(), root));
        } else if (node.isArray()) {
            node.forEach(child -> flattenAllOf(child, root));
        }
    }

    /** OpenAI Structured Outputs에서는 oneOf 대신 anyOf를 사용한다. */
    private static void normalizeOneOf(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            if (object.has("oneOf") && !object.has("anyOf")) object.set("anyOf", object.remove("oneOf"));
            object.fields().forEachRemaining(entry -> normalizeOneOf(entry.getValue()));
        } else if (node.isArray()) node.forEach(ProblemStructuredOutputSchemas::normalizeOneOf);
    }

    /** 지원하지 않는 composition과 열린 object를 API 호출 전에 즉시 검출한다. */
    private static void validateOpenAiSubset(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            if (node.has("allOf") || node.has("not") || node.has("dependentRequired")
                    || node.has("dependentSchemas") || node.has("if") || node.has("then") || node.has("else")) {
                throw new IllegalStateException("OpenAI Structured Outputs 호환 스키마에 지원하지 않는 키워드가 있습니다.");
            }
            if ("object".equals(node.path("type").asText()) && !node.has("additionalProperties")) {
                throw new IllegalStateException("OpenAI Structured Outputs object에는 additionalProperties가 필요합니다.");
            }
            node.fields().forEachRemaining(entry -> validateOpenAiSubset(entry.getValue()));
        } else if (node.isArray()) node.forEach(ProblemStructuredOutputSchemas::validateOpenAiSubset);
    }

    private static com.fasterxml.jackson.databind.JsonNode resolveRef(com.fasterxml.jackson.databind.JsonNode root, String ref) {
        if (ref != null && ref.startsWith("#/$defs/")) return root.path("$defs").path(ref.substring("#/$defs/".length()));
        return root;
    }

    private static boolean containsRequired(com.fasterxml.jackson.databind.node.ArrayNode required, String value) {
        for (var existing : required) if (existing.asText().equals(value)) return true;
        return false;
    }

    /** OpenAI strict JSON Schema가 const와 함께 요구하는 primitive type을 보완한다. */
    private static void addTypesForConst(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            var constant = object.get("const");
            if (constant != null && object.get("type") == null) {
                object.put("type", constant.isIntegralNumber() ? "integer" : "string");
            }
            object.fields().forEachRemaining(entry -> addTypesForConst(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(ProblemStructuredOutputSchemas::addTypesForConst);
        }
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
                }},
                "visualRequired":{"type":"boolean"},
                "visualKind":{"type":["string","null"],"enum":["COORDINATE_GRAPH",null]},
                "visualDescription":{"type":["string","null"]}
              },
              "required":["question","contentBlocks","choices","steps","answerUnits","explanation","learningGuide","rubricItems","assets","visualRequired","visualKind","visualDescription"]
            }
            """;

    /** 수정 대상 Delta 출력 계약. 서버 병합기가 기준 Snapshot의 보호 필드를 최종 보존한다. */
    public static final String MODIFICATION_DELTA = """
            {"type":"object","additionalProperties":false,"properties":{
              "question":{"type":"string"},"contentBlocks":{"type":"array"},
              "choices":{"type":"array"},"steps":{"type":"array"},"answerUnits":{"type":"array"},
              "explanation":{"type":"string"},"learningGuide":{"type":"object"},"rubricItems":{"type":"array"},
              "assets":{"type":"array","maxItems":0}
            }}
            """;

    /**
     * 수정 계획에 포함된 대상만 모델 출력 필드로 허용한다.
     *
     * <p>WHOLE_QUESTION·QUESTION_TYPE(action=REPLACE)은 개별 필드가 아니라 "전체를 다시
     * 만들라"는 뜻이라, 아래 field 목록에 없다. 이 둘을 걸러내지 않으면 REPLACE의 targets는
     * 항상 {WHOLE_QUESTION}뿐이라 어떤 if도 걸리지 않고 빈 properties 스키마
     * ({"type":"object","properties":{}})가 나가— 모델이 사실상 아무 필드도 못 담는
     * 스키마를 받는다. 이때는 생성과 같은 {@link #CANDIDATE} 전체 계약을 그대로 쓴다.
     */
    public static String modificationDeltaFor(java.util.Set<com.cenedu.backend.domain.problem.authoring.edit.EditTargetType> targets) {
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.WHOLE_QUESTION)
                || targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.QUESTION_TYPE)) {
            return CANDIDATE;
        }
        java.util.Set<String> fields = new java.util.LinkedHashSet<>();
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.QUESTION_BODY)
                || targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.CONTENT_BLOCK)) {
            fields.add("question"); fields.add("contentBlocks");
        }
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.CHOICE)) fields.add("choices");
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.STEP)) fields.add("steps");
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.ANSWER_UNIT)) fields.add("answerUnits");
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.EXPLANATION)) fields.add("explanation");
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.LEARNING_GUIDE)) fields.add("learningGuide");
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.edit.EditTargetType.RUBRIC_ITEM)) fields.add("rubricItems");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var candidateProps = CANDIDATE_NODE.path("properties");
        var root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        var required = root.putArray("required");
        var properties = root.putObject("properties");
        for (String field : fields) {
            properties.set(field, candidateProps.path(field).deepCopy());
            required.add(field);
        }
        return root.toString();
    }

    /** CANDIDATE 스키마를 한 번만 파싱해 하위 스키마 재사용에 쓴다. */
    private static final com.fasterxml.jackson.databind.JsonNode CANDIDATE_NODE = parseCandidate();

    private static com.fasterxml.jackson.databind.JsonNode parseCandidate() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(CANDIDATE);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("CANDIDATE 스키마를 파싱할 수 없습니다.", e);
        }
    }

    /** Repair 대상을 CANDIDATE의 동일 필드 하위 스키마로 매핑한다. */
    private static String candidateFieldFor(
            com.cenedu.backend.domain.problem.authoring.repair.RepairTarget target) {
        return switch (target) {
            case CONTENT -> "contentBlocks";
            case CHOICES -> "choices";
            case STEPS -> "steps";
            case ANSWERS -> "answerUnits";
            case RUBRIC -> "rubricItems";
            case EXPLANATION -> "explanation";
            case LEARNING_GUIDE -> "learningGuide";
            case ASSET -> "assets";
        };
    }

    /**
     * Repair 계획에 포함된 대상만 모델 출력 필드로 허용하는 스키마를 만든다.
     *
     * <p>OpenAI Structured Outputs는 strict라 union 타입(예: {@code ["object","array"...]})의
     * object 분기에 {@code additionalProperties:false}를 요구한다. 대체 값은 대상마다 형태가 달라
     * union으로는 strict를 만족시킬 수 없으므로, <b>이미 strict-valid로 동작하는 {@link #CANDIDATE}의
     * 동일 필드 하위 스키마를 그대로 재사용</b>한다(예: STEPS→CANDIDATE.properties.steps).
     *
     * <p>계획 대상만 properties·required에 담아, 어댑터의 "계획 밖 대상 거부"와 병합기의
     * "응답 키가 계획과 정확히 일치" 계약을 모델이 자연히 만족하게 한다.
     */
    public static String repairDeltaFor(
            java.util.Set<com.cenedu.backend.domain.problem.authoring.repair.RepairTarget> targets) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var candidateProps = CANDIDATE_NODE.path("properties");
        var root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.set("required", mapper.createArrayNode().add("replacements").add("rationale"));
        var props = root.putObject("properties");
        var replacements = props.putObject("replacements");
        replacements.put("type", "object");
        replacements.put("additionalProperties", false);
        var required = replacements.putArray("required");
        var replacementProps = replacements.putObject("properties");
        // enum 선언 순서로 순회해 스키마 출력을 결정적으로 만든다.
        for (var target : com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.values()) {
            if (!targets.contains(target)) continue;
            replacementProps.set(target.name(), candidateProps.path(candidateFieldFor(target)).deepCopy());
            required.add(target.name());
        }
        props.putObject("rationale").put("type", "string");
        return root.toString();
    }

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
                        "mode":{"type":"string","enum":["PRESENTATIONAL_PATCH","PARAMETRIC_PATCH","CHOICE_REORDER","STRUCTURAL_REGENERATION","RESTORE","REJECTED"]},
                        "operations":{"type":"array","items":{
                          "type":"object","additionalProperties":false,
                          "properties":{
                            "type":{"type":"string","enum":["SET_PARAMETER_VALUE","SET_PARAMETER_UNIT","SET_TEMPLATE_TEXT","SET_DIAGRAM_STYLE","SET_LABEL_TEXT","SET_CHOICE_ORDER"]},
                            "path":{"type":"string"},"expectedOldValue":{"type":["string","null"]},"newValue":{"type":"string"}
                          },
                          "required":["type","path","expectedOldValue","newValue"]
                        }},
                        "assistantMessage":{"type":"string"}
                      },
                      "required":["mode","operations","assistantMessage"]
                    },
                    "requestedSpecification":{"type":["object","null"],"additionalProperties":false,
                      "properties":{
                        "questionType":{"type":["string","null"],"enum":["MULTIPLE_CHOICE","SHORT_INPUT","ESSAY","STEP_FILL",null]},
                        "difficulty":{"type":["string","null"],"enum":["low","mid","high",null]},
                        "requiresAsset":{"type":["boolean","null"]},
                        "differentProblemOnly":{"type":"boolean"},
                        "requiresNewProblem":{"type":"boolean"}
                      },
                      "required":["questionType","difficulty","requiresAsset","differentProblemOnly","requiresNewProblem"]
                    },
                    "assistantMessage":{"type":"string"}
                  },
                  "required":["action","instructionDeltas","semanticPatch","requestedSpecification","assistantMessage"]
                }
              },
              "required":["schemaVersion","problemEditResult"]
            }
            """;
}
