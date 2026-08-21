package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairCommand;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairDelta;

/** 검증 오류가 확인된 구성요소만 한 번에 수정하는 시스템 Port다. */
public interface ProblemRepairPort {

    /** 전체 Snapshot이 아닌 허용된 대상 필드의 대체값만 반환한다. */
    ProblemRepairDelta repair(ProblemRepairCommand command);
}
