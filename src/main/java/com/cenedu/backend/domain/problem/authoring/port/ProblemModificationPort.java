package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;

/** 확정된 교사 수정 계획을 AI 수정 Adapter에 전달하는 전용 경계다. */
public interface ProblemModificationPort {
    /** base Snapshot을 보존하며 확정 수정 후보를 만든다. */
    ProblemCandidateDraft modify(ProblemModificationCommand command);
}
