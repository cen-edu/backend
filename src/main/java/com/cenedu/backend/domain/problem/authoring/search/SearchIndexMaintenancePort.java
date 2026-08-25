package com.cenedu.backend.domain.problem.authoring.search;

import java.util.List;

/** 문제 원본과 검색 인덱스 사이의 운영 정합성을 복구하는 계약이다. */
public interface SearchIndexMaintenancePort {

    /** 삭제 원본과 오래된 인덱싱 작업을 동기화하고 처리 건수를 반환한다. */
    SearchIndexReconciliationResult reconcile();

    /** 커서 뒤의 활성·비서술형·미인덱싱 문항 ID를 일정 크기로 반환한다. */
    List<Long> findActiveMissingQuestionIds(long afterQuestionId, int limit);

    /** 커서 뒤에서 v1 또는 미분류 그림인 활성 인덱스 문항 ID를 반환한다. */
    List<Long> findVisualReclassificationQuestionIds(long afterQuestionId, int limit);

    record SearchIndexReconciliationResult(int deletedIndexes, int reactivatedTasks) {}
}
