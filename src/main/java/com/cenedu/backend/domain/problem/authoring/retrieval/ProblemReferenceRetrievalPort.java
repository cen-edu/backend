package com.cenedu.backend.domain.problem.authoring.retrieval;

import java.util.List;

public interface ProblemReferenceRetrievalPort {
    /** 교육과정 hard filter와 A 정책을 적용한 최종 참고 문제를 순서대로 반환한다. */
    List<RetrievedProblemReference> retrieve(ProblemReferenceQuery query);
}
