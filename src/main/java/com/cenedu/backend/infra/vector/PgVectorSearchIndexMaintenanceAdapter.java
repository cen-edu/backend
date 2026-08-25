package com.cenedu.backend.infra.vector;

import com.cenedu.backend.domain.problem.authoring.search.SearchIndexMaintenancePort;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PgVectorSearchIndexMaintenanceAdapter implements SearchIndexMaintenancePort {
    private final ProblemSearchIndexJdbcRepository repository;

    public PgVectorSearchIndexMaintenanceAdapter(ProblemSearchIndexJdbcRepository repository) {
        this.repository = repository;
    }

    /** 삭제 원본을 제외하고 오래된 최신 인덱싱 작업을 다시 실행 가능하게 만든다. */
    @Override
    @Transactional
    public SearchIndexReconciliationResult reconcile() {
        int deletedIndexes = repository.markDeletedSourceIndexes();
        int reactivatedTasks = repository.reactivateStaleTasks();
        return new SearchIndexReconciliationResult(deletedIndexes, reactivatedTasks);
    }

    /** 커서 뒤의 활성·비서술형·미인덱싱 문항 ID를 반환한다. */
    @Override
    public List<Long> findActiveMissingQuestionIds(long afterQuestionId, int limit) {
        return repository.findActiveMissingQuestionIds(afterQuestionId, limit);
    }

    /** 커서 뒤에서 v1 또는 미분류 그림인 활성 인덱스 문항 ID를 반환한다. */
    @Override
    public List<Long> findVisualReclassificationQuestionIds(long afterQuestionId, int limit) {
        return repository.findVisualReclassificationQuestionIds(afterQuestionId, limit);
    }
}
