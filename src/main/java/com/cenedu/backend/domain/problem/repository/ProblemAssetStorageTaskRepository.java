package com.cenedu.backend.domain.problem.repository;

import java.time.LocalDateTime;
import java.util.List;
import com.cenedu.backend.domain.problem.entity.ProblemAssetStorageTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProblemAssetStorageTaskRepository extends JpaRepository<ProblemAssetStorageTask, Long> {
    /** 재시도 가능한 자산 작업을 선점 후보로 조회한다. */
    @Query("select task from ProblemAssetStorageTask task where task.status in ('PENDING','RETRY_WAIT') or (task.status = 'PROCESSING' and task.nextAttemptAt <= :now) order by task.id")
    List<ProblemAssetStorageTask> findRunnable(@Param("now") LocalDateTime now);

    /** 한 작업을 동시에 두 Worker가 처리하지 않도록 잠금 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from ProblemAssetStorageTask task where task.id = :id")
    Optional<ProblemAssetStorageTask> findByIdForUpdate(@Param("id") Long id);

    /** 보존기간이 지났고 로컬 원본이 아직 남은 영구 실패 작업을 조회한다. */
    @Query("select task from ProblemAssetStorageTask task where task.status = 'FAILED' and task.sourceDeletedAt is null and task.updatedAt <= :cutoff order by task.id")
    List<ProblemAssetStorageTask> findFailedForSourceCleanup(@Param("cutoff") LocalDateTime cutoff);
}
