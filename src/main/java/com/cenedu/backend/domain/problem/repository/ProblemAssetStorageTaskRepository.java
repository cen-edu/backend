package com.cenedu.backend.domain.problem.repository;

import java.time.LocalDateTime;
import java.util.List;
import com.cenedu.backend.domain.problem.entity.ProblemAssetStorageTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemAssetStorageTaskRepository extends JpaRepository<ProblemAssetStorageTask, Long> {
    /** 재시도 가능한 자산 작업을 선점 후보로 조회한다. */
    @Query("select task from ProblemAssetStorageTask task where task.status in ('PENDING','RETRY_WAIT') and (task.nextAttemptAt is null or task.nextAttemptAt <= :now) order by task.id")
    List<ProblemAssetStorageTask> findRunnable(@Param("now") LocalDateTime now);
}
