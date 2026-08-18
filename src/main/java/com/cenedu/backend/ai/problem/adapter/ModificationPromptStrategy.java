package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import org.springframework.stereotype.Component;

/** 확정된 수정 계획을 AI가 보호 영역을 건드리지 않도록 제한하는 프롬프트다. */
@Component
public class ModificationPromptStrategy {
    /** 수정 지시·protected target·기준 Version을 프롬프트로 조립한다. */
    public String create(ProblemModificationCommand command) {
        var plan = command.plan();
        return """
                기존 문제 Snapshot을 교사의 확정 지시에 따라 수정하라.
                반드시 JSON 객체만 반환하고 schemaVersion 1을 유지하라.
                protectedTargets에 포함된 영역은 원문과 의미를 바꾸지 마라.
                requestedTargets와 instructions에 해당하는 변경만 적용하라.
                정답, requestId, DB ID, storageKey는 새로 생성하지 말고 문제 내용 JSON에 포함하지 마라.
                action=%s, requestedTargets=%s, protectedTargets=%s, instructions=%s
                """.formatted(plan.action(), plan.requestedTargets(), plan.protectedTargets(), plan.instructions());
    }
}
