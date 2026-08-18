package com.cenedu.backend.domain.problem.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemAuthoringSessionRepository
        extends JpaRepository<ProblemAuthoringSession, Long> {

    // 다른 교사의 Session 존재를 노출하지 않고 소유한 Session만 조회한다.
    Optional<ProblemAuthoringSession> findByIdAndOwnerTeacherId(Long id, Long ownerTeacherId);

    // Version 번호 배정과 current·pending 전이를 한 Session씩 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from ProblemAuthoringSession session
            where session.id = :sessionId
              and session.ownerTeacherId = :ownerTeacherId
            """)
    Optional<ProblemAuthoringSession> findOwnedByIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("ownerTeacherId") Long ownerTeacherId);

    // 여러 Session 최종화 시 교착을 방지하도록 ID 오름차순으로 잠그어 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from ProblemAuthoringSession session
            where session.id in :sessionIds
            order by session.id
            """)
    List<ProblemAuthoringSession> findAllForFinalization(
            @Param("sessionIds") Collection<Long> sessionIds);

    // 채점·검증 조회측이 최종 questionId로 저장 당시 S1 Version을 찾는다.
    Optional<ProblemAuthoringSession> findByFinalizedQuestionId(Long finalizedQuestionId);

    // TTL이 지난 DRAFT Session을 오래된 순서로 정리한다.
    List<ProblemAuthoringSession> findByLifecycleStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus lifecycleStatus,
            LocalDateTime cutoff);
}
