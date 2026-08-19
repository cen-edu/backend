package com.cenedu.backend.ai.problem.adapter;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** LLM이 생성하는 교육 내용 전용 출력 계약이다. 서버 ID와 처리 상태는 포함하지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProblemGenerationOutput(
        String question,
        List<ContentBlockOutput> contentBlocks,
        List<ChoiceOutput> choices,
        List<StepOutput> steps,
        List<AnswerUnitOutput> answerUnits,
        String explanation,
        LearningGuideOutput learningGuide,
        List<RubricOutput> rubricItems,
        List<AssetOutput> assets
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlockOutput(String blockKind, String text, String assetRef, String markup) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChoiceOutput(String content) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepOutput(String label, List<SegmentOutput> segments) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SegmentOutput(String type, String text, Integer answerUnitIndex) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnswerUnitOutput(Integer stepIndex, String answerRaw, String compareMethod,
                                   String diagnosticType, String displayUnit) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LearningGuideOutput(String conceptTitle, String summary, List<String> keyPoints) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RubricOutput(String criterion, int weightPercent) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssetOutput(String role, String outputFormat, String altText,
                              String visualDescription, List<String> requiredElements,
                              List<String> forbiddenElements, Map<String, Object> renderData) {}
}
