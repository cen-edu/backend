package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
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
                action이 REPLACE면 모든 필드가 대상이다 — 정답을 포함해 지시에 맞게 전부 다시 작성하라.
                action이 REPLACE가 아니면 answerUnits가 requestedTargets 또는 dependentTargets일 때만 정답을 변경하라.
                그 경우 그 외 answerUnits는 빈 배열로 반환해도 서버가 기준 Snapshot의 값을 보존한다.
                targetSpecification은 교사가 요청한 목표 난이도·문항 유형이다. null이 아닌 값은
                반드시 그 값에 맞춰 문항을 다시 작성하라. editableContext.classification은 현재 문항의
                값일 뿐 목표가 아니다 — 둘이 다르면 targetSpecification이 우선한다.
                난이도 low는 한 단계 계산·단순 수치, mid는 두 단계 추론, high는 다단계 추론이나
                조건 결합을 뜻한다. 문항 유형이 바뀌면 그 유형의 구조 규칙(객관식은 보기,
                빈칸형은 steps, 서술형은 rubricItems)에 맞춰 전부 새로 만들어라.
                retryIssueCodes가 비어 있지 않으면 직전 후보가 해당 검증에 실패한 재시도다.
                민감한 검증 근거는 제공되지 않으므로, 원래 지시를 다시 대조해 해당 실패 원인만 교정하라.
                action=%s, requestedTargets=%s, dependentTargets=%s, protectedTargets=%s, instructions=%s
                targetSpecification=%s
                editableContext=%s
                retryIssueCodes=%s
                """.formatted(plan.action(), plan.requestedTargets(), plan.dependentTargets(),
                plan.protectedTargets(), plan.instructions(), targetSpecification(plan),
                editableContext(command), command.previousIssueCodes());
    }

    /** 교사가 바꾸라고 한 난이도·문항 유형만 목표로 드러내고, 없으면 현재 분류를 유지시킨다. */
    private String targetSpecification(ProblemEditExecutionPlan plan) {
        var requested = plan.requestedSpecification();
        if (requested == null) return "null (현재 분류 유지)";
        java.util.Map<String, Object> target = new java.util.LinkedHashMap<>();
        if (requested.questionType() != null) target.put("questionType", requested.questionType());
        if (requested.difficulty() != null) target.put("difficulty", requested.difficulty());
        try { return objectMapper.writeValueAsString(target); }
        catch (Exception exception) { throw new IllegalArgumentException("목표 스펙을 직렬화할 수 없습니다.", exception); }
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
