package com.cenedu.backend.domain.problem.repository;

import com.cenedu.backend.domain.problem.entity.ProblemSearchBackfillState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProblemSearchBackfillStateRepository extends JpaRepository<ProblemSearchBackfillState, String> {
    /** 백필 scheduler 간 중복 실행을 막기 위해 상태 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProblemSearchBackfillState s where s.stateKey = :stateKey")
    Optional<ProblemSearchBackfillState> findByStateKeyForUpdate(String stateKey);
}
