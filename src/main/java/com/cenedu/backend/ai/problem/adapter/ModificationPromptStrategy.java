package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 확정된 수정 계획을 AI가 보호 영역을 건드리지 않도록 제한하는 프롬프트다. */
@Component
public class ModificationPromptStrategy {
    private final ObjectMapper objectMapper;

    public ModificationPromptStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 수정 지시·protected target·기준 Version을 프롬프트로 조립한다. */
    public String create(ProblemModificationCommand command) {
        var plan = command.plan();
        return """
                기존 문제 Snapshot을 교사의 확정 지시에 따라 수정하라.
                반드시 JSON 객체만 반환하고 제공된 출력 스키마의 모든 필수 필드를 포함하라.
                protectedTargets에 포함된 영역은 원문과 의미를 바꾸지 마라.
                requestedTargets와 instructions에 해당하는 변경만 적용하라.
                schemaVersion, requestId, DB ID, storageKey는 출력하지 마라.
                answerUnits가 requestedTargets 또는 dependentTargets일 때만 정답을 변경하라.
                그 외 answerUnits는 빈 배열로 반환해도 서버가 기준 Snapshot의 값을 보존한다.
                action=%s, requestedTargets=%s, dependentTargets=%s, protectedTargets=%s, instructions=%s
                editableContext=%s
                """.formatted(plan.action(), plan.requestedTargets(), plan.dependentTargets(),
                plan.protectedTargets(), plan.instructions(), editableContext(command));
    }

    private String editableContext(ProblemModificationCommand command) {
        var snapshot = command.baseSnapshot();
        var plan = command.plan();
        boolean replace = plan.action() == EditAction.REPLACE;
        java.util.Set<EditTargetType> types = java.util.stream.Stream.concat(
                        plan.requestedTargets().stream(), plan.dependentTargets().stream())
                .map(target -> target.targetType()).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, Object> context = new java.util.LinkedHashMap<>();
        context.put("classification", java.util.Map.of(
                "questionType", snapshot.metadata().questionType(),
                "difficulty", snapshot.metadata().difficulty(),
                "presentation", snapshot.metadata().presentation()));
        if (replace || types.contains(EditTargetType.QUESTION_BODY)
                || types.contains(EditTargetType.CONTENT_BLOCK)) context.put("contentBlocks", snapshot.contentBlocks());
        if (replace || types.contains(EditTargetType.CHOICE)) context.put("choices", snapshot.choices());
        if (replace || types.contains(EditTargetType.STEP)) context.put("steps", snapshot.steps());
        if (replace || types.contains(EditTargetType.ANSWER_UNIT)) context.put("answerUnits", snapshot.answerUnits());
        if (replace || types.contains(EditTargetType.EXPLANATION)) context.put("explanation", snapshot.explanation());
        if (replace || types.contains(EditTargetType.LEARNING_GUIDE)) context.put("learningGuide", snapshot.learningGuide());
        if (replace || types.contains(EditTargetType.RUBRIC_ITEM)) context.put("rubricItems", snapshot.rubricItems());
        if (replace || types.contains(EditTargetType.ASSET)) context.put("assets", snapshot.assets());
        try { return objectMapper.writeValueAsString(context); }
        catch (Exception exception) { throw new IllegalArgumentException("수정 문맥을 직렬화할 수 없습니다.", exception); }
    }
}
