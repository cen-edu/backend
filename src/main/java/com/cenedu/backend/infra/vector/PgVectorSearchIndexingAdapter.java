package com.cenedu.backend.infra.vector;

import com.cenedu.backend.domain.problem.authoring.search.SearchIndexingCommand;
import com.cenedu.backend.domain.problem.authoring.search.SearchIndexingPort;
import org.springframework.stereotype.Component;

@Component
public class PgVectorSearchIndexingAdapter implements SearchIndexingPort {
    private final ProblemSearchIndexJdbcRepository repository;
    public PgVectorSearchIndexingAdapter(ProblemSearchIndexJdbcRepository repository) { this.repository = repository; }

    /** 검색 인덱싱 명령을 PostgreSQL 작업 큐에 멱등 등록한다. */
    @Override public boolean enqueue(SearchIndexingCommand command) { return repository.insertPending(command); }
}
