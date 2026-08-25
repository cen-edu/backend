package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.visual.ProblemImageRevisionCommand;

/** 검증에 실패한 이미지 자산만 다시 생성하는 AI 경계다. */
public interface ProblemImageRevisionPort {

    /** 검증 피드백을 반영해 본문이 동일한 새 이미지 후보를 반환한다. */
    ProblemCandidateDraft revise(ProblemImageRevisionCommand command);
}
