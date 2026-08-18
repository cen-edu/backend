package com.cenedu.backend.domain.problem.repository;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemGenerationItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemGenerationItemRepository
        extends JpaRepository<ProblemGenerationItem, Long> {

    // Job 상태 집계와 화면 표시에 사용할 Item을 요청 순서로 조회한다.
    List<ProblemGenerationItem> findAllByJobIdOrderByItemOrder(Long jobId);

    // 하나의 Item을 두 Worker가 동시에 실행하지 못하게 선점한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ProblemGenerationItem item where item.id = :itemId")
    java.util.Optional<ProblemGenerationItem> findByIdForUpdate(
            @Param("itemId") Long itemId);
}
