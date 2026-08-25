package com.cenedu.backend.domain.problem.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 검색 백필 cursor를 재시작 후에도 이어가기 위한 단일 상태 레코드다. */
@Entity
@Table(name = "problem_search_backfill_state")
public class ProblemSearchBackfillState {
    @Id
    private String stateKey;
    private long cursor;
    private OffsetDateTime updatedAt;

    protected ProblemSearchBackfillState() {}

    public ProblemSearchBackfillState(String stateKey, long cursor, OffsetDateTime updatedAt) {
        this.stateKey = stateKey;
        this.cursor = cursor;
        this.updatedAt = updatedAt;
    }

    public String getStateKey() { return stateKey; }
    public long getCursor() { return cursor; }

    /** 백필 결과의 마지막 문항 ID를 저장한다. */
    public void advance(long nextCursor, OffsetDateTime now) {
        this.cursor = nextCursor;
        this.updatedAt = now;
    }
}
