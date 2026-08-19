package com.cenedu.backend.domain.problem.authoring.search;

public interface SearchIndexingPort {
    /** questionId 멱등 키로 PENDING 작업을 만들며 이미 존재하면 false를 반환한다. */
    boolean enqueue(SearchIndexingCommand command);
}
