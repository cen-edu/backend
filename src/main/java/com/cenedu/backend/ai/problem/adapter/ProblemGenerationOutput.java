package com.cenedu.backend.ai.problem.adapter;

import java.util.List;
import java.util.Map;

/** LLM이 생성하는 교육 내용 전용 출력 계약이다. 서버 ID와 처리 상태는 포함하지 않는다. */
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
    public record ContentBlockOutput(String blockKind, String text, String assetRef, String markup) {}
    public record ChoiceOutput(String content) {}
    public record StepOutput(String label, List<SegmentOutput> segments) {}
    public record SegmentOutput(String type, String text, Integer answerUnitIndex) {}
    public record AnswerUnitOutput(Integer stepIndex, String answerRaw, String compareMethod,
                                   String diagnosticType, String displayUnit) {}
    public record LearningGuideOutput(String conceptTitle, String summary, List<String> keyPoints) {}
    public record RubricOutput(String criterion, int weightPercent) {}
    public record AssetOutput(String role, String outputFormat, String altText,
                              String visualDescription, List<String> requiredElements,
                              List<String> forbiddenElements, Map<String, Object> renderData) {}
}
