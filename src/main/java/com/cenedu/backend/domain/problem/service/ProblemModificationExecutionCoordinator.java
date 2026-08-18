package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import org.springframework.stereotype.Component;

/** 확정 수정 계획을 RESTORE 또는 AI 수정 실행으로 분기한다. */
@Component
public class ProblemModificationExecutionCoordinator {
    private final ProblemModificationWorker modificationWorker;
    private final ProblemAuthoringStateService stateService;

    public ProblemModificationExecutionCoordinator(ProblemModificationWorker modificationWorker,
            ProblemAuthoringStateService stateService) {
        this.modificationWorker = modificationWorker;
        this.stateService = stateService;
    }

    /** RESTORE는 AI 호출 없이 즉시 전환하고 나머지는 수정 Worker에 위임한다. */
    public Object execute(long teacherId, ProblemEditExecutionPlan plan,
                          com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 baseSnapshot) {
        if (plan.action() == EditAction.RESTORE) {
            stateService.restorePassedVersion(teacherId, plan.sessionId(), plan.restoreVersionId());
            return plan.restoreVersionId();
        }
        return modificationWorker.execute(teacherId,
                new com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand(
                        plan.requestId(), plan, baseSnapshot));
    }
}
