package com.cenedu.backend.domain.problem.repository;

import java.util.Optional;
import java.util.UUID;

import com.cenedu.backend.domain.problem.entity.ProblemGenerationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemGenerationJobRepository extends JpaRepository<ProblemGenerationJob, Long> {

    // 교사의 동일 clientRequestId로 이미 생성된 멱등 Job을 조회한다.
    Optional<ProblemGenerationJob> findByOwnerTeacherIdAndClientRequestId(
            Long ownerTeacherId, UUID clientRequestId);

    // 다른 교사의 Job 존재를 노출하지 않고 소유한 Job만 조회한다.
    Optional<ProblemGenerationJob> findByIdAndOwnerTeacherId(Long id, Long ownerTeacherId);

    // Item 완료 집계가 동시에 도착해도 Job을 한 번만 종료한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ProblemGenerationJob job where job.id = :jobId")
    Optional<ProblemGenerationJob> findByIdForUpdate(@Param("jobId") Long jobId);
}
